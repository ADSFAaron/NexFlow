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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val draft = MutableStateFlow("")
    val isThinking = MutableStateFlow(false)
    val thinkingStep = MutableStateFlow<AiChatOrchestrator.Progress?>(null)

    /** Name of the existing flow this conversation is editing, shown under the title. */
    val editingFlowName = MutableStateFlow<String?>(null)

    /** Flow id already saved from this conversation; later saves update it in place. */
    var savedFlowId: String? = null

    /** Flow id this conversation was opened to edit — guards re-seeding on re-entry. */
    private var editingFlowId: String? = null

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
            editingFlowName.value = flow.name
            draft.value = ""
            orchestrator.startFlowEditing(FlowContextFormatter.format(flow))
            messages.value = listOf(
                ChatMessage.AssistantText(context.getString(R.string.ai_editing_flow_intro, flow.name)),
            )
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isThinking.value) return
        messages.value += ChatMessage.UserText(trimmed)
        draft.value = ""
        isThinking.value = true
        scope.launch {
            val result = orchestrator.sendUserMessage(trimmed) { thinkingStep.value = it }
            messages.value += when (result) {
                is AiChatOrchestrator.TurnResult.Assistant ->
                    listOf(ChatMessage.AssistantText(result.text))

                is AiChatOrchestrator.TurnResult.FlowReady -> buildList {
                    if (result.summaryText.isNotBlank()) add(ChatMessage.AssistantText(result.summaryText))
                    add(ChatMessage.FlowPreview(result.flow))
                }

                is AiChatOrchestrator.TurnResult.Failure ->
                    listOf(ChatMessage.Error(result.error.toUserMessage()))
            }
            isThinking.value = false
            thinkingStep.value = null
        }
    }

    fun markSaved(messageId: String, flowId: String) {
        savedFlowId = flowId
        messages.value = messages.value.map {
            if (it.id == messageId && it is ChatMessage.FlowPreview) it.copy(saved = true) else it
        }
    }

    /** Start over: clears the transcript, the draft, and the model-side history. */
    fun newChat() {
        if (isThinking.value) return
        messages.value = emptyList()
        draft.value = ""
        savedFlowId = null
        editingFlowId = null
        editingFlowName.value = null
        orchestrator.reset()
    }

    private fun GeminiException.toUserMessage(): String = when (this) {
        is GeminiException.InvalidKey -> context.getString(R.string.ai_error_invalid_key)
        is GeminiException.RateLimited -> context.getString(R.string.ai_error_rate_limited)
        is GeminiException.ModelNotFound -> context.getString(R.string.ai_error_model_not_found)
        is GeminiException.Network -> context.getString(R.string.ai_error_network)
        is GeminiException.Unknown -> context.getString(R.string.ai_error_generic, message ?: "")
    }
}
