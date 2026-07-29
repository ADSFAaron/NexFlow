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

import android.content.Context
import android.content.SharedPreferences
import com.nexflow.ai.AiChatOrchestrator.TurnResult
import com.nexflow.core.automation.repository.GlobalVariableRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiChatOrchestratorTest {

    private val prefs = mockk<SharedPreferences> {
        every { getString("gemini_api_key", null) } returns "test-key"
        every { getString("gemini_model", null) } returns null
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }
    private val client = mockk<GeminiClient>()
    private val installedApps = mockk<InstalledAppsSource>()
    private val globals = mockk<GlobalVariableRepository> {
        coEvery { currentValues() } returns emptyMap()
    }

    private fun orchestrator() = AiChatOrchestrator(client, installedApps, globals, context)

    private fun textResponse(text: String) = GenerateContentResponse(
        candidates = listOf(
            GeminiCandidate(
                content = GeminiContent(role = "model", parts = listOf(GeminiPart(text = text))),
            ),
        ),
    )

    private fun functionCallResponse(name: String, args: JsonObject) = GenerateContentResponse(
        candidates = listOf(
            GeminiCandidate(
                content = GeminiContent(
                    role = "model",
                    parts = listOf(GeminiPart(functionCall = GeminiFunctionCall(name, args))),
                ),
            ),
        ),
    )

    private fun createFlowArgs(actionType: String = "TOAST") = buildJsonObject {
        put("name", "Test flow")
        put("trigger_logic", "ANY")
        putJsonArray("triggers") {
            add(buildJsonObject { put("type", "MANUAL") })
        }
        putJsonArray("actions") {
            add(
                buildJsonObject {
                    put("type", actionType)
                    put("config", buildJsonObject { if (actionType == "TOAST") put("message", "hi") })
                },
            )
        }
    }

    @Test
    fun `plain text reply becomes Assistant`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(textResponse("Which app should I open?"))

        val result = orchestrator().sendUserMessage("open an app on headset plug")

        assertEquals(TurnResult.Assistant("Which app should I open?"), result)
    }

    @Test
    fun `search_installed_apps is answered locally and the loop continues`() = runTest {
        every { installedApps.search("spotify") } returns
            listOf(InstalledAppsSource.AppEntry("Spotify", "com.spotify.music"))

        // The slot keeps only the latest call, i.e. the follow-up request after the tool reply
        val secondRequest = slot<GenerateContentRequest>()
        coEvery { client.generateContent(any(), any(), capture(secondRequest)) } returns
            Result.success(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "spotify") },
                ),
            ) andThen Result.success(textResponse("Found it."))

        val result = orchestrator().sendUserMessage("play spotify")

        assertEquals(TurnResult.Assistant("Found it."), result)
        // The follow-up request must carry the tool result back to the model
        val history = secondRequest.captured.contents
        val toolReply = history.last().parts.mapNotNull { it.functionResponse }
        assertEquals(AiTools.SEARCH_INSTALLED_APPS, toolReply.single().name)
        assertTrue("com.spotify.music" in toolReply.single().response.toString())
    }

    @Test
    fun `valid create_flow yields FlowReady with the model summary`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            Result.success(textResponse("Done — review and enable it."))

        val result = orchestrator().sendUserMessage("toast hi when I tap run")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        val ready = result as TurnResult.FlowReady
        assertEquals("Done — review and enable it.", ready.summaryText)
        assertEquals("Test flow", ready.flow.name)
        assertFalse(ready.flow.enabled)
    }

    @Test
    fun `invalid create_flow is repaired via error functionResponse`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE"))) andThen
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            Result.success(textResponse("Fixed and saved."))

        val result = orchestrator().sendUserMessage("do the thing")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        assertEquals("Fixed and saved.", (result as TurnResult.FlowReady).summaryText)
    }

    @Test
    fun `repair loop gives up after the attempt limit`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE")))

        val result = orchestrator().sendUserMessage("do the thing")

        assertInstanceOf(TurnResult.Failure::class.java, result)
        val failure = result as TurnResult.Failure
        assertInstanceOf(GeminiException.Unknown::class.java, failure.error)
        assertTrue("validation failed" in failure.error.message.orEmpty())
    }

    @Test
    fun `client errors surface as Failure with their original type`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.failure(GeminiException.RateLimited("slow down"))

        val result = orchestrator().sendUserMessage("hello")

        val failure = assertInstanceOf(TurnResult.Failure::class.java, result)
        assertInstanceOf(GeminiException.RateLimited::class.java, failure.error)
    }

    @Test
    fun `missing API key fails without calling the network`() = runTest {
        val emptyPrefs = mockk<SharedPreferences> {
            every { getString(any(), null) } returns null
        }
        val keylessContext = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns emptyPrefs
        }

        val result = AiChatOrchestrator(client, installedApps, globals, keylessContext)
            .sendUserMessage("hello")

        val failure = assertInstanceOf(TurnResult.Failure::class.java, result)
        assertInstanceOf(GeminiException.InvalidKey::class.java, failure.error)
    }

    @Test
    fun `thoughtSignature on functionCall parts survives into the replayed history`() = runTest {
        // Gemini 3 rejects follow-up requests (HTTP 400) when a functionCall part in history
        // lost its thoughtSignature, so the signature must round-trip verbatim.
        every { installedApps.search(any()) } returns emptyList()
        val secondRequest = slot<GenerateContentRequest>()
        coEvery { client.generateContent(any(), any(), capture(secondRequest)) } returns
            Result.success(
                GenerateContentResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                role = "model",
                                parts = listOf(
                                    GeminiPart(
                                        functionCall = GeminiFunctionCall(
                                            AiTools.SEARCH_INSTALLED_APPS,
                                            buildJsonObject { put("query", "x") },
                                        ),
                                        thoughtSignature = "sig-123",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ) andThen Result.success(textResponse("ok"))

        orchestrator().sendUserMessage("hello")

        val modelTurn = secondRequest.captured.contents.first { it.role == "model" }
        assertEquals("sig-123", modelTurn.parts.single().thoughtSignature)
    }

    @Test
    fun `thought summary parts are hidden from the user-facing reply`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(
                GenerateContentResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                role = "model",
                                parts = listOf(
                                    GeminiPart(text = "internal reasoning", thought = true),
                                    GeminiPart(text = "Visible answer"),
                                ),
                            ),
                        ),
                    ),
                ),
            )

        val result = orchestrator().sendUserMessage("hello")

        assertEquals(TurnResult.Assistant("Visible answer"), result)
    }

    @Test
    fun `progress callback reports each stage of the loop`() = runTest {
        every { installedApps.search(any()) } returns emptyList()
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "x") },
                ),
            ) andThen
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE"))) andThen
            Result.success(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            Result.success(textResponse("done"))

        val steps = mutableListOf<AiChatOrchestrator.Progress>()
        orchestrator().sendUserMessage("hello") { steps += it }

        assertEquals(
            listOf(
                AiChatOrchestrator.Progress.Contacting,
                AiChatOrchestrator.Progress.SearchingApps,
                AiChatOrchestrator.Progress.BuildingFlow,
                AiChatOrchestrator.Progress.Repairing(1),
                AiChatOrchestrator.Progress.BuildingFlow,
            ),
            steps,
        )
    }

    @Test
    fun `blocked or empty responses fail instead of looping`() = runTest {
        coEvery { client.generateContent(any(), any(), any()) } returns
            Result.success(
                GenerateContentResponse(
                    candidates = emptyList(),
                    promptFeedback = GeminiPromptFeedback(blockReason = "SAFETY"),
                ),
            )

        val result = orchestrator().sendUserMessage("hello")

        val failure = assertInstanceOf(TurnResult.Failure::class.java, result)
        assertTrue("SAFETY" in failure.error.message.orEmpty())
    }
}
