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
package com.nexflow.ui.logs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.core.automation.repository.FlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RunDetail(
    val log: ExecutionLog?,
    val flowName: String,
    val steps: List<ExecutionStep>,
    /**
     * Steps the run executed but the collector did not keep (see
     * [com.nexflow.service.StepCollector]). Derived rather than stored: seq numbers every step the
     * run took, kept or not, so the highest surviving seq still reveals how many went missing.
     */
    val droppedSteps: Int,
    val loading: Boolean = false,
)

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    repository: FlowRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val logId: String = savedStateHandle[ARG_LOG_ID] ?: ""

    val detail: StateFlow<RunDetail> =
        if (logId.isEmpty()) {
            flowOf(RunDetail(null, "", emptyList(), 0))
        } else {
            combine(
                repository.observeLog(logId),
                repository.observeStepsForLog(logId),
                repository.observeAll(),
            ) { log, steps, flows ->
                RunDetail(
                    log = log,
                    flowName = flows.firstOrNull { it.id == log?.flowId }?.name.orEmpty(),
                    steps = steps,
                    droppedSteps = steps.lastOrNull()?.let { it.seq + 1 - steps.size } ?: 0,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // loading = true so the screen shows a spinner rather than flashing "run not found"
            // for the frame before the first database emission arrives.
            RunDetail(null, "", emptyList(), 0, loading = true),
        )

    companion object {
        const val ARG_LOG_ID = "logId"
    }
}
