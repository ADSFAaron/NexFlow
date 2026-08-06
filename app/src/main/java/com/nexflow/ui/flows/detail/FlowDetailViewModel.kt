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
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.GlobalVariable
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.permissions.FlowPermissionChecker
import com.nexflow.permissions.PermissionReminder
import com.nexflow.permissions.PermissionSetup
import com.nexflow.permissions.PermissionSetupManager
import com.nexflow.permissions.PermissionSetupResult
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.GlobalVariableJson
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import com.nexflow.service.FlowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val globalVariableRepository: GlobalVariableRepository,
    private val flowEngine: FlowEngine,
    private val permissionChecker: FlowPermissionChecker,
) : ViewModel() {

    private val flowId: String = checkNotNull(savedStateHandle["flowId"])

    /**
     * Trigger/condition/action whose settings should open on arrival, set when the import review
     * sends the user here to fix something. Consumed once so it doesn't reopen on every rotation.
     */
    var focusItemId: String? = savedStateHandle["focus"]
        private set

    fun consumeFocusItem() {
        focusItemId = null
    }

    val flow: StateFlow<Flow?> = repository.observeAll()
        .map { flows -> flows.find { it.id == flowId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val globalVariables: StateFlow<List<GlobalVariable>> = globalVariableRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Global variable reference tokens (e.g. "g:counter") offered by the insert-variable menu. */
    val globalVariableRefs: StateFlow<List<String>> = globalVariables
        .map { list -> list.map { "${FlowInterpreter.GLOBAL_PREFIX}${it.name}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Same permission gate as the Flows list: enabling a flow that is missing permissions
    // must be blocked and walked through the guided setup, never written silently.
    private val _permissionReminder = MutableSharedFlow<PermissionReminder>(extraBufferCapacity = 1)
    val permissionReminder: SharedFlow<PermissionReminder> = _permissionReminder.asSharedFlow()

    private val setupManager = PermissionSetupManager(repository, permissionChecker)
    val permissionSetup: StateFlow<PermissionSetup?> = setupManager.setup
    val setupComplete: SharedFlow<PermissionSetupResult> = setupManager.complete

    fun beginPermissionSetup(id: String, autoEnable: Boolean) {
        viewModelScope.launch { setupManager.begin(id, autoEnable) }
    }

    fun advancePermissionSetup() {
        viewModelScope.launch { setupManager.advance() }
    }

    fun markPermissionAttempted(permissions: List<String>) = setupManager.markAttempted(permissions)

    fun skipCurrentPermission() {
        viewModelScope.launch { setupManager.skip() }
    }

    fun cancelPermissionSetup() = setupManager.cancel()

    fun addTrigger(trigger: Trigger) = edit { copy(triggers = triggers + trigger) }

    fun updateTrigger(trigger: Trigger) = edit {
        copy(triggers = triggers.map { if (it.id == trigger.id) trigger else it })
    }

    fun removeTrigger(id: String) = edit { copy(triggers = triggers.filter { it.id != id }) }

    fun setTriggerLogic(logic: TriggerLogic) = edit { copy(triggerLogic = logic) }

    fun addCondition(condition: Condition) = edit { copy(conditions = conditions + condition) }

    fun updateCondition(condition: Condition) = edit {
        copy(conditions = conditions.map { if (it.id == condition.id) condition else it })
    }

    fun removeCondition(id: String) = edit { copy(conditions = conditions.filter { it.id != id }) }

    fun addAction(action: Action) = edit {
        copy(actions = actions + action.copy(order = actions.size))
    }

    /** Appends a batch of actions, assigning consecutive order indices. */
    fun addActions(newActions: List<Action>) = edit {
        val base = actions.size
        val indexed = newActions.mapIndexed { i, a -> a.copy(order = base + i) }
        copy(actions = actions + indexed)
    }

    /**
     * Inserts actions immediately after [anchorId] — the "add into this branch" path for
     * block markers (Menu Case / If / Else / Repeat), where appending at the end would
     * land the new action outside the block. Falls back to appending if the anchor is gone.
     */
    fun addActionsAfter(anchorId: String, newActions: List<Action>) = edit {
        val sorted = actions.sortedBy { it.order }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == anchorId }
        val insertAt = if (idx == -1) sorted.size else idx + 1
        sorted.addAll(insertAt, newActions)
        copy(actions = sorted.mapIndexed { i, a -> a.copy(order = i) })
    }

    /**
     * Updates the SHOW_MENU action's title/options and synchronises the MENU_CASE
     * markers that follow it (by position). Extra options append empty cases;
     * removed options drop their case marker and any body actions up to the next case.
     */
    fun syncMenuBlock(showMenuId: String, newTitle: String, newOptions: List<String>) = edit {
        val sorted = actions.sortedBy { it.order }.toMutableList()
        val showMenuIdx = sorted.indexOfFirst { it.id == showMenuId }
        if (showMenuIdx == -1) return@edit this

        // Find matching END_MENU
        var depth = 0
        var endMenuIdx = -1
        for (k in showMenuIdx until sorted.size) {
            when (sorted[k].type) {
                ActionType.SHOW_MENU -> depth++
                ActionType.END_MENU -> { depth--; if (depth == 0) { endMenuIdx = k; break } }
                else -> {}
            }
        }
        if (endMenuIdx == -1) return@edit this

        // Collect direct MENU_CASE indices (depth=1)
        val caseStartIndices = mutableListOf<Int>()
        depth = 0
        for (k in showMenuIdx until endMenuIdx + 1) {
            when (sorted[k].type) {
                ActionType.SHOW_MENU -> depth++
                ActionType.END_MENU -> depth--
                ActionType.MENU_CASE -> if (depth == 1) caseStartIndices.add(k)
                else -> {}
            }
        }

        // Derive body ranges for each existing case
        val caseBodyRanges = caseStartIndices.mapIndexed { i, startIdx ->
            val bodyStart = startIdx + 1
            val bodyEnd = if (i + 1 < caseStartIndices.size) caseStartIndices[i + 1] else endMenuIdx
            bodyStart until bodyEnd
        }

        val optionsJson = kotlinx.serialization.json.Json.encodeToString(newOptions)
        val updatedShowMenu = sorted[showMenuIdx].copy(
            config = mapOf("title" to newTitle, "options" to optionsJson),
        )

        // Build new block: for each new option reuse existing case (preserving body), add empty if new
        val newBlock = mutableListOf<Action>()
        newOptions.forEachIndexed { i, opt ->
            val caseId = if (i < caseStartIndices.size) sorted[caseStartIndices[i]].id
                         else java.util.UUID.randomUUID().toString()
            newBlock += Action(caseId, ActionType.MENU_CASE, mapOf("option" to opt), 0, true)
            if (i < caseBodyRanges.size) {
                newBlock += sorted.subList(caseBodyRanges[i].first, caseBodyRanges[i].last)
            }
        }

        val before = sorted.subList(0, showMenuIdx)
        val endMenu = sorted[endMenuIdx]
        val after = if (endMenuIdx + 1 < sorted.size) sorted.subList(endMenuIdx + 1, sorted.size) else emptyList()

        val all = (before + updatedShowMenu + newBlock + endMenu + after)
            .mapIndexed { i, a -> a.copy(order = i) }
        copy(actions = all)
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

    /**
     * Re-inserts an action captured before [removeAction] at its original position —
     * the undo path of the delete snackbar. Orders are re-normalised afterwards.
     */
    fun restoreAction(action: Action) = edit {
        val sorted = actions.sortedBy { it.order }.toMutableList()
        sorted.add(action.order.coerceIn(0, sorted.size), action)
        copy(actions = sorted.mapIndexed { i, a -> a.copy(order = i) })
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
        viewModelScope.launch {
            // Block enabling while required permissions are missing — otherwise the engine
            // would subscribe the trigger and crash with a SecurityException. Guide the user
            // through the setup instead; on completion the flow is enabled automatically.
            if (enabled) {
                val current = flow.value ?: return@launch
                val missing = permissionChecker.missingPermissions(current)
                if (missing.isNotEmpty()) {
                    _permissionReminder.emit(
                        PermissionReminder(current.id, current.name, missing, autoEnableOnComplete = true),
                    )
                    return@launch
                }
            }
            repository.setEnabled(flowId, enabled)
        }
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentActionId = MutableStateFlow<String?>(null)
    val currentActionId: StateFlow<String?> = _currentActionId.asStateFlow()

    private var runJob: kotlinx.coroutines.Job? = null

    /**
     * Emits when a manual run was held back by the flow's own conditions. Without it, tapping Run
     * on a constrained flow looks like a bug: nothing happens and the only trace is a log entry.
     */
    private val _runSkipped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val runSkipped: SharedFlow<Unit> = _runSkipped.asSharedFlow()

    fun runNow() {
        if (_isRunning.value) return
        runJob = viewModelScope.launch {
            _isRunning.value = true
            yield()
            try {
                val status = flowEngine.runNow(flowId) { actionId ->
                    _currentActionId.value = actionId
                    delay(300)
                }
                if (status == ExecutionStatus.SKIPPED) _runSkipped.tryEmit(Unit)
            } finally {
                _isRunning.value = false
                _currentActionId.value = null
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
            // Declare the globals this flow uses so it still runs after import elsewhere;
            // only the declaration travels, never this device's live value.
            globalVariables = referencedGlobals(f).map { g ->
                GlobalVariableJson(g.name, g.type.name, g.defaultValue)
            },
        )
        return json.encodeToString(flowJson)
    }

    /** Globals named by a `{{g:x}}` reference or a `g:x` SET_VARIABLE target anywhere in [f]. */
    private fun referencedGlobals(f: Flow): List<GlobalVariable> {
        val text = (f.actions.flatMap { it.config.values } + f.triggers.flatMap { it.config.values })
            .joinToString("\n")
        val referenced = GLOBAL_REF_REGEX.findAll(text).map { it.groupValues[1].trim() }.toMutableSet()
        f.actions.filter { it.type == ActionType.SET_VARIABLE }
            .mapNotNull { it.config["variable_name"] ?: it.config["name"] }
            .filter { it.startsWith(FlowInterpreter.GLOBAL_PREFIX) }
            .forEach { referenced += it.removePrefix(FlowInterpreter.GLOBAL_PREFIX) }
        return globalVariables.value.filter { it.name in referenced }
    }

    private fun edit(transform: Flow.() -> Flow) {
        val current = flow.value ?: return
        val updated = current.transform().copy(updatedAt = System.currentTimeMillis())
        viewModelScope.launch { repository.save(updated) }
    }

    private companion object {
        // Both braces are escaped explicitly — an unescaped `}}` trips some Android regex engines.
        val GLOBAL_REF_REGEX = Regex("""\{\{${FlowInterpreter.GLOBAL_PREFIX}([^}]+)\}\}""")
    }
}
