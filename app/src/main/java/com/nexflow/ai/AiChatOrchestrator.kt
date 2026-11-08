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
import com.nexflow.prefs.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * functionResponse so Gemini can repair (bounded). A validated flow ends the turn with the
 * summary the model put in its `create_flow` call.
 */
class AiChatOrchestrator @Inject constructor(
    private val client: GeminiClient,
    private val compatibleClient: OpenAiCompatibleClient,
    private val installedApps: InstalledAppsSource,
    private val appShortcuts: AppShortcutsSource,
    private val globalVariableRepository: GlobalVariableRepository,
    private val images: AiImageStore,
    @param:ApplicationContext private val context: Context,
) {
    sealed interface TurnResult {
        /** Plain assistant reply (question, confirmation, etc.). */
        data class Assistant(val text: String) : TurnResult

        /** A flow passed validation; [summaryText] is what the model wrote for the user. */
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

        /** Gemini answered "busy"; waiting before sending the same request again. */
        data class Retrying(val attempt: Int, val maxAttempts: Int) : Progress

        /** The per-minute quota ran out; counting down to when Google said it frees up. */
        data class WaitingForQuota(val secondsLeft: Int) : Progress
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
        /** Names of images in [AiImageStore] attached to this message. */
        imageNames: List<String> = emptyList(),
        onProgress: (Progress) -> Unit = {},
        onDelta: (String) -> Unit = {},
    ): TurnResult {
        // A snapshot, not an index: the turn also trims old entries off the front, which would
        // leave any position we remembered pointing at the wrong element.
        val before = history.toList()
        val usage = TurnUsage()
        return try {
            runTurn(text, imageNames, usage, onProgress, onDelta).also { result ->
                if (result is TurnResult.Failure) history.restore(before)
            }
        } catch (e: CancellationException) {
            // The user hit stop. Same rule: nothing half-written survives.
            history.restore(before)
            throw e
        } finally {
            if (usage.requests > 0) Log.d(USAGE_TAG, usage.summaryLine())
        }
    }

    private fun MutableList<GeminiContent>.restore(snapshot: List<GeminiContent>) {
        clear()
        addAll(snapshot)
    }

    private suspend fun runTurn(
        text: String,
        imageNames: List<String>,
        turnUsage: TurnUsage,
        reportProgress: (Progress) -> Unit,
        onDelta: (String) -> Unit,
    ): TurnResult {
        // Remembered so a retry's "server busy" line can be replaced by whatever the loop was
        // doing before it, once the retried request gets through.
        var shownProgress: Progress = Progress.Contacting
        val onProgress: (Progress) -> Unit = {
            shownProgress = it
            reportProgress(it)
        }
        val send = modelStream()
            ?: return TurnResult.Failure(GeminiException.InvalidKey("API key not set"))

        // Text first, then the images: the order Google's image-understanding guide uses. The
        // images go in as file references — see [resolveImages].
        history += GeminiContent(
            role = "user",
            parts = buildList {
                if (text.isNotBlank()) add(GeminiPart(text = text))
                imageNames.forEach {
                    add(GeminiPart(inlineData = GeminiBlob(mimeType = AiImageStore.MIME_TYPE, data = it)))
                }
            },
        )
        trimHistory()

        var repairAttempts = 0
        var pendingFlow: FlowJson? = null
        var pendingSummary: String? = null

        repeat(MAX_STEPS) { step ->
            if (step == 0) onProgress(Progress.Contacting)
            val request = GenerateContentRequest(
                contents = resolveImages(history.toList()),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = AiCatalog.systemInstruction(context))),
                ),
                tools = AiTools.tools(),
            )
            val response = runCatching {
                streamStepWithRetry(send, request, onDelta) { waiting ->
                    reportProgress(waiting ?: shownProgress)
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "turn failed: ${e.javaClass.simpleName}: ${e.message}")
                return TurnResult.Failure(
                    e as? GeminiException ?: GeminiException.Unknown(e.message ?: "unexpected error"),
                )
            }

            logStepUsage(step, request, response, turnUsage)

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

            var rejected = false
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
                        val installed = installedApps.launchablePackages()
                        val preamble = history.first().parts.mapNotNull { it.text }.joinToString("\n")
                        val draft = FlowDraftMapper.fromArgs(
                            call.args,
                            context,
                            knownGlobals = globalVariableRepository.currentValues().keys,
                            // A flow being edited may point at an app since uninstalled. That's
                            // the user's to fix; rejecting it would fail every edit of that flow.
                            isInstalled = { it in installed || it in preamble },
                        )
                        if (draft.isValid) {
                            Log.d(TAG, "create_flow validated: \"${draft.flow?.name}\"")
                            pendingFlow = draft.flow
                            pendingSummary = (call.args["summary"] as? JsonPrimitive)?.contentOrNull
                            functionResponse(call.name, buildJsonObject { put("status", "ok") })
                        } else {
                            rejected = true
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

            // The model already wrote its summary into the call. Asking for it again would
            // replay the whole prompt for one sentence: measured on a real conversation, that
            // last step alone was 42% of the tokens. A step that also searched is left to run
            // on, because the model asked for an answer it hasn't seen yet.
            val flow = pendingFlow
            if (flow != null && !rejected && functionCalls.all { it.name == AiTools.CREATE_FLOW }) {
                val summary = pendingSummary?.trim().orEmpty().ifEmpty { flow.description }.ifEmpty { flow.name }
                // Closes the exchange, or the next user message would follow a functionResponse.
                history += GeminiContent(role = "model", parts = listOf(GeminiPart(text = summary)))
                return TurnResult.FlowReady(flow, summary)
            }
        }

        return TurnResult.Failure(GeminiException.Unknown("conversation step limit reached"))
    }

    /** How to reach the service the user selected, or null when it isn't set up. */
    private fun modelStream(): ModelStream? = when (AiPrefs.getProvider(context)) {
        AiProvider.GEMINI -> AiPrefs.getApiKey(context)?.let { key ->
            val model = AiPrefs.getModel(context)
            ModelStream { client.streamGenerateContent(key, model, it) }
        }
        AiProvider.CUSTOM -> AiPrefs.getCustomEndpoint(context)?.let { endpoint ->
            ModelStream { compatibleClient.streamGenerateContent(endpoint, it) }
        }
    }

    /** Numbers and tool names only: this runs in release builds, so no user text goes in it. */
    private fun logStepUsage(
        step: Int,
        request: GenerateContentRequest,
        response: GenerateContentResponse,
        turnUsage: TurnUsage,
    ) {
        val usage = response.usageMetadata
        if (usage == null) {
            Log.d(USAGE_TAG, "step=${step + 1} no usage reported")
            return
        }
        turnUsage.add(usage)
        Log.d(
            USAGE_TAG,
            usage.stepLine(
                step = step + 1,
                contents = request.contents.size,
                images = request.contents.sumOf { c -> c.parts.count { it.inlineData != null } },
                signatureChars = request.contents.sumOf { c ->
                    c.parts.sumOf { it.thoughtSignature?.length ?: 0 }
                },
                outcome = response.parts.mapNotNull { it.functionCall?.name }
                    .ifEmpty { listOf("text") }
                    .joinToString(","),
            ),
        )
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
     * The history as the API wants it: every image reference swapped for the image's bytes.
     *
     * The history itself only holds [AiImageStore] file names, so it can be persisted like any
     * other text. Images are filled in newest first against [MAX_INLINE_IMAGE_BYTES] — Gemini
     * caps a request at 20 MB and every turn replays every image — and an image that is over
     * budget, or whose file is gone, becomes a note saying so instead of a dangling reference.
     */
    private suspend fun resolveImages(contents: List<GeminiContent>): List<GeminiContent> {
        if (contents.none { c -> c.parts.any { it.inlineData != null } }) return contents
        var budget = MAX_INLINE_IMAGE_BYTES
        return contents.asReversed().map { content ->
            if (content.parts.none { it.inlineData != null }) return@map content
            content.copy(
                parts = content.parts.map { part ->
                    val blob = part.inlineData ?: return@map part
                    val data = images.base64(blob.data)?.takeIf { it.length <= budget }
                    if (data == null) {
                        GeminiPart(text = IMAGE_UNAVAILABLE_NOTE)
                    } else {
                        budget -= data.length
                        part.copy(inlineData = blob.copy(data = data))
                    }
                },
            )
        }.asReversed()
    }

    /**
     * [streamStep], sent again when the failure is one that waiting fixes:
     *
     * - Gemini is overloaded (503 "high demand", or a 500 / 502). Google's error reference says
     *   to retry these with exponential backoff, and the official SDKs do it by default.
     * - A per-minute quota ran out (429). On the free tier that is a handful of requests a
     *   minute, and one turn that searches apps and then builds a flow is already two — so
     *   a brisk conversation used to fail outright within a few messages. Google says in the
     *   error how long to wait; the turn now waits that long and carries on. A daily quota, or
     *   a model the key's tier does not include, is reported straight away: no wait inside a
     *   conversation fixes those.
     *
     * Only a request that failed before any text reached the screen is retried. Retrying after
     * that would show the user the start of an answer twice; both errors are status codes, so
     * in practice they always arrive before any chunk. The retry sits below the tool-calling
     * loop, so a tool the model already called is never run twice.
     *
     * [onWait] gets what to show while waiting, then null once a retried request is through.
     */
    private suspend fun streamStepWithRetry(
        send: ModelStream,
        request: GenerateContentRequest,
        onDelta: (String) -> Unit,
        onWait: (Progress?) -> Unit,
    ): GenerateContentResponse {
        var busyRetries = 0
        var quotaRetries = 0
        while (true) {
            val waited = busyRetries + quotaRetries > 0
            var shownText = false
            try {
                return streamStep(send, request) { text ->
                    if (!shownText && text.isNotEmpty()) {
                        shownText = true
                        if (waited) onWait(null)
                    }
                    onDelta(text)
                }.also { if (waited && !shownText) onWait(null) }
            } catch (e: GeminiException.Unavailable) {
                if (shownText || busyRetries >= MAX_UNAVAILABLE_RETRIES) throw e
                busyRetries++
                val wait = backoffMillis(busyRetries)
                Log.w(TAG, "Gemini unavailable (${e.message}); retry $busyRetries/$MAX_UNAVAILABLE_RETRIES in ${wait}ms")
                onWait(Progress.Retrying(busyRetries, MAX_UNAVAILABLE_RETRIES))
                delay(wait)
            } catch (e: GeminiException.RateLimited) {
                val wait = quotaWaitMillis(e, quotaRetries + 1)
                if (shownText || wait == null || quotaRetries >= MAX_RATE_LIMIT_RETRIES) throw e
                quotaRetries++
                Log.w(TAG, "per-minute quota spent; retry $quotaRetries/$MAX_RATE_LIMIT_RETRIES in ${wait}ms")
                countDown(wait) { onWait(Progress.WaitingForQuota(it)) }
            }
        }
    }

    /** 2 s, 4 s, 8 s, each plus up to a second of jitter so clients that failed together don't retry together. */
    private fun backoffMillis(attempt: Int): Long =
        INITIAL_BACKOFF_MS * (1L shl (attempt - 1)) + Random.nextLong(MAX_JITTER_MS)

    /**
     * How long to wait out a 429, or null when waiting won't help. Google's own `retryDelay`
     * wins when there is one; past a minute it isn't a per-minute limit, whatever the quota id
     * says, and a user won't sit through it anyway.
     */
    private fun quotaWaitMillis(e: GeminiException.RateLimited, attempt: Int): Long? {
        if (e.daily || e.noQuota) return null
        val wait = e.retryAfterMillis?.plus(QUOTA_SLACK_MS)
            ?: (RATE_LIMIT_FALLBACK_MS * (1L shl (attempt - 1)))
        return wait.takeIf { it <= MAX_QUOTA_WAIT_MS }
    }

    /** Waits [millis], telling [onSecond] the whole seconds left as they tick down. */
    private suspend fun countDown(millis: Long, onSecond: (Int) -> Unit) {
        var left = millis
        while (left > 0) {
            onSecond(((left + 999) / 1000).toInt())
            val step = minOf(left, 1000L)
            delay(step)
            left -= step
        }
    }

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
        send: ModelStream,
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
        send(request).collect { chunk ->
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
                                // Omitted rather than sent as null/empty: most apps supply none of
                                // these, and blank keys would be prompt noise on every entry.
                                app.category?.let { put("category", it) }
                                if (app.roles.isNotEmpty()) {
                                    putJsonArray("roles") { app.roles.forEach { add(it) } }
                                }
                                app.description?.let { put("description", it) }
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

    private fun interface ModelStream {
        operator fun invoke(request: GenerateContentRequest): Flow<GenerateContentResponse>
    }

    private companion object {
        const val TAG = "AiChatOrchestrator"

        /** Its own tag, so `adb logcat -s AiUsage` shows the token breakdown and nothing else. */
        const val USAGE_TAG = "AiUsage"

        /** Upper bound on model round-trips per user turn (tool answers + repairs). */
        const val MAX_STEPS = 8

        /** Three retries at 2 / 4 / 8 s: about 15 s of waiting before a busy model is reported. */
        const val MAX_UNAVAILABLE_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_JITTER_MS = 1_000L
        const val MAX_REPAIR_ATTEMPTS = 3

        /**
         * 429 on a per-minute quota: up to three waits. Without a `retryDelay` from Google,
         * 10 / 20 / 40 s — a per-minute window is over within that.
         */
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val RATE_LIMIT_FALLBACK_MS = 10_000L
        const val MAX_QUOTA_WAIT_MS = 65_000L

        /** Google's delay is to the edge of the window; arriving exactly on it can still bounce. */
        const val QUOTA_SLACK_MS = 1_000L

        /**
         * Base64 characters of image data one request may carry. The API's limit is 20 MB for
         * the whole request; this leaves room for the text, the tools and the system prompt.
         */
        const val MAX_INLINE_IMAGE_BYTES = 12 * 1024 * 1024

        const val IMAGE_UNAVAILABLE_NOTE =
            "[The user attached an image here earlier; it is no longer included in the conversation.]"

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
