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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            if (enabled) remindIfMissingPermissions(id)
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
