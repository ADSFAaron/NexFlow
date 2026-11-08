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
import io.mockk.verify
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
        every { getString("ai_provider", null) } returns null
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }
    private val client = mockk<GeminiClient>()
    private val compatibleClient = mockk<OpenAiCompatibleClient>()
    private val installedApps = mockk<InstalledAppsSource> {
        coEvery { launchablePackages() } returns setOf("com.spotify.music")
    }
    private val globals = mockk<GlobalVariableRepository> {
        coEvery { currentValues() } returns emptyMap()
    }

    private val appShortcuts = mockk<AppShortcutsSource>()
    private val images = mockk<AiImageStore>()

    private fun orchestrator() =
        AiChatOrchestrator(client, compatibleClient, installedApps, appShortcuts, globals, images, context)

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

    private fun createFlowArgs(
        actionType: String = "TOAST",
        summary: String? = "Done — review and enable it.",
    ) = buildJsonObject {
        summary?.let { put("summary", it) }
        put("name", "Test flow")
        put("description", "Shows a toast")
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
    fun `a custom service gets the request and Gemini is left alone`() = runTest {
        every { prefs.getString("ai_provider", null) } returns "CUSTOM"
        every { prefs.getString("custom_base_url", null) } returns "http://localhost:11434/v1"
        every { prefs.getString("custom_model", null) } returns "qwen3"
        every { prefs.getString("custom_api_key", null) } returns null
        val endpoint = slot<CustomEndpoint>()
        every { compatibleClient.streamGenerateContent(capture(endpoint), any()) } returns
            chunks(textResponse("hi"))

        val result = orchestrator().sendUserMessage("hello")

        assertEquals("hi", (result as TurnResult.Assistant).text)
        assertEquals(CustomEndpoint("http://localhost:11434/v1", null, "qwen3"), endpoint.captured)
        verify(exactly = 0) { client.streamGenerateContent(any(), any(), any()) }
    }

    @Test
    fun `a custom service that lost its settings fails without calling anything`() = runTest {
        every { prefs.getString("ai_provider", null) } returns "CUSTOM"
        every { prefs.getString("custom_base_url", null) } returns null

        val result = orchestrator().sendUserMessage("hello")

        assertInstanceOf(GeminiException.InvalidKey::class.java, (result as TurnResult.Failure).error)
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
    fun `valid create_flow ends the turn with the summary from its own arguments`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs()))

        val result = orchestrator().sendUserMessage("toast hi when I tap run")

        verify(exactly = 1) { client.streamGenerateContent(any(), any(), any()) }

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
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs(summary = "Fixed and saved.")))

        val result = orchestrator().sendUserMessage("do the thing")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        assertEquals("Fixed and saved.", (result as TurnResult.FlowReady).summaryText)
    }

    @Test
    fun `a create_flow with no summary falls back to the flow's description`() = runTest {
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs(summary = null)))

        val ready = orchestrator().sendUserMessage("toast hi") as TurnResult.FlowReady

        assertEquals(ready.flow.description, ready.summaryText)
    }

    @Test
    fun `the message after a finished flow follows a model turn, not a functionResponse`() = runTest {
        val orchestrator = orchestrator()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs()))
        orchestrator.sendUserMessage("toast hi")

        val next = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(next)) } returns
            chunks(textResponse("ok"))
        orchestrator.sendUserMessage("change it")

        val contents = next.captured.contents
        assertEquals("user", contents.last().role)
        assertEquals("model", contents[contents.lastIndex - 1].role)
        assertEquals("Done — review and enable it.", contents[contents.lastIndex - 1].parts.single().text)
    }

    @Test
    fun `a step that searched as well as built keeps going, because it asked for an answer`() = runTest {
        coEvery { installedApps.search(any()) } returns emptyList()
        val both = GenerateContentResponse(
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
                            ),
                            GeminiPart(functionCall = GeminiFunctionCall(AiTools.CREATE_FLOW, createFlowArgs())),
                        ),
                    ),
                ),
            ),
        )
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(both) andThen chunks(textResponse("Reply after the search."))

        val ready = orchestrator().sendUserMessage("hello") as TurnResult.FlowReady

        assertEquals("Reply after the search.", ready.summaryText)
    }

    private fun openAppArgs(packageName: String) = buildJsonObject {
        put("name", "Open")
        put("description", "Opens an app")
        put("summary", "Opens it.")
        putJsonArray("triggers") { add(buildJsonObject { put("type", "MANUAL") }) }
        putJsonArray("actions") {
            add(
                buildJsonObject {
                    put("type", "OPEN_APP")
                    put("config", """{"package_name":"$packageName"}""")
                },
            )
        }
    }

    @Test
    fun `an app that isn't installed goes back to the model for repair`() = runTest {
        val repair = mutableListOf<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(repair)) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, openAppArgs("com.made.up"))) andThen
            chunks(functionCallResponse(AiTools.CREATE_FLOW, openAppArgs("com.spotify.music")))

        val result = orchestrator().sendUserMessage("open my music app")

        assertInstanceOf(TurnResult.FlowReady::class.java, result)
        val errors = repair.last().contents.last().parts.single().functionResponse?.response.toString()
        assertTrue("com.made.up" in errors && "search_installed_apps" in errors, errors)
    }

    @Test
    fun `editing a flow may keep an app that has since been uninstalled`() = runTest {
        val orchestrator = orchestrator()
        orchestrator.startFlowEditing("""actions: OPEN_APP config: {"package_name":"com.gone.app"}""")
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.CREATE_FLOW, openAppArgs("com.gone.app")))

        assertInstanceOf(TurnResult.FlowReady::class.java, orchestrator.sendUserMessage("rename it"))
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

        val result = AiChatOrchestrator(client, compatibleClient, installedApps, appShortcuts, globals, images, keylessContext)
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
            chunks(functionCallResponse(AiTools.CREATE_FLOW, createFlowArgs()))

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

    // --- Busy server (503 "high demand") -------------------------------------

    @Test
    fun `a busy server is retried and the turn succeeds once it gets through`() = runTest {
        val busy = GeminiException.Unavailable("This model is currently experiencing high demand.")
        every { client.streamGenerateContent(any(), any(), any()) } returns
            failingStream(busy) andThen failingStream(busy) andThen chunks(textResponse("hi"))

        val steps = mutableListOf<AiChatOrchestrator.Progress>()
        val result = orchestrator().sendUserMessage("hello", onProgress = { steps += it })

        assertEquals("hi", (result as TurnResult.Assistant).text)
        assertEquals(
            listOf(
                AiChatOrchestrator.Progress.Contacting,
                AiChatOrchestrator.Progress.Retrying(1, 3),
                AiChatOrchestrator.Progress.Retrying(2, 3),
                // Back to what it was doing, not left saying the server is busy.
                AiChatOrchestrator.Progress.Contacting,
            ),
            steps,
        )
    }

    @Test
    fun `a server that stays busy is reported after three retries`() = runTest {
        var calls = 0
        every { client.streamGenerateContent(any(), any(), any()) } answers {
            calls++
            failingStream(GeminiException.Unavailable("high demand"))
        }

        val result = orchestrator().sendUserMessage("hello")

        assertInstanceOf(GeminiException.Unavailable::class.java, (result as TurnResult.Failure).error)
        assertEquals(4, calls, "one request plus three retries")
    }

    @Test
    fun `other errors are not retried`() = runTest {
        var calls = 0
        every { client.streamGenerateContent(any(), any(), any()) } answers {
            calls++
            failingStream(GeminiException.ModelNotFound("models/nope is not found"))
        }

        val result = orchestrator().sendUserMessage("hello")

        assertInstanceOf(GeminiException.ModelNotFound::class.java, (result as TurnResult.Failure).error)
        assertEquals(1, calls)
    }

    @Test
    fun `a stream that fails after showing text is not replayed`() = runTest {
        // Retrying here would put the start of the answer on screen twice.
        var calls = 0
        every { client.streamGenerateContent(any(), any(), any()) } answers {
            calls++
            flow {
                emit(textResponse("Half an ans"))
                throw GeminiException.Unavailable("high demand")
            }
        }

        val result = orchestrator().sendUserMessage("hello")

        assertInstanceOf(GeminiException.Unavailable::class.java, (result as TurnResult.Failure).error)
        assertEquals(1, calls)
    }

    @Test
    fun `a retried step after a tool call does not run the tool again`() = runTest {
        coEvery { installedApps.search(any()) } returns emptyList()
        every { client.streamGenerateContent(any(), any(), any()) } returns
            chunks(functionCallResponse(AiTools.SEARCH_INSTALLED_APPS, buildJsonObject { put("query", "x") })) andThen
            failingStream(GeminiException.Unavailable("high demand")) andThen
            chunks(textResponse("done"))

        val result = orchestrator().sendUserMessage("hello")

        assertEquals("done", (result as TurnResult.Assistant).text)
        io.mockk.coVerify(exactly = 1) { installedApps.search(any()) }
    }

    // --- Quota (429) ---------------------------------------------------------

    @Test
    fun `a spent per-minute quota is waited out for as long as Google says`() = runTest {
        val perMinute = GeminiException.RateLimited("quota exceeded", retryAfterMillis = 2_500)
        every { client.streamGenerateContent(any(), any(), any()) } returns
            failingStream(perMinute) andThen chunks(textResponse("hi"))

        val steps = mutableListOf<AiChatOrchestrator.Progress>()
        val start = testScheduler.currentTime
        val result = orchestrator().sendUserMessage("hello", onProgress = { steps += it })

        assertEquals("hi", (result as TurnResult.Assistant).text)
        // Google's delay plus a second of slack, so the retry doesn't land on the window's edge.
        assertEquals(3_500, testScheduler.currentTime - start)
        assertEquals(
            listOf(
                AiChatOrchestrator.Progress.Contacting,
                AiChatOrchestrator.Progress.WaitingForQuota(4),
                AiChatOrchestrator.Progress.WaitingForQuota(3),
                AiChatOrchestrator.Progress.WaitingForQuota(2),
                AiChatOrchestrator.Progress.WaitingForQuota(1),
                AiChatOrchestrator.Progress.Contacting,
            ),
            steps,
        )
    }

    @Test
    fun `a 429 without a delay still gets a few waits before it is reported`() = runTest {
        var calls = 0
        every { client.streamGenerateContent(any(), any(), any()) } answers {
            calls++
            failingStream(GeminiException.RateLimited("quota exceeded"))
        }

        val result = orchestrator().sendUserMessage("hello")

        assertInstanceOf(GeminiException.RateLimited::class.java, (result as TurnResult.Failure).error)
        assertEquals(4, calls, "one request plus three waits")
    }

    @Test
    fun `a daily quota or a model outside the plan fails at once`() = runTest {
        for (error in listOf(
            GeminiException.RateLimited("per day", retryAfterMillis = 1_000, daily = true),
            GeminiException.RateLimited("limit: 0", retryAfterMillis = 1_000, noQuota = true),
            // Not a per-minute window whatever it claims to be, and nobody waits ten minutes.
            GeminiException.RateLimited("later", retryAfterMillis = 600_000),
        )) {
            var calls = 0
            every { client.streamGenerateContent(any(), any(), any()) } answers {
                calls++
                failingStream(error)
            }

            val result = orchestrator().sendUserMessage("hello")

            assertEquals(error, (result as TurnResult.Failure).error)
            assertEquals(1, calls, error.message)
        }
    }

    // --- Images --------------------------------------------------------------

    @Test
    fun `an attached image is sent as bytes but kept in history as its file name`() = runTest {
        coEvery { images.base64("a.jpg") } returns "QUJD"
        val request = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(request)) } returns
            chunks(textResponse("I see a form."))

        val orchestrator = orchestrator()
        orchestrator.sendUserMessage("fill this in", imageNames = listOf("a.jpg"))

        val sent = request.captured.contents.first().parts
        assertEquals("fill this in", sent[0].text)
        assertEquals(GeminiBlob("image/jpeg", "QUJD"), sent[1].inlineData)
        // The persisted history must stay small: a reference, never the bytes.
        assertEquals(
            GeminiBlob("image/jpeg", "a.jpg"),
            orchestrator.exportHistory().first().parts[1].inlineData,
        )
    }

    @Test
    fun `an image whose file is gone becomes a note instead of a broken request`() = runTest {
        coEvery { images.base64("gone.jpg") } returns null
        val request = slot<GenerateContentRequest>()
        every { client.streamGenerateContent(any(), any(), capture(request)) } returns
            chunks(textResponse("ok"))

        orchestrator().sendUserMessage("", imageNames = listOf("gone.jpg"))

        val part = request.captured.contents.first().parts.single()
        assertEquals(null, part.inlineData)
        assertTrue(part.text!!.contains("no longer included"))
    }
}
