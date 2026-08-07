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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.ai.AiChatOrchestrator
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.data.toDomain
import com.nexflow.prefs.AiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Serializable so a conversation can be written to the database and read back whole. */
@Serializable
sealed interface ChatMessage {
    val id: String
    val timestamp: Long

    @Serializable
    @SerialName("user")
    data class UserText(
        val text: String,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

    @Serializable
    @SerialName("assistant")
    data class AssistantText(
        val text: String,
        /** True when the user stopped the turn mid-answer, so the bubble can say it's partial. */
        val stopped: Boolean = false,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

    @Serializable
    @SerialName("error")
    data class Error(
        val text: String,
        /** The user message this turn was carrying, so the bubble can offer a one-tap retry. */
        val retryText: String = "",
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

    @Serializable
    @SerialName("flow_preview")
    data class FlowPreview(
        val flow: FlowJson,
        val saved: Boolean = false,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val thinkingStep: AiChatOrchestrator.Progress? = null,
    /** The reply currently streaming in, or null when nothing is in flight. */
    val streamingText: String? = null,
    /** Tokens this conversation has spent, shown in the header. */
    val tokensUsed: Int = 0,
    val apiKeyMissing: Boolean = false,
    /** True once this conversation saved a flow — later saves update it instead. */
    val hasSavedFlow: Boolean = false,
    /** Non-null while the conversation is scoped to an existing flow the user opened to edit. */
    val editingFlow: EditingFlowInfo? = null,
    /** The saved flow a proposal is compared against; null for a flow being built from nothing. */
    val baselineFlow: FlowJson? = null,
)

/** Thin screen adapter over [AiChatSession]; the conversation itself outlives this ViewModel. */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: AiChatSession,
    private val repository: FlowRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val apiKeyMissing = MutableStateFlow(AiPrefs.getApiKey(context) == null)

    init {
        // Entered from a flow's "edit with Gemini" button: scope the conversation to that flow.
        val flowId = savedStateHandle.get<String>(ARG_FLOW_ID)?.takeIf { it.isNotBlank() }
        if (flowId != null) {
            session.startFlowEdit(flowId)
        } else {
            // Plain chat entry: bring back whatever was going on before the app was killed.
            session.restoreFreeChat()
        }
    }

    /** Everything about the turn in flight, bundled so [uiState] stays within combine's arity. */
    private data class TurnState(
        val isThinking: Boolean,
        val step: AiChatOrchestrator.Progress?,
        val streamingText: String?,
    )

    private val turnState = combine(
        session.isThinking,
        session.thinkingStep,
        session.streamingText,
    ) { thinking, step, streaming -> TurnState(thinking, step, streaming) }

    val uiState: StateFlow<ChatUiState> = combine(
        session.messages,
        turnState,
        apiKeyMissing,
        session.editingFlow,
        session.baselineFlow,
    ) { messages, turn, keyMissing, editing, baseline ->
        ChatUiState(
            messages = messages,
            isThinking = turn.isThinking,
            thinkingStep = turn.step,
            streamingText = turn.streamingText,
            // Read at emission time like hasSavedFlow: every turn ends with a state change
            // that re-emits, so the count is never stale by more than an in-flight request.
            tokensUsed = session.tokensUsed,
            apiKeyMissing = keyMissing,
            hasSavedFlow = session.savedFlowId != null,
            editingFlow = editing,
            baselineFlow = baseline,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState(apiKeyMissing = apiKeyMissing.value))

    /** Unsent input, preserved across navigation. */
    val draft: StateFlow<String> = session.draft

    private val _navigateToFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToFlow: SharedFlow<String> = _navigateToFlow.asSharedFlow()

    /** Saved while editing: the chat stays put and offers a way over to the flow. */
    private val _flowSaved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val flowSaved: SharedFlow<String> = _flowSaved.asSharedFlow()

    /** Called on resume — the user may have just added the key in Settings. */
    fun refreshApiKeyState() {
        apiKeyMissing.value = AiPrefs.getApiKey(context) == null
    }

    fun updateDraft(text: String) {
        session.draft.value = text
    }

    fun sendMessage(text: String) = session.send(text)

    /** Stop the running turn. Nothing half-generated is kept in the model's history. */
    fun stop() = session.stop()

    fun retry(errorMessageId: String) = session.retry(errorMessageId)

    fun newChat() = session.newChat()

    fun saveFlow(message: ChatMessage.FlowPreview) {
        if (message.saved) return
        val wasEditing = uiState.value.editingFlow != null
        viewModelScope.launch {
            // Revisions from the same conversation keep the first save's id, so "small
            // improvements" update the flow in place instead of piling up near-duplicates.
            val flowId = session.savedFlowId ?: message.flow.id
            // Mirrors the import path: AI-generated flows land disabled; enabling routes
            // through the existing missing-permission wizard.
            repository.save(message.flow.copy(id = flowId).toDomain(forceDisabled = true))
            session.markSaved(message.id, flowId)
            if (wasEditing) {
                // Editing is iterative — "change this, look, change that again". Navigating away
                // on every save would throw the user out of the loop they're in. Offer the trip
                // instead of making it.
                _flowSaved.tryEmit(flowId)
            } else {
                // A flow built from nothing is finished business: go look at what was made.
                _navigateToFlow.tryEmit(flowId)
            }
        }
    }

    companion object {
        /** Optional nav argument: the existing flow this chat should edit. */
        const val ARG_FLOW_ID = "flowId"
    }
}
