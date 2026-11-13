/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nexflow.ai

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A service the user pointed the assistant at instead of Gemini. */
data class CustomEndpoint(
    /** Normalized by [normalizeBaseUrl]: no trailing slash, `/chat/completions` not included. */
    val baseUrl: String,
    /** Null for a server that takes none, such as a local Ollama. */
    val apiKey: String?,
    val model: String,
) {
    companion object {
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")

        /**
         * The usable form of what the user typed, or null when it can't be used. Plain http is
         * refused except on loopback: a key and every flow's settings would otherwise cross the
         * network unencrypted, and `network_security_config.xml` only opens the same two hosts.
         */
        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/').removeSuffix("/chat/completions").trimEnd('/')
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val allowed = when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> host in LOOPBACK_HOSTS
                else -> false
            }
            return trimmed.takeIf { allowed }
        }
    }
}

/**
 * Talks to any service that implements OpenAI's Chat Completions: OpenAI itself, OpenRouter,
 * Ollama and so on.
 *
 * The rest of the assistant only knows the Gemini wire types, and those are also what gets
 * persisted, so this translates at the edge rather than introducing a third format: requests go
 * out through [OpenAiChatMapper], and streamed chunks come back as [GenerateContentResponse].
 * Gemini itself stays on [GeminiClient]; Google recommends its native API over this one.
 */
