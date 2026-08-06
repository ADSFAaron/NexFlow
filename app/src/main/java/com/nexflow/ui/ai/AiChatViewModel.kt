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

sealed interface ChatMessage {
    val id: String
    val timestamp: Long

    data class UserText(
        val text: String,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

    data class AssistantText(
        val text: String,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

    data class Error(
        val text: String,
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage

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
    val apiKeyMissing: Boolean = false,
    /** True once this conversation saved a flow — later saves update it instead. */
    val hasSavedFlow: Boolean = false,
    /** Non-null while the conversation is scoped to an existing flow the user opened to edit. */
    val editingFlow: EditingFlowInfo? = null,
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
        savedStateHandle.get<String>(ARG_FLOW_ID)?.takeIf { it.isNotBlank() }
            ?.let { session.startFlowEdit(it) }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        session.messages,
        session.isThinking,
        session.thinkingStep,
        apiKeyMissing,
        session.editingFlow,
    ) { messages, thinking, step, keyMissing, editing ->
        ChatUiState(
            messages = messages,
            isThinking = thinking,
            thinkingStep = step,
            apiKeyMissing = keyMissing,
            hasSavedFlow = session.savedFlowId != null,
            editingFlow = editing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState(apiKeyMissing = apiKeyMissing.value))

    /** Unsent input, preserved across navigation. */
    val draft: StateFlow<String> = session.draft

    private val _navigateToFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToFlow: SharedFlow<String> = _navigateToFlow.asSharedFlow()

    /** Called on resume — the user may have just added the key in Settings. */
    fun refreshApiKeyState() {
        apiKeyMissing.value = AiPrefs.getApiKey(context) == null
    }

    fun updateDraft(text: String) {
        session.draft.value = text
    }

    fun sendMessage(text: String) = session.send(text)

    fun newChat() = session.newChat()

    fun saveFlow(message: ChatMessage.FlowPreview) {
        if (message.saved) return
        viewModelScope.launch {
            // Revisions from the same conversation keep the first save's id, so "small
            // improvements" update the flow in place instead of piling up near-duplicates.
            val flowId = session.savedFlowId ?: message.flow.id
            // Mirrors the import path: AI-generated flows land disabled; enabling routes
            // through the existing missing-permission wizard.
            repository.save(message.flow.copy(id = flowId).toDomain(forceDisabled = true))
            session.markSaved(message.id, flowId)
            _navigateToFlow.tryEmit(flowId)
        }
    }

    companion object {
        /** Optional nav argument: the existing flow this chat should edit. */
        const val ARG_FLOW_ID = "flowId"
    }
}
