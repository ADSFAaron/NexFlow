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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val appShortcuts = mockk<AppShortcutsSource>()

    private fun orchestrator() = AiChatOrchestrator(client, installedApps, appShortcuts, globals, context)

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

    /** One SSE chunk carrying a whole response — what the tests mean by "the model replied". */
    private fun chunks(vararg responses: GenerateContentResponse): Flow<GenerateContentResponse> =
        flowOf(*responses)

    /** A stream that fails before its first chunk, i.e. the request itself was rejected. */
    private fun failingStream(error: GeminiException): Flow<GenerateContentResponse> =
        flow { throw error }

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
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(textResponse("Which app should I open?"))

        val result = orchestrator().sendUserMessage("open an app on headset plug")

        assertEquals(TurnResult.Assistant("Which app should I open?"), result)
    }

    @Test
    fun `search_installed_apps is answered locally and the loop continues`() = runTest {
        coEvery { installedApps.search("spotify") } returns
            listOf(InstalledAppsSource.AppEntry("Spotify", "com.spotify.music"))

        // The slot keeps only the latest call, i.e. the follow-up request after the tool reply
        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "spotify") },
                ),
            ) andThen chunks(textResponse("Found it."))

        val result = orchestrator().sendUserMessage("play spotify")

        assertEquals(TurnResult.Assistant("Found it."), result)
        // The follow-up request must carry the tool result back to the model
        val history = secondRequest.captured.contents
        val toolReply = history.last().parts.mapNotNull { it.functionResponse }
        assertEquals(AiTools.SEARCH_INSTALLED_APPS, toolReply.single().name)
        assertTrue("com.spotify.music" in toolReply.single().response.toString())
    }

    /**
     * A label alone only identifies apps the model already knows; what the device can say about an
     * app's purpose has to reach it. Fields the device does not have must be left out rather than
     * sent empty, so they cost nothing on the overwhelming majority of apps that declare none.
     */
    @Test
    fun `search_installed_apps forwards the category, roles and description it has`() = runTest {
        coEvery { installedApps.search("music") } returns listOf(
            InstalledAppsSource.AppEntry(
                label = "Fictional Player",
                packageName = "com.example.player",
                category = "AUDIO",
                roles = listOf("MUSIC"),
                description = "Plays your offline library.",
            ),
            InstalledAppsSource.AppEntry(label = "Bare App", packageName = "com.example.bare"),
        )

        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "music") },
                ),
            ) andThen chunks(textResponse("Done."))

        orchestrator().sendUserMessage("open a music app")

        val apps = secondRequest.captured.contents.last().parts
            .mapNotNull { it.functionResponse }.single()
            .response["apps"]!!.jsonArray
        val enriched = apps[0].jsonObject
        assertEquals("AUDIO", enriched["category"]?.jsonPrimitive?.content)
        assertEquals(listOf("MUSIC"), enriched["roles"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("Plays your offline library.", enriched["description"]?.jsonPrimitive?.content)

        val bare = apps[1].jsonObject
        assertEquals("com.example.bare", bare["package_name"]?.jsonPrimitive?.content)
        assertFalse("category" in bare)
        assertFalse("roles" in bare)
        assertFalse("description" in bare)
    }

    @Test
    fun `search_app_shortcuts hands the model a launchable intent_uri`() = runTest {
        coEvery { appShortcuts.shortcutsFor("com.pay.app") } returns AppShortcutsSource.Result(
            shortcuts = listOf(AppShortcutsSource.ShortcutEntry("Pay code", "intent:#Intent;end")),
            notLaunchableCount = 0,
            configurableCount = 0,
        )

        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_APP_SHORTCUTS,
                    buildJsonObject { put("package_name", "com.pay.app") },
                ),
            ) andThen chunks(textResponse("Found it."))

        orchestrator().sendUserMessage("open the payment code")

        val toolReply = secondRequest.captured.contents.last().parts.mapNotNull { it.functionResponse }.single()
        assertEquals(AiTools.SEARCH_APP_SHORTCUTS, toolReply.name)
        assertTrue("intent:#Intent;end" in toolReply.response.toString())
    }

    @Test
    fun `search_app_shortcuts explains why an app yielded nothing`() = runTest {
        coEvery { appShortcuts.shortcutsFor(any()) } returns AppShortcutsSource.Result(
            shortcuts = emptyList(),
            notLaunchableCount = 3,
            configurableCount = 1,
        )

        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_APP_SHORTCUTS,
                    buildJsonObject { put("package_name", "com.maps.app") },
                ),
            ) andThen chunks(textResponse("I'll just open the app."))

        orchestrator().sendUserMessage("open my saved place")

        val note = secondRequest.captured.contents.last().parts
            .mapNotNull { it.functionResponse }.single().response.toString()
        assertTrue("OPEN_APP" in note, note)
        assertTrue("3" in note && "1" in note, note)
    }

    @Test
    fun `valid create_flow yields FlowReady with the model summary`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            chunks(textResponse("Done — review and enable it."))

        val result = orchestrator().sendUserMessage("toast hi when I tap run")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        val ready = result as TurnResult.FlowReady
        assertEquals("Done — review and enable it.", ready.summaryText)
        assertEquals("Test flow", ready.flow.name)
        assertFalse(ready.flow.enabled)
    }

    @Test
    fun `invalid create_flow is repaired via error functionResponse`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE"))) andThen
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            chunks(textResponse("Fixed and saved."))

        val result = orchestrator().sendUserMessage("do the thing")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        assertEquals("Fixed and saved.", (result as TurnResult.FlowReady).summaryText)
    }

    @Test
    fun `repair loop gives up after the attempt limit`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE")))

        val result = orchestrator().sendUserMessage("do the thing")

        assertInstanceOf(TurnResult.Failure::class.java, result)
        val failure = result as TurnResult.Failure
        assertInstanceOf(GeminiException.Unknown::class.java, failure.error)
        assertTrue("validation failed" in failure.error.message.orEmpty())
    }

    @Test
    fun `client errors surface as Failure with their original type`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            failingStream(GeminiException.RateLimited("slow down"))

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

        val result = AiChatOrchestrator(client, installedApps, appShortcuts, globals, keylessContext)
            .sendUserMessage("hello")

        val failure = assertInstanceOf(TurnResult.Failure::class.java, result)
        assertInstanceOf(GeminiException.InvalidKey::class.java, failure.error)
    }

    @Test
    fun `thoughtSignature on functionCall parts survives into the replayed history`() = runTest {
        // Gemini 3 rejects follow-up requests (HTTP 400) when a functionCall part in history
        // lost its thoughtSignature, so the signature must round-trip verbatim.
        coEvery { installedApps.search(any()) } returns emptyList()
        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
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
            ) andThen chunks(textResponse("ok"))

        orchestrator().sendUserMessage("hello")

        val modelTurn = secondRequest.captured.contents.first { it.role == "model" }
        assertEquals("sig-123", modelTurn.parts.single().thoughtSignature)
    }

    @Test
    fun `thought summary parts are hidden from the user-facing reply`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(
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
        coEvery { installedApps.search(any()) } returns emptyList()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "x") },
                ),
            ) andThen
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE"))) andThen
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs())) andThen
            chunks(textResponse("done"))

        val steps = mutableListOf<AiChatOrchestrator.Progress>()
        orchestrator().sendUserMessage("hello", onProgress = { steps += it })

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
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(
                GenerateContentResponse(
                    candidates = emptyList(),
                    promptFeedback = GeminiPromptFeedback(blockReason = "SAFETY"),
                ),
            )

        val result = orchestrator().sendUserMessage("hello")

        val failure = assertInstanceOf(TurnResult.Failure::class.java, result)
        assertTrue("SAFETY" in failure.error.message.orEmpty())
    }

    // A turn that doesn't finish must leave the history exactly as it found it. Otherwise the
    // dropped call leaves a user turn with no model reply behind it, and every later request
    // carries two user turns in a row — or worse, an unanswered functionCall.

    @Test
    fun `a failed turn leaves no trace in the model history`() = runTest {
        val orchestrator = orchestrator()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            failingStream(GeminiException.Network("offline"))
        assertInstanceOf(TurnResult.Failure::class.java, orchestrator.sendUserMessage("first"))

        val retryRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(retryRequest)) } returns
            chunks(textResponse("ok"))
        orchestrator.sendUserMessage("second")

        val userTurns = retryRequest.captured.contents.filter { it.role == "user" }
        assertEquals(1, userTurns.size, "the failed turn must not linger in the history")
        assertEquals("second", userTurns.single().parts.single().text)
    }

    @Test
    fun `a spent repair budget rewinds the tool calls it made`() = runTest {
        val orchestrator = orchestrator()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs("FROBNICATE")))
        assertInstanceOf(TurnResult.Failure::class.java, orchestrator.sendUserMessage("first"))

        val nextRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(nextRequest)) } returns
            chunks(textResponse("ok"))
        orchestrator.sendUserMessage("second")

        val contents = nextRequest.captured.contents
        assertEquals(1, contents.size, "the whole repair round trip must be gone")
        assertTrue(contents.single().parts.none { it.functionCall != null || it.functionResponse != null })
    }

    @Test
    fun `streamed text is reported as it grows and joined into one reply`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(textResponse("Hel"), textResponse("lo there"))

        val deltas = mutableListOf<String>()
        val result = orchestrator().sendUserMessage("hi", onDelta = { deltas += it })

        assertEquals(TurnResult.Assistant("Hello there"), result)
        // Starts blank, then each chunk extends what the user already sees
        assertEquals(listOf("", "Hel", "Hello there"), deltas)
    }

    @Test
    fun `chunked text lands in the history as one part, not fragments`() = runTest {
        coEvery { installedApps.search(any()) } returns emptyList()
        val secondRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(secondRequest)) } returns
            chunks(
                textResponse("Checking"),
                textResponse(" now"),
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "x") },
                ),
            ) andThen chunks(textResponse("done"))

        orchestrator().sendUserMessage("hello")

        val modelTurn = secondRequest.captured.contents.first { it.role == "model" }
        assertEquals(listOf("Checking now"), modelTurn.parts.mapNotNull { it.text })
    }

    @Test
    fun `text streamed before a tool call is taken back off the screen`() = runTest {
        coEvery { installedApps.search(any()) } returns emptyList()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(
                textResponse("Let me look that up"),
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "x") },
                ),
            ) andThen chunks(textResponse("Found it."))

        val deltas = mutableListOf<String>()
        val result = orchestrator().sendUserMessage("hello", onDelta = { deltas += it })

        assertEquals(TurnResult.Assistant("Found it."), result)
        // The narration showed up, then was cleared once the step turned out to be a tool call
        val narrationAt = deltas.indexOf("Let me look that up")
        assertTrue(narrationAt >= 0, "the narration should have been streamed: $deltas")
        assertEquals("", deltas[narrationAt + 1], "the placeholder must be taken back: $deltas")
    }

    @Test
    fun `a long conversation stops growing without losing what it is about`() = runTest {
        val orchestrator = orchestrator()
        orchestrator.startFlowEditing("CURRENT FLOW — drive mode")
        val request = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(request)) } returns
            chunks(textResponse("ok"))

        repeat(40) { orchestrator.sendUserMessage("message $it") }

        val contents = request.captured.contents
        // Bounded: every request replays the history, so unbounded growth costs real money
        assertTrue(contents.size <= 41, "history grew to ${contents.size}")
        // ...but the flow the conversation is about is still the first thing the model sees
        assertTrue("drive mode" in contents.first().parts.first().text.orEmpty())
        // ...and the newest exchange is still there
        assertTrue(contents.any { it.parts.any { p -> p.text == "message 39" } })
    }

    @Test
    fun `trimming never cuts a tool call away from its answer`() = runTest {
        val orchestrator = orchestrator()
        orchestrator.startFlowEditing("CURRENT FLOW — drive mode")
        coEvery { installedApps.search(any()) } returns emptyList()
        val request = slot<GenerateContentRequest>()
        // Every turn is a tool round trip, so the history fills with call/response pairs
        every { client.streamGenerateContent(any(), any(), capture(request)) } answers {
            chunks(
                functionCallResponse(
                    AiTools.SEARCH_INSTALLED_APPS,
                    buildJsonObject { put("query", "x") },
                ),
            )
        } andThenAnswer { chunks(textResponse("ok")) }

        repeat(30) { orchestrator.sendUserMessage("message $it") }

        // A functionResponse whose functionCall was trimmed away is a history Gemini rejects
        val contents = request.captured.contents
        contents.forEachIndexed { index, content ->
            if (content.parts.any { it.functionResponse != null }) {
                val previous = contents.getOrNull(index - 1)
                assertTrue(
                    previous?.parts?.any { it.functionCall != null } == true,
                    "orphaned functionResponse at $index",
                )
            }
        }
    }

    @Test
    fun `a turn that fails after the history was trimmed still leaves no trace`() = runTest {
        // Trimming shifts everything, so a rollback that remembered a position instead of the
        // contents would rewind to the wrong place and strand the failed message.
        val orchestrator = orchestrator()
        orchestrator.startFlowEditing("CURRENT FLOW — drive mode")
        every { client.streamGenerateContent(any(), any(), any()) } returns chunks(textResponse("ok"))
        repeat(45) { orchestrator.sendUserMessage("filler $it") }

        every { client.streamGenerateContent(any(), any(), any()) } returns
            failingStream(GeminiException.Network("offline"))
        assertInstanceOf(TurnResult.Failure::class.java, orchestrator.sendUserMessage("doomed"))

        val nextRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(nextRequest)) } returns
            chunks(textResponse("ok"))
        orchestrator.sendUserMessage("after")

        val texts = nextRequest.captured.contents.flatMap { it.parts.mapNotNull { p -> p.text } }
        assertFalse("doomed" in texts, "the failed message survived the rollback: $texts")
        assertTrue("after" in texts)
    }

    @Test
    fun `stopping a turn rewinds the history so the next one starts clean`() = runTest {
        val orchestrator = orchestrator()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            flow { awaitCancellation() }

        val turn = launch { orchestrator.sendUserMessage("first") }
        advanceUntilIdle()
        turn.cancelAndJoin()

        val nextRequest = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(nextRequest)) } returns
            chunks(textResponse("ok"))
        orchestrator.sendUserMessage("second")

        val userTurns = nextRequest.captured.contents.filter { it.role == "user" }
        assertEquals(1, userTurns.size, "the cancelled turn must not linger in the history")
        assertEquals("second", userTurns.single().parts.single().text)
    }
}
