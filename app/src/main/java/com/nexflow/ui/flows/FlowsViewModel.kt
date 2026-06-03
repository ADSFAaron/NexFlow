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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FlowsViewModel @Inject constructor(
    private val repository: FlowRepository,
) : ViewModel() {

    val flows: StateFlow<List<Flow>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun deleteFlow(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun createFlow(name: String, description: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.save(
                Flow(
                    id = UUID.randomUUID().toString(),
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
        }
    }
}
