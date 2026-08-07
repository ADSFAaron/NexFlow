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
import android.util.Log
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.prefs.AiPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Multi-turn function-calling loop for one chat session. Unscoped — each ViewModel owns its
 * own conversation history.
 *
 * Per user turn: call generateContent; answer `search_installed_apps` / `search_app_shortcuts`
 * locally and continue; validate `create_flow` via [FlowDraftMapper], feeding errors back as a
 * functionResponse so Gemini can repair (bounded), and surface the validated flow with the
 * model's summary text.
 */
class AiChatOrchestrator @Inject constructor(
    private val client: GeminiClient,
    private val installedApps: InstalledAppsSource,
    private val appShortcuts: AppShortcutsSource,
    private val globalVariableRepository: GlobalVariableRepository,
    @param:ApplicationContext private val context: Context,
) {
    sealed interface TurnResult {
        /** Plain assistant reply (question, confirmation, etc.). */
        data class Assistant(val text: String) : TurnResult

        /** A flow passed validation; [summaryText] is the model's follow-up message. */
        data class FlowReady(val flow: FlowJson, val summaryText: String) : TurnResult

        data class Failure(val error: GeminiException) : TurnResult
    }

    /** What the loop is doing right now — surfaced under the thinking indicator. */
    sealed interface Progress {
        data object Contacting : Progress
        data object SearchingApps : Progress
        data object SearchingShortcuts : Progress
        data object BuildingFlow : Progress
        data class Repairing(val attempt: Int) : Progress
    }

    private val history = mutableListOf<GeminiContent>()

    /** Tokens this conversation has spent so far — the user is paying for their own key. */
    var tokensUsed: Int = 0
        private set

    fun reset() {
        history.clear()
        tokensUsed = 0
    }

    /** The model-side history, for persisting a conversation across process death. */
    fun exportHistory(): List<GeminiContent> = history.toList()

    /**
     * Restores a persisted conversation. Without this the user would come back to a transcript
     * they can read while the model has no idea what it says — which reads as the assistant
     * suddenly playing dumb.
     */
    fun restoreHistory(contents: List<GeminiContent>, tokens: Int) {
        history.clear()
        history += contents
        tokensUsed = tokens
    }

    /**
     * Starts a conversation about an existing flow (the "edit with Gemini" entry point).
     *
     * The flow travels as the first user turn plus a canned model acknowledgement, so from the
     * model's side this is indistinguishable from a flow it built earlier in the conversation —
     * which is exactly the case the system instruction's "call create_flow again with the
     * COMPLETE revised flow" rule already covers.
     */
    fun startFlowEditing(flowContext: String) {
        history.clear()
        tokensUsed = 0
        history += GeminiContent(role = "user", parts = listOf(GeminiPart(text = flowContext)))
        history += GeminiContent(
            role = "model",
            parts = listOf(GeminiPart(text = "Understood — I have the current flow. What would you like to change?")),
        )
    }

    /**
     * Runs one user turn. A turn either finishes and stays in [history], or leaves no trace.
     *
     * Half-finished turns are the trap: a network error, a cancelled stream or a spent repair
     * budget would otherwise leave a user message with no model reply behind it, and every
     * later request would carry two user turns in a row — plus an unanswered functionCall when
     * the failure landed mid-tool-loop. The visible transcript still keeps the user's message;
     * only the model-side history is rewound.
     */
    suspend fun sendUserMessage(
        text: String,
        onProgress: (Progress) -> Unit = {},
        onDelta: (String) -> Unit = {},
    ): TurnResult {
        // A snapshot, not an index: the turn also trims old entries off the front, which would
        // leave any position we remembered pointing at the wrong element.
        val before = history.toList()
        return try {
            runTurn(text, onProgress, onDelta).also { result ->
                if (result is TurnResult.Failure) history.restore(before)
            }
        } catch (e: CancellationException) {
            // The user hit stop. Same rule: nothing half-written survives.
            history.restore(before)
            throw e
        }
    }

    private fun MutableList<GeminiContent>.restore(snapshot: List<GeminiContent>) {
        clear()
        addAll(snapshot)
    }

    private suspend fun runTurn(
        text: String,
        onProgress: (Progress) -> Unit,
        onDelta: (String) -> Unit,
    ): TurnResult {
        val apiKey = AiPrefs.getApiKey(context)
            ?: return TurnResult.Failure(GeminiException.InvalidKey("API key not set"))
        val model = AiPrefs.getModel(context)

        history += GeminiContent(role = "user", parts = listOf(GeminiPart(text = text)))
        trimHistory()

        var repairAttempts = 0
        var pendingFlow: FlowJson? = null

        repeat(MAX_STEPS) { step ->
            if (step == 0) onProgress(Progress.Contacting)
            val request = GenerateContentRequest(
                contents = history.toList(),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = AiCatalog.systemInstruction(context))),
                ),
                tools = AiTools.tools(),
            )
            val response = runCatching { streamStep(apiKey, model, request, onDelta) }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "turn failed: ${e.javaClass.simpleName}: ${e.message}")
                return TurnResult.Failure(
                    e as? GeminiException ?: GeminiException.Unknown(e.message ?: "unexpected error"),
                )
            }

            val parts = response.parts
            if (parts.isEmpty()) {
                Log.e(TAG, "empty response, blockReason=${response.promptFeedback?.blockReason}")
                return TurnResult.Failure(
                    GeminiException.Unknown(
                        response.promptFeedback?.blockReason?.let { "blocked: $it" } ?: "empty response",
                    ),
                )
            }
            history += GeminiContent(role = "model", parts = parts)

            val functionCalls = parts.mapNotNull { it.functionCall }
            // Anything streamed during a step that turns out to be a tool call was the model
            // narrating its own bookkeeping — the non-streaming path dropped it too. Take the
            // placeholder back so the user doesn't watch a sentence appear and then get
            // contradicted by the real answer.
            if (functionCalls.isNotEmpty()) onDelta("")
            Log.d(
                TAG,
                "model turn: ${functionCalls.size} function call(s) ${functionCalls.map { it.name }}, " +
                    "text parts=${parts.count { it.text != null }}",
            )
            if (functionCalls.isEmpty()) {
                val reply = parts.visibleText()
                val flow = pendingFlow
                return if (flow != null) TurnResult.FlowReady(flow, reply) else TurnResult.Assistant(reply)
            }

            val responseParts = functionCalls.map { call ->
                when (call.name) {
                    AiTools.SEARCH_INSTALLED_APPS -> {
                        onProgress(Progress.SearchingApps)
                        searchAppsResponse(call)
                    }

                    AiTools.SEARCH_APP_SHORTCUTS -> {
                        onProgress(Progress.SearchingShortcuts)
                        searchShortcutsResponse(call)
                    }

                    AiTools.CREATE_FLOW -> {
                        onProgress(Progress.BuildingFlow)
                        val draft = FlowDraftMapper.fromArgs(
                            call.args,
                            context,
                            knownGlobals = globalVariableRepository.currentValues().keys,
                        )
                        if (draft.isValid) {
                            Log.d(TAG, "create_flow validated: \"${draft.flow?.name}\"")
                            pendingFlow = draft.flow
                            functionResponse(call.name, buildJsonObject { put("status", "ok") })
                        } else {
                            repairAttempts++
                            onProgress(Progress.Repairing(repairAttempts))
                            Log.w(
                                TAG,
                                "create_flow rejected (attempt $repairAttempts/$MAX_REPAIR_ATTEMPTS): ${draft.errors}",
                            )
                            if (repairAttempts > MAX_REPAIR_ATTEMPTS) {
                                return TurnResult.Failure(
                                    GeminiException.Unknown(
                                        "flow validation failed after $MAX_REPAIR_ATTEMPTS attempts: " +
                                            draft.errors.joinToString("; "),
                                    ),
                                )
                            }
                            functionResponse(
                                call.name,
                                buildJsonObject {
                                    put("status", "error")
                                    putJsonArray("errors") { draft.errors.forEach { add(it) } }
                                },
                            )
                        }
                    }

                    else -> functionResponse(
                        call.name,
                        buildJsonObject {
                            put("status", "error")
                            put("message", "unknown function ${call.name}")
                        },
                    )
                }
            }
            history += GeminiContent(role = "user", parts = responseParts)
        }

        return TurnResult.Failure(GeminiException.Unknown("conversation step limit reached"))
    }

    /**
     * Drops the oldest turns once the conversation gets long. Every request replays the whole
     * history, so an unbounded one costs more tokens on each turn and eventually hits the
     * model's context limit — the user pays for both.
     *
     * Two rules make the trim safe. The preamble (the flow being edited, plus the canned
     * acknowledgement) is never dropped, because the model would lose the very thing the
     * conversation is about. And cuts land only where a plain user turn starts: slicing between
     * a functionCall and its functionResponse produces a history Gemini rejects outright.
     */
    private fun trimHistory() {
        if (history.size <= MAX_HISTORY_CONTENTS) return
        val target = history.size - MAX_HISTORY_CONTENTS
        var cut = PREAMBLE_CONTENTS
        var dropped = 0
        while (dropped < target && cut < history.size) {
            cut++
            dropped++
            // Advance to the next plain user turn, so a tool exchange is never split.
            while (cut < history.size && !history[cut].isPlainUserTurn()) {
                cut++
                dropped++
            }
        }
        if (cut > PREAMBLE_CONTENTS && cut < history.size) {
            Log.d(TAG, "trimming $dropped old history entries (was ${history.size})")
            history.subList(PREAMBLE_CONTENTS, cut).clear()
        }
    }

    /** A turn the user typed, as opposed to a functionResponse (which also uses role "user"). */
    private fun GeminiContent.isPlainUserTurn(): Boolean =
        role == "user" && parts.all { it.functionResponse == null }

    /**
     * Runs one model call as a stream and folds the chunks back into the single response the
     * rest of the loop already knows how to handle — the tool-calling logic never learns that
     * streaming happened.
     *
     * Text is reported through [onDelta] as it accumulates so the UI can render a reply that
     * grows. Everything else (functionCall parts and their `thoughtSignature`, block reasons,
     * token usage) is merged verbatim, because history replay depends on it round-tripping.
     */
    private suspend fun streamStep(
        apiKey: String,
        model: String,
        request: GenerateContentRequest,
        onDelta: (String) -> Unit,
    ): GenerateContentResponse {
        val parts = mutableListOf<GeminiPart>()
        var feedback: GeminiPromptFeedback? = null
        var usage: GeminiUsageMetadata? = null
        var lastVisible = ""

        // Each step starts from a blank placeholder: a previous step's narration must not
        // linger under the next one's output.
        onDelta("")
        client.streamGenerateContent(apiKey, model, request).collect { chunk ->
            chunk.promptFeedback?.let { feedback = it }
            chunk.usageMetadata?.let { usage = it }
            chunk.parts.forEach { parts.appendMerging(it) }
            val visible = parts.visibleText()
            if (visible != lastVisible) {
                lastVisible = visible
                onDelta(visible)
            }
        }
        usage?.let { tokensUsed += it.totalTokenCount }

        return GenerateContentResponse(
            candidates = listOf(
                GeminiCandidate(content = GeminiContent(role = "model", parts = parts)),
            ),
            promptFeedback = feedback,
            usageMetadata = usage,
        )
    }

    /**
     * Appends a streamed part, joining it to the previous one when both are plain text of the
     * same kind — the API splits a sentence across chunks, and history should hold the sentence,
     * not the fragments. Parts carrying a signature or a call are never merged.
     */
    private fun MutableList<GeminiPart>.appendMerging(part: GeminiPart) {
        val previous = lastOrNull()
        val mergeable = part.text != null &&
            part.functionCall == null &&
            part.functionResponse == null &&
            part.thoughtSignature == null &&
            previous?.text != null &&
            previous.functionCall == null &&
            previous.functionResponse == null &&
            previous.thoughtSignature == null &&
            previous.thought == part.thought
        if (mergeable) {
            this[lastIndex] = previous.copy(text = previous.text + part.text)
        } else {
            add(part)
        }
    }

    /** The user-facing text of a step so far — thought summaries are the model's, not theirs. */
    private fun List<GeminiPart>.visibleText(): String =
        filter { it.thought != true }.mapNotNull { it.text }.joinToString("\n").trim()

    private suspend fun searchAppsResponse(call: GeminiFunctionCall): GeminiPart {
        val query = (call.args["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val results = installedApps.search(query)
        Log.d(TAG, "search_installed_apps(\"$query\") → ${results.size} result(s)")
        return functionResponse(
            call.name,
            buildJsonObject {
                putJsonArray("apps") {
                    results.forEach { app ->
                        add(
                            buildJsonObject {
                                put("label", app.label)
                                put("package_name", app.packageName)
                            },
                        )
                    }
                }
                if (results.isEmpty()) put("note", "no installed app matched \"$query\"")
            },
        )
    }

    /**
     * Only static, exported shortcuts are reachable (see [AppShortcutsSource]); when the app has
     * none the model needs to know *why* so it can explain the fallback instead of retrying.
     */
    private suspend fun searchShortcutsResponse(call: GeminiFunctionCall): GeminiPart {
        val packageName = (call.args["package_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val result = appShortcuts.shortcutsFor(packageName)
        Log.d(
            TAG,
            "search_app_shortcuts(\"$packageName\") → ${result.shortcuts.size} launchable, " +
                "${result.notLaunchableCount} blocked, ${result.configurableCount} configurable",
        )
        return functionResponse(
            call.name,
            buildJsonObject {
                putJsonArray("shortcuts") {
                    result.shortcuts.forEach { shortcut ->
                        add(
                            buildJsonObject {
                                put("label", shortcut.label)
                                put("intent_uri", shortcut.intentUri)
                            },
                        )
                    }
                }
                if (result.shortcuts.isEmpty()) {
                    put(
                        "note",
                        buildString {
                            append("\"$packageName\" publishes no shortcut NexFlow can launch")
                            if (result.notLaunchableCount > 0) {
                                append("; ${result.notLaunchableCount} exist but only its own launcher may start them")
                            }
                            if (result.configurableCount > 0) {
                                append(
                                    "; ${result.configurableCount} must be configured interactively — " +
                                        "the user can add them from the shortcut picker in the editor",
                                )
                            }
                            append(". Use OPEN_APP instead and tell the user why.")
                        },
                    )
                }
            },
        )
    }

    private fun functionResponse(name: String, response: kotlinx.serialization.json.JsonObject) =
        GeminiPart(functionResponse = GeminiFunctionResponse(name = name, response = response))

    private companion object {
        const val TAG = "AiChatOrchestrator"

        /** Upper bound on model round-trips per user turn (tool answers + repairs). */
        const val MAX_STEPS = 8
        const val MAX_REPAIR_ATTEMPTS = 3

        /**
         * How many `contents` a request may replay. Roughly twenty exchanges — long enough that
         * no real conversation notices, short enough that cost stops growing forever.
         */
        const val MAX_HISTORY_CONTENTS = 40

        /**
         * The flow-context turn and its acknowledgement, which must never be trimmed away. In a
         * free-form chat these are the opening exchange, kept for the same reason: it's usually
         * where the user said what they're trying to build.
         */
        const val PREAMBLE_CONTENTS = 2
    }
}
