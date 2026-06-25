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
package com.nexflow.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.permissions.FlowPermissionChecker
import com.nexflow.permissions.MissingPermission
import com.nexflow.service.FlowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PermissionReminder(val flowName: String, val missing: List<MissingPermission>)

@HiltViewModel
class FlowsViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val flowEngine: FlowEngine,
    private val permissionChecker: FlowPermissionChecker,
) : ViewModel() {

    val flows: StateFlow<List<Flow>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _navigateToFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToFlow: SharedFlow<String> = _navigateToFlow.asSharedFlow()

    private val _permissionReminder = MutableSharedFlow<PermissionReminder>(extraBufferCapacity = 1)
    val permissionReminder: SharedFlow<PermissionReminder> = _permissionReminder.asSharedFlow()

    // Bumped whenever the screen resumes so the warning set is recomputed: the user may have
    // granted (or revoked) a permission in system Settings while the app was backgrounded.
    private val _permissionRefresh = MutableStateFlow(0)

    /**
     * Ids of flows that are enabled but still missing a required permission — so the user can see
     * at a glance that an "on" flow is not actually able to run. Recomputed on every flow-list
     * change and on each [refreshPermissionWarnings] (screen resume).
     */
    val flowsMissingPermissions: StateFlow<Set<String>> =
        combine(flows, _permissionRefresh) { list, _ ->
            list.filter { it.enabled && permissionChecker.missingPermissions(it).isNotEmpty() }
                .map { it.id }
                .toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun refreshPermissionWarnings() {
        _permissionRefresh.value++
    }

    /** Re-open the permission guidance dialog for a specific flow (tapping the warning chip). */
    fun showMissingPermissions(id: String) {
        viewModelScope.launch { remindIfMissingPermissions(id) }
    }

    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            // Block enabling while required permissions are missing — otherwise the engine would
            // immediately subscribe the trigger (e.g. GeofencingClient.addGeofences) and a
            // missing-permission SecurityException would crash the app. Guide the user to grant
            // first; the DB stays disabled so the Switch reverts to off. Disabling is never blocked.
            if (enabled) {
                val flow = repository.getById(id) ?: return@launch
                val missing = permissionChecker.missingPermissions(flow)
                if (missing.isNotEmpty()) {
                    _permissionReminder.emit(PermissionReminder(flow.name, missing))
                    return@launch
                }
            }
            repository.setEnabled(id, enabled)
        }
    }

    fun deleteFlow(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun runFlow(id: String) {
        viewModelScope.launch {
            remindIfMissingPermissions(id)
            flowEngine.runNow(id)
        }
    }

    private suspend fun remindIfMissingPermissions(id: String) {
        val flow = repository.getById(id) ?: return
        val missing = permissionChecker.missingPermissions(flow)
        if (missing.isNotEmpty()) {
            _permissionReminder.emit(PermissionReminder(flow.name, missing))
        }
    }

    fun createFlow(name: String, description: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            repository.save(
                Flow(
                    id = id,
                    schemaVersion = 1,
                    name = name,
                    description = description,
                    author = null,
                    tags = emptyList(),
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                    triggers = listOf(
                        Trigger(
                            id = UUID.randomUUID().toString(),
                            type = TriggerType.MANUAL,
                            config = emptyMap(),
                        ),
                    ),
                    triggerLogic = TriggerLogic.ANY,
                    conditions = emptyList(),
                    actions = emptyList(),
                    variables = emptyList(),
                ),
            )
            _navigateToFlow.emit(id)
        }
    }
}