@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun streamGenerateContent(
        endpoint: CustomEndpoint,
        request: GenerateContentRequest,
    ): Flow<GenerateContentResponse> = flow {
        val requestBody = OpenAiChatMapper.toRequestBody(endpoint.model, request).toString()
        Log.d(TAG, "chat/completions → model=${endpoint.model}, ${request.contents.size} contents")
        val statement = httpClient.preparePost("${endpoint.baseUrl}/chat/completions") {
            endpoint.apiKey?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                // A local model can spend minutes reading the prompt before its first byte.
                socketTimeoutMillis = 180_000
            }
        }
        try {
            statement.execute { response ->
                if (response.status.value !in 200..299) {
                    val body = response.bodyAsText()
                    Log.e(TAG, "chat/completions HTTP ${response.status.value} (model=${endpoint.model}): $body")
                    throw mapHttpError(response.status.value, body, response.headers[HttpHeaders.RetryAfter])
                }
                val assembler = OpenAiStreamAssembler()
                val channel = response.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    val chunk = parseSseLine(line) ?: continue
                    // Some gateways report a failure inside a 200 stream.
                    chunk.error?.let { throw GeminiException.Unknown(errorMessage(it) ?: "stream error") }
                    assembler.accept(chunk)?.let { emit(it) }
                }
                emit(assembler.finish())
            }
        } catch (e: Exception) {
            if (e is GeminiException || e is CancellationException) throw e
            Log.e(TAG, "chat/completions transport failure (model=${endpoint.model})", e)
            throw if (e is IOException || e is HttpRequestTimeoutException) {
                GeminiException.Network(e.message ?: "network error")
            } else {
                e
            }
        }
    }

    /** One SSE line → one chunk, or null for anything else. A bad line must not kill a reply. */
    internal fun parseSseLine(line: String): OpenAiChunk? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return runCatching { json.decodeFromString(OpenAiChunk.serializer(), payload) }.getOrElse {
            Log.w(TAG, "unparseable SSE payload skipped: ${payload.take(120)}")
            null
        }
    }

    /** Same exception types as Gemini's, so the retry logic and error messages carry over. */
    internal fun mapHttpError(statusCode: Int, body: String, retryAfter: String?): GeminiException {
        val error = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()?.get("error")
        val message = error?.let(::errorMessage) ?: "HTTP $statusCode"
        val code = ((error as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull
        return when (statusCode) {
            401, 403 -> GeminiException.InvalidKey(message)
            404 -> GeminiException.ModelNotFound(message)
            429 -> GeminiException.RateLimited(
                message = message,
                retryAfterMillis = retryAfter?.toDoubleOrNull()?.let { (it * 1000).toLong() },
                // OpenAI's out-of-credit answer. Waiting can't fix it, unlike a real rate limit.
                noQuota = code == "insufficient_quota",
            )
            500, 502, 503 -> GeminiException.Unavailable(message)
            else -> GeminiException.Unknown(message)
        }
    }

    /** OpenAI nests the message in an object; Ollama's own errors are a bare string. */
    private fun errorMessage(error: JsonElement): String? = when (error) {
        is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> error.contentOrNull
        else -> null
    }

    private companion object {
        const val TAG = "OpenAiCompatibleClient"
    }
}

/** [GenerateContentRequest] → a Chat Completions request body. */
internal object OpenAiChatMapper {

    fun toRequestBody(model: String, request: GenerateContentRequest): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
        put("messages", JsonArray(messages(request)))
        val declarations = request.tools.orEmpty().flatMap { it.functionDeclarations }
        if (declarations.isNotEmpty()) {
            putJsonArray("tools") { declarations.forEach { add(tool(it)) } }
        }
    }

    private fun tool(declaration: GeminiFunctionDeclaration) = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", declaration.name)
            put("description", declaration.description)
            declaration.parameters?.let { put("parameters", it) }
        }
    }

    private fun messages(request: GenerateContentRequest): List<JsonObject> = buildList {
        request.systemInstruction?.let { system ->
            add(textMessage("system", system.parts.mapNotNull { it.text }.joinToString("\n")))
        }
        // The Gemini history carries no call ids, so they're derived from position: what matters
        // is only that a tool message names the call just before it. Trimming never separates
        // the two, so they stay adjacent.
        var callIds = emptyList<String>()
        request.contents.forEachIndexed { index, content ->
            val responses = content.parts.mapNotNull { it.functionResponse }
            when {
                responses.isNotEmpty() -> responses.forEachIndexed { i, response ->
                    add(
                        buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", callIds.getOrElse(i) { callId(index, i) })
                            put("content", response.response.toString())
                        },
                    )
                }

                content.role == "model" -> {
                    val calls = content.parts.mapNotNull { it.functionCall }
                    callIds = calls.indices.map { callId(index, it) }
                    add(assistantMessage(content, calls, callIds))
                }

                else -> add(userMessage(content))
            }
        }
    }

    private fun callId(contentIndex: Int, callIndex: Int) = "call_${contentIndex}_$callIndex"

    private fun textMessage(role: String, text: String) = buildJsonObject {
        put("role", role)
        put("content", text)
    }

    private fun assistantMessage(content: GeminiContent, calls: List<GeminiFunctionCall>, ids: List<String>) =
        buildJsonObject {
            put("role", "assistant")
            put(
                "content",
                content.parts.filter { it.thought != true }.mapNotNull { it.text }.joinToString("\n").trim(),
            )
            if (calls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    calls.forEachIndexed { i, call ->
                        addJsonObject {
                            put("id", ids[i])
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.args.toString())
                            }
                        }
                    }
                }
            }
        }

    private fun userMessage(content: GeminiContent): JsonObject {
        val images = content.parts.mapNotNull { it.inlineData }
        val text = content.parts.mapNotNull { it.text }.joinToString("\n")
        if (images.isEmpty()) return textMessage("user", text)
        return buildJsonObject {
            put("role", "user")
            put(
                "content",
                buildJsonArray {
                    if (text.isNotBlank()) {
                        addJsonObject {
                            put("type", "text")
                            put("text", text)
                        }
                    }
                    images.forEach { image ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${image.mimeType};base64,${image.data}") }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Streamed chunks → the responses [AiChatOrchestrator] folds together. Text passes straight
 * through so it can be shown as it arrives; a tool call's arguments come in fragments that mean
 * nothing until the stream ends, so calls are held back for [finish].
 */
internal class OpenAiStreamAssembler {
    private class PendingCall(val index: Int, val id: String?) {
        var name: String = ""
        val arguments = StringBuilder()
    }

    private val calls = mutableListOf<PendingCall>()
    private var usage: GeminiUsageMetadata? = null

    fun accept(chunk: OpenAiChunk): GenerateContentResponse? {
        chunk.usage?.let { usage = it.toGemini() }
        val delta = chunk.choices.firstOrNull()?.delta ?: return null
        delta.toolCalls.orEmpty().forEach { fragment ->
            val call = pendingCall(fragment)
            fragment.function?.name?.let { call.name += it }
            fragment.function?.arguments?.let { call.arguments.append(it) }
        }
        val text = delta.content?.takeIf { it.isNotEmpty() } ?: return null
        return response(listOf(GeminiPart(text = text)))
    }

    /** Continuation fragments repeat the index. A new id on the same index is a new call. */
    private fun pendingCall(fragment: OpenAiToolCallDelta): PendingCall {
        val existing = calls.lastOrNull { it.index == fragment.index }
        val isNew = existing == null || (fragment.id != null && existing.id != null && fragment.id != existing.id)
        return if (isNew) PendingCall(fragment.index, fragment.id).also { calls += it } else existing!!
    }

    fun finish(): GenerateContentResponse =
        response(calls.sortedBy { it.index }.map { it.toPart() }).copy(usageMetadata = usage)

    private fun PendingCall.toPart(): GeminiPart {
        // Malformed arguments go through as empty, so validation reports what's missing and the
        // model gets a chance to repair it, instead of the whole turn failing here.
        val args = runCatching { Json.parseToJsonElement(arguments.toString()) as? JsonObject }.getOrNull()
        return GeminiPart(
            functionCall = GeminiFunctionCall(name, args ?: JsonObject(emptyMap())),
            // The conversation may later move to Gemini 3, which rejects any function call
            // without a signature. This is Google's documented value for calls it didn't issue.
            thoughtSignature = FOREIGN_CALL_SIGNATURE,
        )
    }

    private fun response(parts: List<GeminiPart>) = GenerateContentResponse(
        candidates = listOf(GeminiCandidate(content = GeminiContent(role = "model", parts = parts))),
    )

    private fun OpenAiUsage.toGemini(): GeminiUsageMetadata {
        // Unlike Gemini, reasoning is counted inside completion_tokens here.
        val reasoning = completionDetails?.reasoningTokens ?: 0
        return GeminiUsageMetadata(
            promptTokenCount = promptTokens,
            cachedContentTokenCount = promptDetails?.cachedTokens ?: 0,
            candidatesTokenCount = completionTokens - reasoning,
            thoughtsTokenCount = reasoning,
            totalTokenCount = totalTokens,
            promptTokensDetails = promptDetails?.imageTokens
                ?.let { listOf(GeminiModalityTokenCount("IMAGE", it)) }
                .orEmpty(),
        )
    }

    private companion object {
        const val FOREIGN_CALL_SIGNATURE = "skip_thought_signature_validator"
    }
}

@Serializable
internal data class OpenAiChunk(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
    val error: JsonElement? = null,
)

@Serializable
internal data class OpenAiChoice(val delta: OpenAiDelta? = null)

@Serializable
internal data class OpenAiDelta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCallDelta>? = null,
)

@Serializable
internal data class OpenAiToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: OpenAiFunctionDelta? = null,
)

@Serializable
internal data class OpenAiFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_tokens_details") val promptDetails: OpenAiPromptDetails? = null,
    @SerialName("completion_tokens_details") val completionDetails: OpenAiCompletionDetails? = null,
)

@Serializable
internal data class OpenAiPromptDetails(
    @SerialName("cached_tokens") val cachedTokens: Int? = null,
    @SerialName("image_tokens") val imageTokens: Int? = null,
)

@Serializable
internal data class OpenAiCompletionDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null,
)
