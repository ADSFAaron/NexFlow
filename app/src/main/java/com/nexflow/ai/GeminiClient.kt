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
import com.nexflow.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed class GeminiException(message: String) : Exception(message) {
    class InvalidKey(message: String) : GeminiException(message)
    class RateLimited(message: String) : GeminiException(message)
    class ModelNotFound(message: String) : GeminiException(message)
    class Network(message: String) : GeminiException(message)
    class Unknown(message: String) : GeminiException(message)
}

@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    // The shared client's ContentNegotiation uses strict default Json; Gemini responses carry
    // fields we don't model (usageMetadata, safetyRatings, ...), so (de)serialization happens
    // here with a lenient instance and plain-string bodies.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun generateContent(
        apiKey: String,
        model: String,
        request: GenerateContentRequest,
    ): Result<GenerateContentResponse> = runCatching {
        val requestBody = json.encodeToString(GenerateContentRequest.serializer(), request)
        Log.d(TAG, "generateContent → model=$model, ${request.contents.size} contents, ${requestBody.length} chars")
        logLongDebug("generateContent request", requestBody)
        val response = try {
            httpClient.post("$BASE_URL/models/$model:generateContent") {
                header(API_KEY_HEADER, apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                // LLM turns routinely exceed the shared client's 30 s budget.
                timeout {
                    requestTimeoutMillis = 90_000
                    socketTimeoutMillis = 90_000
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateContent transport failure (model=$model)", e)
            throw e.asNetworkOrRethrow()
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            Log.e(TAG, "generateContent HTTP ${response.status.value} (model=$model): $body")
            throw response.toGeminiException(body)
        }
        logLongDebug("generateContent response", body)
        json.decodeFromString(GenerateContentResponse.serializer(), body)
    }

    suspend fun listModels(apiKey: String): Result<List<GeminiModelInfo>> = runCatching {
        val models = mutableListOf<GeminiModelInfo>()
        var pageToken: String? = null
        do {
            val response = try {
                httpClient.get("$BASE_URL/models") {
                    header(API_KEY_HEADER, apiKey)
                    parameter("pageSize", 100)
                    pageToken?.let { parameter("pageToken", it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "listModels transport failure", e)
                throw e.asNetworkOrRethrow()
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                Log.e(TAG, "listModels HTTP ${response.status.value}: $body")
                throw response.toGeminiException(body)
            }
            val page = json.decodeFromString(ListModelsResponse.serializer(), body)
            models += page.models
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)
        models
    }

    private fun HttpResponse.toGeminiException(body: String): GeminiException =
        mapHttpError(status.value, body)

    // A 400 is any malformed request (bad tool schema, model without function-calling support,
    // ...), NOT necessarily a bad key — only treat it as InvalidKey when the error body says so,
    // and surface the API's own message otherwise so the real cause reaches the user.
    internal fun mapHttpError(statusCode: Int, body: String): GeminiException {
        val error = runCatching {
            json.decodeFromString(GeminiErrorBody.serializer(), body).error
        }.getOrNull()
        val message = error?.message ?: "HTTP $statusCode"
        val reasons = error?.details.orEmpty().mapNotNull {
            (it["reason"] as? JsonPrimitive)?.contentOrNull
        }
        return when {
            statusCode == 401 || statusCode == 403 -> GeminiException.InvalidKey(message)
            statusCode == 400 &&
                ("API_KEY_INVALID" in reasons || message.contains("api key", ignoreCase = true)) ->
                GeminiException.InvalidKey(message)
            statusCode == 404 -> GeminiException.ModelNotFound(message)
            statusCode == 429 -> GeminiException.RateLimited(message)
            else -> GeminiException.Unknown(message)
        }
    }

    private fun Exception.asNetworkOrRethrow(): Exception = when (this) {
        is IOException, is HttpRequestTimeoutException ->
            GeminiException.Network(message ?: "network error")
        else -> this
    }

    // Full request/response bodies only in debug builds (they contain user prompts), chunked
    // because logcat truncates single entries around 4 KB. The API key is never logged.
    private fun logLongDebug(label: String, text: String) {
        if (!BuildConfig.DEBUG) return
        text.chunked(3000).forEachIndexed { i, chunk ->
            Log.d(TAG, "$label [$i]: $chunk")
        }
    }

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val API_KEY_HEADER = "x-goog-api-key"

        // Special-purpose variants that also report generateContent but can't drive the chat
        // (image generation, TTS, native audio, Live API, embeddings).
        private val NON_CHAT_KEYWORDS = listOf("image", "tts", "audio", "live", "embedding", "vision")

        // Families the pricing page lists as shut down (1.x and 2.0 as of June 2026) but that
        // models.list may still return.
        private val RETIRED_PREFIXES = listOf("gemini-1.", "gemini-2.0")

        /**
         * Models the picker should offer: text-chat Gemini models that support generateContent,
         * excluding retired families and anything self-described as deprecated, newest first.
         */
        fun chatModels(models: List<GeminiModelInfo>): List<GeminiModelInfo> =
            models.filter { model ->
                val id = model.modelId.lowercase()
                "generateContent" in model.supportedGenerationMethods &&
                    id.startsWith("gemini-") &&
                    NON_CHAT_KEYWORDS.none { it in id } &&
                    RETIRED_PREFIXES.none { id.startsWith(it) } &&
                    "deprecated" !in model.description.orEmpty().lowercase() &&
                    "deprecated" !in model.displayName.orEmpty().lowercase()
            }.sortedByDescending { it.modelId }
    }
}
