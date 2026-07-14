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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiClientTest {

    private val client = GeminiClient(mockk<HttpClient>())

    // --- HTTP error mapping -------------------------------------------------

    @Test
    fun `400 with API_KEY_INVALID reason maps to InvalidKey`() {
        val body = """
            {"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.",
             "status": "INVALID_ARGUMENT",
             "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo",
                          "reason": "API_KEY_INVALID", "domain": "googleapis.com"}]}}
        """.trimIndent()
        assertInstanceOf(GeminiException.InvalidKey::class.java, client.mapHttpError(400, body))
    }

    @Test
    fun `400 from a malformed request is NOT blamed on the key`() {
        val body = """
            {"error": {"code": 400,
             "message": "Function calling is not enabled for this model.",
             "status": "INVALID_ARGUMENT"}}
        """.trimIndent()
        val mapped = client.mapHttpError(400, body)
        assertInstanceOf(GeminiException.Unknown::class.java, mapped)
        // The API's own message must survive so the user sees the real cause
        assertEquals("Function calling is not enabled for this model.", mapped.message)
    }

    @Test
    fun `401 and 403 map to InvalidKey`() {
        val body = """{"error": {"code": 403, "message": "Permission denied", "status": "PERMISSION_DENIED"}}"""
        assertInstanceOf(GeminiException.InvalidKey::class.java, client.mapHttpError(401, body))
        assertInstanceOf(GeminiException.InvalidKey::class.java, client.mapHttpError(403, body))
    }

    @Test
    fun `404 and 429 keep their specific types`() {
        assertInstanceOf(
            GeminiException.ModelNotFound::class.java,
            client.mapHttpError(404, """{"error": {"message": "model not found"}}"""),
        )
        assertInstanceOf(
            GeminiException.RateLimited::class.java,
            client.mapHttpError(429, """{"error": {"message": "quota exceeded"}}"""),
        )
    }

    @Test
    fun `unparseable error body falls back to the status code`() {
        val mapped = client.mapHttpError(500, "<html>Internal error</html>")
        assertInstanceOf(GeminiException.Unknown::class.java, mapped)
        assertEquals("HTTP 500", mapped.message)
    }

    // --- Model picker filtering ---------------------------------------------

    private fun model(
        id: String,
        methods: List<String> = listOf("generateContent"),
        description: String? = null,
        displayName: String? = null,
    ) = GeminiModelInfo(
        name = "models/$id",
        displayName = displayName,
        description = description,
        supportedGenerationMethods = methods,
    )

    @Test
    fun `chatModels keeps only current text-chat gemini models, newest first`() {
        val filtered = GeminiClient.chatModels(
            listOf(
                model("gemini-2.5-pro"),
                model("gemini-3.5-flash"),
                model("gemini-2.5-flash"),
            ),
        )
        assertEquals(
            listOf("gemini-3.5-flash", "gemini-2.5-pro", "gemini-2.5-flash"),
            filtered.map { it.modelId },
        )
    }

    @Test
    fun `special-purpose variants are excluded even when they support generateContent`() {
        val filtered = GeminiClient.chatModels(
            listOf(
                model("gemini-3.5-flash"),
                model("gemini-2.5-flash-image"),
                model("gemini-2.5-flash-preview-tts"),
                model("gemini-2.5-flash-native-audio-dialog"),
                model("gemini-live-2.5-flash-preview"),
                model("gemini-embedding-001"),
            ),
        )
        assertEquals(listOf("gemini-3.5-flash"), filtered.map { it.modelId })
    }

    @Test
    fun `retired families and self-described deprecated models are excluded`() {
        val filtered = GeminiClient.chatModels(
            listOf(
                model("gemini-3.5-flash"),
                model("gemini-1.5-flash"),
                model("gemini-2.0-flash"),
                model("gemini-2.5-flash-old", description = "Deprecated on June 1, 2026."),
                model("gemini-2.5-pro-old", displayName = "Gemini 2.5 Pro (Deprecated)"),
            ),
        )
        assertEquals(listOf("gemini-3.5-flash"), filtered.map { it.modelId })
    }

    @Test
    fun `non-gemini and non-generateContent models are excluded`() {
        val filtered = GeminiClient.chatModels(
            listOf(
                model("gemini-3.5-flash"),
                model("gemma-3-27b-it"),
                model("imagen-4.0-generate-001", methods = listOf("predict")),
                model("veo-3.0-generate-preview", methods = listOf("predictLongRunning")),
                model("aqa"),
                model("gemini-3.1-pro-preview", methods = listOf("countTokens")),
            ),
        )
        assertEquals(listOf("gemini-3.5-flash"), filtered.map { it.modelId })
        assertTrue(filtered.none { "gemma" in it.modelId })
    }
}
