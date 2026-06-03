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
package com.nexflow.ui.flows.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.service.FlowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlowRepository,
    private val flowEngine: FlowEngine,
) : ViewModel() {

    private val flowId: String = checkNotNull(savedStateHandle["flowId"])

    val flow: StateFlow<Flow?> = repository.observeAll()
        .map { flows -> flows.find { it.id == flowId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTrigger(trigger: Trigger) = edit { copy(triggers = triggers + trigger) }

    fun updateTrigger(trigger: Trigger) = edit {
        copy(triggers = triggers.map { if (it.id == trigger.id) trigger else it })
    }

    fun removeTrigger(id: String) = edit { copy(triggers = triggers.filter { it.id != id }) }

    fun setTriggerLogic(logic: TriggerLogic) = edit { copy(triggerLogic = logic) }

    fun addAction(action: Action) = edit {
        copy(actions = actions + action.copy(order = actions.size))
    }

    fun updateAction(action: Action) = edit {
        copy(actions = actions.map { if (it.id == action.id) action else it })
    }

    fun removeAction(id: String) = edit {
        copy(
            actions = actions.filter { it.id != id }
                .mapIndexed { i, a -> a.copy(order = i) },
        )
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(flowId, enabled) }
    }

    fun runNow() {
        viewModelScope.launch { flowEngine.runNow(flowId) }
    }

    fun rename(name: String, description: String) = edit { copy(name = name, description = description) }

    private fun edit(transform: Flow.() -> Flow) {
        val current = flow.value ?: return
        val updated = current.transform().copy(updatedAt = System.currentTimeMillis())
        viewModelScope.launch { repository.save(updated) }
    }
}
