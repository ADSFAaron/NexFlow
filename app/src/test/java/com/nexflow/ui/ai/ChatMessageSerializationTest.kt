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
package com.nexflow.ui.ai

import com.nexflow.ai.GeminiContent
import com.nexflow.ai.GeminiFunctionCall
import com.nexflow.ai.GeminiPart
import com.nexflow.core.flowschema.FlowJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A persisted conversation is only worth keeping if it comes back exactly as it went in — a
 * transcript that loses its flow preview or its retry text restores into a broken screen.
 */
class ChatMessageSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val messagesSerializer = ListSerializer(ChatMessage.serializer())
    private val historySerializer = ListSerializer(GeminiContent.serializer())

    private val flow = FlowJson(
        schemaVersion = 1,
        id = "flow-1",
        name = "Drive mode",
        description = "Turns the volume up",
        tags = emptyList(),
        enabled = false,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        triggers = emptyList(),
        triggerLogic = "ANY",
        conditions = emptyList(),
        actions = emptyList(),
        variables = emptyList(),
    )

    @Test
    fun `every message type survives a round trip`() {
        val original = listOf(
            ChatMessage.UserText("turn the volume up", id = "m1", timestamp = 1000),
            ChatMessage.AssistantText("Done.", stopped = true, id = "m2", timestamp = 2000),
            ChatMessage.Error("no network", retryText = "turn the volume up", id = "m3", timestamp = 3000),
            ChatMessage.FlowPreview(flow, saved = true, id = "m4", timestamp = 4000),
        )

        val restored = json.decodeFromString(
            messagesSerializer,
            json.encodeToString(messagesSerializer, original),
        )

        assertEquals(original, restored)
    }

    @Test
    fun `the model history round trips including function calls`() {
        // Losing a functionCall (or its thoughtSignature) makes the restored history one
        // Gemini rejects outright, so this is not just a nice-to-have.
        val original = listOf(
            GeminiContent(role = "user", parts = listOf(GeminiPart(text = "hi"))),
            GeminiContent(
                role = "model",
                parts = listOf(
                    GeminiPart(
                        functionCall = GeminiFunctionCall(
                            "search_installed_apps",
                            buildJsonObject { put("query", "spotify") },
                        ),
                        thoughtSignature = "sig-123",
                    ),
                ),
            ),
        )

        val restored = json.decodeFromString(
            historySerializer,
            json.encodeToString(historySerializer, original),
        )

        assertEquals(original, restored)
        assertEquals("sig-123", restored[1].parts.single().thoughtSignature)
    }
}
