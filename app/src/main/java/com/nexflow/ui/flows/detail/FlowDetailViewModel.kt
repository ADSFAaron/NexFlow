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
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import com.nexflow.service.FlowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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

    /** Adds a new variable ([originalName] = null) or replaces the one named [originalName]. */
    fun saveVariable(originalName: String?, variable: Variable) = edit {
        val others = variables.filter { it.name != originalName }
        copy(variables = others.filter { it.name != variable.name } + variable)
    }

    fun removeVariable(name: String) = edit {
        copy(variables = variables.filter { it.name != name })
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(flowId, enabled) }
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var runJob: kotlinx.coroutines.Job? = null

    fun runNow() {
        if (_isRunning.value) return
        runJob = viewModelScope.launch {
            _isRunning.value = true
            yield()
            try {
                flowEngine.runNow(flowId)
            } finally {
                _isRunning.value = false
                runJob = null
            }
        }
    }

    fun cancelRun() {
        runJob?.cancel()
        runJob = null
    }

    fun reorderActions(fromIndex: Int, toIndex: Int) {
        val current = flow.value ?: return
        val sorted = current.actions.sortedBy { it.order }.toMutableList()
        sorted.add(toIndex, sorted.removeAt(fromIndex))
        val reordered = sorted.mapIndexed { i, a -> a.copy(order = i) }
        viewModelScope.launch {
            repository.save(current.copy(actions = reordered, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateDetails(name: String, description: String, icon: String?, iconColor: String?) = edit {
        copy(name = name, description = description, icon = icon, iconColor = iconColor)
    }

    fun setIcon(icon: String?, iconColor: String?) = edit { copy(icon = icon, iconColor = iconColor) }

    fun exportAsJson(): String? {
        val f = flow.value ?: return null
        val now = java.time.Instant.ofEpochMilli(f.createdAt).toString()
        val json = Json { prettyPrint = true }
        val flowJson = FlowJson(
            schemaVersion = f.schemaVersion,
            id = f.id,
            name = f.name,
            description = f.description,
            author = f.author,
            icon = f.icon,
            iconColor = f.iconColor,
            tags = f.tags,
            enabled = f.enabled,
            createdAt = now,
            updatedAt = java.time.Instant.ofEpochMilli(f.updatedAt).toString(),
            triggers = f.triggers.map { t ->
                TriggerJson(t.id, t.type.name, JsonObject(t.config.mapValues { JsonPrimitive(it.value) }))
            },
            triggerLogic = f.triggerLogic.name,
            conditions = f.conditions.map { c ->
                ConditionJson(c.id, c.type, JsonObject(c.config.mapValues { JsonPrimitive(it.value) }), c.negate)
            },
            actions = f.actions.map { a ->
                ActionJson(a.id, a.type.name, JsonObject(a.config.mapValues { JsonPrimitive(it.value) }), a.order, a.enabled)
            },
            variables = f.variables.map { v ->
                VariableJson(v.name, v.type.name, JsonPrimitive(v.defaultValue))
            },
        )
        return json.encodeToString(flowJson)
    }

    private fun edit(transform: Flow.() -> Flow) {
        val current = flow.value ?: return
        val updated = current.transform().copy(updatedAt = System.currentTimeMillis())
        viewModelScope.launch { repository.save(updated) }
    }
}
