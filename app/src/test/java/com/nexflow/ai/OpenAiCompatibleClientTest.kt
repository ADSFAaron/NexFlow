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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiCompatibleClientTest {

    private val endpoint = CustomEndpoint("https://api.example.com/v1", "sk-test", "some-model")
    private var lastRequest: HttpRequestData? = null

    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        retryAfter: String? = null,
    ) = OpenAiCompatibleClient(
        HttpClient(
            MockEngine { request ->
                lastRequest = request
                respond(
                    body,
                    status,
                    if (retryAfter != null) headersOf(HttpHeaders.RetryAfter, retryAfter) else headersOf(),
                )
            },
        ) { install(HttpTimeout) },
    )

    private val request = GenerateContentRequest(
        contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = "hi")))),
    )

    private fun sse(vararg payloads: String) =
        payloads.joinToString("\n\n") { "data: $it" } + "\n\ndata: [DONE]\n\n"

    private fun toolDelta(index: Int, id: String?, name: String?, arguments: String): String {
        val idJson = id?.let { "\"id\":\"$it\"," }.orEmpty()
        val nameJson = name?.let { "\"name\":\"$it\"," }.orEmpty()
        val escaped = arguments.replace("\"", "\\\"")
        return """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":$index,$idJson""" +
            """"type":"function","function":{$nameJson"arguments":"$escaped"}}]}}]}"""
    }

    @Test
    fun `base url keeps https and loopback, and refuses plain http elsewhere`() {
        assertEquals("https://api.openai.com/v1", CustomEndpoint.normalizeBaseUrl(" https://api.openai.com/v1/ "))
        assertEquals("https://a.io/v1", CustomEndpoint.normalizeBaseUrl("https://a.io/v1/chat/completions"))
        assertEquals("http://localhost:11434/v1", CustomEndpoint.normalizeBaseUrl("http://localhost:11434/v1"))
        assertEquals("http://127.0.0.1:11434/v1", CustomEndpoint.normalizeBaseUrl("http://127.0.0.1:11434/v1"))

        assertNull(CustomEndpoint.normalizeBaseUrl("http://192.168.0.5:11434/v1"))
        assertNull(CustomEndpoint.normalizeBaseUrl("http://example.com/v1"))
        assertNull(CustomEndpoint.normalizeBaseUrl("api.openai.com/v1"))
        assertNull(CustomEndpoint.normalizeBaseUrl(""))
    }

    @Test
    fun `a request carries the system prompt, the tools and a usage request`() {
        val body = OpenAiChatMapper.toRequestBody(
            "m",
            GenerateContentRequest(
                contents = request.contents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "be brief"))),
                tools = AiTools.tools(),
            ),
        )

        val messages = body.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("be brief", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        val function = body.getValue("tools").jsonArray[0].jsonObject.getValue("function").jsonObject
        assertEquals(AiTools.CREATE_FLOW, function.getValue("name").jsonPrimitive.content)
        assertTrue("parameters" in function)
        assertEquals(
            "true",
            body.getValue("stream_options").jsonObject.getValue("include_usage").jsonPrimitive.content,
        )
    }

    @Test
    fun `each tool answer names the call it answers`() {
        val args = buildJsonObject { put("query", "x") }
        val body = OpenAiChatMapper.toRequestBody(
            "m",
            GenerateContentRequest(
                contents = listOf(
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = "hi"))),
                    GeminiContent(
                        role = "model",
                        parts = listOf(
                            GeminiPart(text = "my reasoning", thought = true),
                            GeminiPart(
                                functionCall = GeminiFunctionCall("a", args),
                                thoughtSignature = "sig",
                            ),
                            GeminiPart(functionCall = GeminiFunctionCall("b", args)),
                        ),
                    ),
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(functionResponse = GeminiFunctionResponse("a", JsonObject(emptyMap()))),
                            GeminiPart(functionResponse = GeminiFunctionResponse("b", JsonObject(emptyMap()))),
                        ),
                    ),
                ),
            ),
        )

        val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
        val assistant = messages[1]
        assertEquals("", assistant.getValue("content").jsonPrimitive.content, "thoughts are not the reply")
        val ids = assistant.getValue("tool_calls").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(2, ids.toSet().size)
        assertEquals(ids, messages.drop(2).map { it.getValue("tool_call_id").jsonPrimitive.content })
        assertEquals(listOf("tool", "tool"), messages.drop(2).map { it.getValue("role").jsonPrimitive.content })
    }

    @Test
    fun `an image goes out as a data url beside the text`() {
        val body = OpenAiChatMapper.toRequestBody(
            "m",
            GenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = "what is this"),
                            GeminiPart(inlineData = GeminiBlob("image/jpeg", "QUJD")),
                        ),
                    ),
                ),
            ),
        )

        val content = body.getValue("messages").jsonArray[0].jsonObject.getValue("content").jsonArray
        assertEquals("text", content[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            content[1].jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content,
        )
    }

    @Test
    fun `text streams through as it arrives and the key goes in the header`() = runTest {
        val responses = client(
            sse(
                """{"choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}""",
                """{"choices":[{"index":0,"delta":{"content":"Hel"}}]}""",
                """{"choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":"stop"}]}""",
            ),
        ).streamGenerateContent(endpoint, request).toList()

        assertEquals(listOf("Hel", "lo"), responses.flatMap { it.parts }.mapNotNull { it.text })
        assertEquals("https://api.example.com/v1/chat/completions", lastRequest?.url.toString())
        assertEquals("Bearer sk-test", lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `no key means no authorization header`() = runTest {
        client(sse()).streamGenerateContent(endpoint.copy(apiKey = null), request).toList()

        assertNull(lastRequest?.headers?.get(HttpHeaders.Authorization))
    }

    @Test
    fun `tool arguments arriving in fragments are put back together`() = runTest {
        val responses = client(
            sse(
                toolDelta(0, "call_abc", "search_installed_apps", ""),
                toolDelta(0, null, null, """{"query":"""),
                toolDelta(0, null, null, """"spotify"}"""),
            ),
        ).streamGenerateContent(endpoint, request).toList()

        val call = responses.flatMap { it.parts }.mapNotNull { it.functionCall }.single()
        assertEquals("search_installed_apps", call.name)
        assertEquals("spotify", call.args.getValue("query").jsonPrimitive.content)
    }

    @Test
    fun `parallel calls stay separate, by index or by a new id on the same index`() = runTest {
        val byIndex = client(sse(toolDelta(0, "a", "one", "{}"), toolDelta(1, "b", "two", "{}")))
            .streamGenerateContent(endpoint, request).toList()
        val byId = client(sse(toolDelta(0, "a", "one", "{}"), toolDelta(0, "b", "two", "{}")))
            .streamGenerateContent(endpoint, request).toList()

        listOf(byIndex, byId).forEach { responses ->
            assertEquals(
                listOf("one", "two"),
                responses.flatMap { it.parts }.mapNotNull { it.functionCall?.name },
            )
        }
    }

    @Test
    fun `broken tool arguments become empty ones, so validation can ask for a repair`() = runTest {
        val responses = client(sse(toolDelta(0, "a", "create_flow", """{"name":"""))
        ).streamGenerateContent(endpoint, request).toList()

        val call = responses.flatMap { it.parts }.mapNotNull { it.functionCall }.single()
        assertTrue(call.args.isEmpty())
    }

    @Test
    fun `a call from another service can be replayed to Gemini 3`() = runTest {
        val responses = client(sse(toolDelta(0, "a", "one", "{}")))
            .streamGenerateContent(endpoint, request).toList()

        val part = responses.flatMap { it.parts }.single { it.functionCall != null }
        assertEquals("skip_thought_signature_validator", part.thoughtSignature)
    }

    @Test
    fun `usage splits reasoning out of completion tokens, as Gemini reports it`() = runTest {
        val responses = client(
            sse(
                """{"choices":[],"usage":{"prompt_tokens":1000,"completion_tokens":300,"total_tokens":1300,""" +
                    """"prompt_tokens_details":{"cached_tokens":640,"image_tokens":85},""" +
                    """"completion_tokens_details":{"reasoning_tokens":200}}}""",
            ),
        ).streamGenerateContent(endpoint, request).toList()

        val usage = responses.mapNotNull { it.usageMetadata }.single()
        assertEquals(1000, usage.promptTokenCount)
        assertEquals(640, usage.cachedContentTokenCount)
        assertEquals(100, usage.candidatesTokenCount)
        assertEquals(200, usage.thoughtsTokenCount)
        assertEquals(1300, usage.totalTokenCount)
        assertEquals(85, usage.promptTokensDetails.single().tokenCount)
    }

    @Test
    fun `a server that reports no usage still finishes the turn`() = runTest {
        val responses = client(sse("""{"choices":[{"delta":{"content":"ok"}}]}"""))
            .streamGenerateContent(endpoint, request).toList()

        assertEquals("ok", responses.flatMap { it.parts }.mapNotNull { it.text }.single())
        assertTrue(responses.all { it.usageMetadata == null })
    }

    @Test
    fun `sse tolerates no space after the colon and skips what it can't read`() {
        val client = client("")

        assertEquals("x", client.parseSseLine("""data:{"choices":[{"delta":{"content":"x"}}]}""")?.choices?.single()?.delta?.content)
        assertNull(client.parseSseLine("data: [DONE]"))
        assertNull(client.parseSseLine(": keep-alive"))
        assertNull(client.parseSseLine("""data: {"choices":"""))
    }

    @Test
    fun `an error inside a 200 stream fails the turn with its message`() = runTest {
        val error = assertFailsWith<GeminiException.Unknown> {
            client(sse("""{"error":{"message":"provider exploded","code":502}}"""))
                .streamGenerateContent(endpoint, request).toList()
        }

        assertEquals("provider exploded", error.message)
    }

    @Test
    fun `http errors map onto the exceptions the chat already handles`() {
        val client = client("")
        val body = """{"error":{"message":"nope","type":"x","code":"y"}}"""

        assertInstanceOf(GeminiException.InvalidKey::class.java, client.mapHttpError(401, body, null))
        assertInstanceOf(GeminiException.ModelNotFound::class.java, client.mapHttpError(404, body, null))
        assertInstanceOf(GeminiException.Unavailable::class.java, client.mapHttpError(503, body, null))
        assertEquals("nope", client.mapHttpError(400, body, null).message)
        assertEquals("HTTP 500", client.mapHttpError(500, "<html>", null).message)
    }

    @Test
    fun `ollama's bare string error keeps its message`() {
        assertEquals(
            "model 'x' not found",
            client("").mapHttpError(404, """{"error":"model 'x' not found"}""", null).message,
        )
    }

    @Test
    fun `a 429 says how long to wait, and out of credit means waiting is pointless`() {
        val client = client("")

        val limited = client.mapHttpError(429, """{"error":{"message":"slow"}}""", "20")
            as GeminiException.RateLimited
        assertEquals(20_000L, limited.retryAfterMillis)
        assertFalse(limited.noQuota)

        val broke = client.mapHttpError(429, """{"error":{"message":"pay","code":"insufficient_quota"}}""", null)
            as GeminiException.RateLimited
        assertTrue(broke.noQuota)
    }

    @Test
    fun `a failed response reaches the caller as its mapped exception`() = runTest {
        assertFailsWith<GeminiException.InvalidKey> {
            client("""{"error":{"message":"bad key"}}""", HttpStatusCode.Unauthorized)
                .streamGenerateContent(endpoint, request).toList()
        }
    }

    private suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit): T {
        val failure = runCatching { block() }.exceptionOrNull()
        return assertInstanceOf(T::class.java, failure)
    }
}
