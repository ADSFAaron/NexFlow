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
package com.nexflow.service

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.interpreter.InterpreterResult
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.trigger.TriggerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlowEngine @Inject constructor(
    private val repository: FlowRepository,
    private val triggerHandlerSet: Set<@JvmSuppressWildcards TriggerHandler>,
    private val actionExecutorSet: Set<@JvmSuppressWildcards ActionExecutor>,
) {
    private val triggerHandlers = triggerHandlerSet.associateBy { it.supportedType }
    private val interpreter by lazy { FlowInterpreter(actionExecutorSet.associateBy { it.supportedType }) }

    private var engineJob: Job? = null

    fun start(scope: CoroutineScope) {
        engineJob = scope.launch {
            repository.observeEnabled().collectLatest { flows ->
                if (flows.isEmpty()) return@collectLatest
                coroutineScope {
                    // Build one observable stream per (flow, trigger) pair that has a matching handler
                    val streams = flows.flatMap { flow ->
                        flow.triggers.mapNotNull { trigger ->
                            triggerHandlers[trigger.type]
                                ?.observe(trigger)
                                ?.map { flow }
                        }
                    }
                    if (streams.isNotEmpty()) {
                        streams.asFlow()
                            .flattenMerge(concurrency = streams.size)
                            .collect { flow -> launch { runFlow(flow) } }
                    }
                }
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
    }

    /** Directly execute a flow by ID — used by the manual Run button. */
    suspend fun runNow(flowId: String) {
        val flow = repository.getById(flowId) ?: return
        runFlow(flow)
    }

    private suspend fun runFlow(flow: AutomationFlow) {
        val startMs = System.currentTimeMillis()
        val result = interpreter.execute(flow)
        val durationMs = System.currentTimeMillis() - startMs

        repository.saveExecutionLog(
            ExecutionLog(
                id = UUID.randomUUID().toString(),
                flowId = flow.id,
                triggeredAt = startMs,
                status = if (result is InterpreterResult.Success) ExecutionStatus.SUCCESS else ExecutionStatus.FAIL,
                errorMessage = (result as? InterpreterResult.Failure)?.message,
                executionDurationMs = durationMs,
            ),
        )
    }
}
