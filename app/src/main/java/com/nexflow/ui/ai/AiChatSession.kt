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

import android.content.Context
import com.nexflow.R
import com.nexflow.ai.AiChatOrchestrator
import com.nexflow.ai.FlowContextFormatter
import com.nexflow.ai.GeminiException
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.data.toFlowJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-scoped conversation state. The ViewModel dies with the nav back stack entry, so
 * everything the user would miss after "back and re-enter" lives here instead: messages,
 * the unsent input draft, the in-flight request (own scope — leaving the screen doesn't
 * cancel a running turn), and which flow this conversation already saved.
 */
@Singleton
class AiChatSession @Inject constructor(
    private val orchestrator: AiChatOrchestrator,
    private val flowRepository: FlowRepository,
    private val store: AiConversationStore,
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val draft = MutableStateFlow("")
    val isThinking = MutableStateFlow(false)
    val thinkingStep = MutableStateFlow<AiChatOrchestrator.Progress?>(null)

    /**
     * The reply currently forming, or null when no turn is running. Empty string means the turn
     * has started but the model hasn't said anything yet — that's when the thinking indicator
     * shows instead.
     */
    val streamingText = MutableStateFlow<String?>(null)

    /** The existing flow this conversation is scoped to, or null for a free-form chat. */
    val editingFlow = MutableStateFlow<EditingFlowInfo?>(null)

    /** Flow id already saved from this conversation; later saves update it in place. */
    var savedFlowId: String? = null

    /** Tokens this conversation has spent, straight from the orchestrator's running total. */
    val tokensUsed: Int get() = orchestrator.tokensUsed

    /**
     * The flow as it stands on disk, for diffing against what the model proposes. Updated after
     * a save so the next proposal is compared with what the user actually has now.
     */
    val baselineFlow = MutableStateFlow<FlowJson?>(null)

    /** Flow id this conversation was opened to edit — guards re-seeding on re-entry. */
    private var editingFlowId: String? = null

    /** The in-flight turn, so the user can stop it. */
    private var turnJob: Job? = null

    /** The free chat is read back from the database once per process, not on every entry. */
    private var freeChatRestored = false

    /**
     * Opens (or resumes) a conversation about an existing flow. Re-entering the same flow keeps
     * the transcript; opening a different one starts over, because the model's history would
     * otherwise still describe the previous flow.
     */
    fun startFlowEdit(flowId: String) {
        if (editingFlowId == flowId || isThinking.value) return
        // Claimed before the suspending load so a second entry can't seed the chat twice.
        editingFlowId = flowId
        scope.launch {
            val flow = flowRepository.getById(flowId)
            if (flow == null) {
                editingFlowId = null
                return@launch
            }
            // Revisions land on this flow instead of creating a near-duplicate.
            savedFlowId = flowId
            editingFlow.value = EditingFlowInfo(
                name = flow.name,
                description = flow.description,
                icon = flow.icon,
                iconColor = flow.iconColor,
            )
            draft.value = ""
            baselineFlow.value = flow.toFlowJson()

            // A conversation the user left days ago picks up where it stopped. Replaying the
            // intro over an existing transcript would read as the assistant forgetting.
            val stored = store.load(flowId)
            if (stored != null) {
                orchestrator.restoreHistory(stored.history, stored.tokensUsed)
                savedFlowId = stored.savedFlowId ?: flowId
                messages.value = stored.messages
                return@launch
            }

            orchestrator.startFlowEditing(FlowContextFormatter.format(flow))
            messages.value = listOf(
                ChatMessage.AssistantText(context.getString(R.string.ai_editing_flow_intro, flow.name)),
            )
        }
    }

    /** Restores the free-form chat, once, when the app comes back after being killed. */
    fun restoreFreeChat() {
        if (freeChatRestored || editingFlowId != null || messages.value.isNotEmpty()) return
        freeChatRestored = true
        scope.launch {
            store.purgeOrphans()
            val stored = store.load(null) ?: return@launch
            // Entering a flow edit while this was loading wins — don't stomp on it.
            if (editingFlowId != null || messages.value.isNotEmpty()) return@launch
            orchestrator.restoreHistory(stored.history, stored.tokensUsed)
            savedFlowId = stored.savedFlowId
            messages.value = stored.messages
        }
    }

    /** Writes the conversation as it now stands. Cheap enough to do after every change. */
    private fun persist() {
        val flowId = editingFlowId
        val snapshot = StoredConversation(
            messages = messages.value,
            history = orchestrator.exportHistory(),
            savedFlowId = savedFlowId,
            tokensUsed = orchestrator.tokensUsed,
        )
        scope.launch { store.save(flowId, snapshot) }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isThinking.value) return
        messages.value += ChatMessage.UserText(trimmed)
        draft.value = ""
        runTurn(trimmed)
    }

    /**
     * Re-sends what a failed turn was carrying. The user's own message is still sitting in the
     * transcript above the error, so only the error bubble goes — a retry must not read as the
     * user asking the same thing twice.
     */
    fun retry(errorMessageId: String) {
        if (isThinking.value) return
        val error = messages.value.firstOrNull { it.id == errorMessageId } as? ChatMessage.Error ?: return
        if (error.retryText.isBlank()) return
        messages.value = messages.value.filterNot { it.id == errorMessageId }
        runTurn(error.retryText)
    }

    /** The user hit stop mid-turn. [AiChatOrchestrator] rewinds its own history on cancellation. */
    fun stop() {
        turnJob?.cancel()
    }

    private fun runTurn(text: String) {
        isThinking.value = true
        streamingText.value = ""
        turnJob = scope.launch {
            try {
                // Off the main thread. A turn serialises the whole conversation into a request
                // body, parses every streamed chunk back out, validates proposed flows, and in
                // debug builds logs both bodies in 3 KB slices — none of which belongs on the
                // thread drawing the frames. The state flows it writes are thread-safe.
                val result = withContext(Dispatchers.Default) {
                    orchestrator.sendUserMessage(
                        text,
                        onProgress = { thinkingStep.value = it },
                        onDelta = { streamingText.value = it },
                    )
                }
                messages.value += when (result) {
                    is AiChatOrchestrator.TurnResult.Assistant ->
                        listOf(ChatMessage.AssistantText(result.text))

                    is AiChatOrchestrator.TurnResult.FlowReady -> buildList {
                        if (result.summaryText.isNotBlank()) add(ChatMessage.AssistantText(result.summaryText))
                        add(ChatMessage.FlowPreview(result.flow))
                    }

                    // The text that failed rides along, so the error bubble can offer a retry
                    // instead of making the user type it again.
                    is AiChatOrchestrator.TurnResult.Failure ->
                        listOf(ChatMessage.Error(result.error.toUserMessage(), retryText = text))
                }
            } catch (e: CancellationException) {
                // Keep what the model had already said. The user watched it arrive and may
                // have stopped precisely because they had read enough.
                val partial = streamingText.value.orEmpty().trim()
                if (partial.isNotEmpty()) {
                    messages.value += ChatMessage.AssistantText(partial, stopped = true)
                }
                throw e
            } finally {
                // Also runs on cancellation, which is the only way the thinking state clears
                // when the user stops a turn.
                isThinking.value = false
                thinkingStep.value = null
                streamingText.value = null
                // Written at the end of every turn, however it ended: this is the point where
                // the transcript and the model history are consistent with each other again.
                persist()
            }
        }
    }

    fun markSaved(messageId: String, flowId: String) {
        savedFlowId = flowId
        messages.value = messages.value.map {
            if (it.id == messageId && it is ChatMessage.FlowPreview) it.copy(saved = true) else it
        }
        // What's on disk just changed, so the next proposal must be diffed against this one.
        (messages.value.firstOrNull { it.id == messageId } as? ChatMessage.FlowPreview)
            ?.let { baselineFlow.value = it.flow }
        persist()
    }

    /**
     * Start over: clears the transcript, the draft, and the model-side history.
     *
     * A conversation about a flow starts over *about that same flow* — the header says which
     * flow the user is editing, so silently turning it into a free-form chat would make the
     * button a trapdoor out of the thing they came here to change.
     */
    fun newChat() {
        if (isThinking.value) return
        val flowId = editingFlowId
        messages.value = emptyList()
        draft.value = ""
        savedFlowId = null
        editingFlowId = null
        orchestrator.reset()
        if (flowId != null) {
            // Clearing and re-seeding must be ordered: startFlowEdit reads the stored
            // conversation, and if that read won the race it would restore the very transcript
            // the user just asked to throw away.
            scope.launch {
                store.clear(flowId)
                startFlowEdit(flowId)
            }
        } else {
            // "Start over" has to reach the database too, or the old transcript comes back
            // the next time the app is opened.
            scope.launch { store.clear(null) }
            editingFlow.value = null
            baselineFlow.value = null
        }
    }

    private fun GeminiException.toUserMessage(): String = when (this) {
        is GeminiException.InvalidKey -> context.getString(R.string.ai_error_invalid_key)
        is GeminiException.RateLimited -> context.getString(R.string.ai_error_rate_limited)
        is GeminiException.ModelNotFound -> context.getString(R.string.ai_error_model_not_found)
        is GeminiException.Network -> context.getString(R.string.ai_error_network)
        is GeminiException.Unknown -> context.getString(R.string.ai_error_generic, message ?: "")
    }
}

/** What the chat header shows about the flow being edited — the flow's own identity. */
data class EditingFlowInfo(
    val name: String,
    val description: String,
    val icon: String?,
    val iconColor: String?,
)
