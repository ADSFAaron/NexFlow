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

import android.util.Log
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.interpreter.InterpreterResult
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.trigger.TimeTriggerScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlowEngine @Inject constructor(
    private val repository: FlowRepository,
    private val triggerHandlerSet: Set<@JvmSuppressWildcards TriggerHandler>,
    private val actionExecutorSet: Set<@JvmSuppressWildcards ActionExecutor>,
    private val timeTriggerScheduler: TimeTriggerScheduler,
) {
    private companion object {
        const val TAG = "FlowEngine"
    }

    private val triggerHandlers = triggerHandlerSet.associateBy { it.supportedType }
    private val interpreter by lazy { FlowInterpreter(actionExecutorSet.associateBy { it.supportedType }) }

    private var engineJob: Job? = null

    /** Flow ids currently executing, to drop overlapping runs of the same flow. */
    private val runningFlows = ConcurrentHashMap.newKeySet<String>()

    fun start(scope: CoroutineScope) {
        engineJob = scope.launch {
            // TIME triggers are driven by AlarmManager (see TimeTriggerScheduler), not the
            // in-process streams below, so they survive Doze and service death. Keep alarms in
            // sync with the current enabled set on every change.
            launch {
                repository.observeEnabled().collect { timeTriggerScheduler.sync(it) }
            }

            // Rebuild the in-process trigger streams ONLY when the trigger structure changes.
            // Editing a flow's actions/name re-emits the whole list; without this guard every
            // such edit would tear down and re-subscribe all observers, dropping any event that
            // arrived during the gap. TIME is excluded — handled by AlarmManager above.
            launch {
                repository.observeEnabled()
                    .map { flows -> flows.filter { f -> f.triggers.any { it.type != TriggerType.TIME } } }
                    .distinctUntilChangedBy { flows -> flows.map { it.id to it.triggers } }
                    .collectLatest { flows ->
                        if (flows.isEmpty()) return@collectLatest
                        coroutineScope {
                            val streams = flows.flatMap { flow ->
                                flow.triggers
                                    .filter { it.type != TriggerType.TIME }
                                    .mapNotNull { trigger ->
                                        // Carry only the id; the freshest flow is re-fetched when
                                        // the trigger fires, so action edits take effect without
                                        // needing to re-subscribe.
                                        triggerHandlers[trigger.type]
                                            ?.observe(trigger)
                                            ?.map { flow.id }
                                            // Isolate per-trigger failures: a handler that errors
                                            // (e.g. geofence registration failing) must not crash
                                            // the merged collector and tear down EVERY other
                                            // trigger. Swallow + log; that one stream just ends.
                                            ?.catch { e ->
                                                if (e is CancellationException) throw e
                                                Log.w(
                                                    TAG,
                                                    "Trigger ${trigger.type} for flow ${flow.id} " +
                                                        "failed; stream stopped",
                                                    e,
                                                )
                                            }
                                    }
                            }
                            if (streams.isNotEmpty()) {
                                streams.asFlow()
                                    .flattenMerge(concurrency = streams.size)
                                    .collect { flowId -> launch { runFlowById(flowId) } }
                            }
                        }
                    }
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
    }

    /** Directly execute a flow by ID — used by the manual Run button and alarm/tile/widget runs. */
    suspend fun runNow(
        flowId: String,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ) = runFlowById(flowId, onActionStart)

    private suspend fun runFlowById(
        flowId: String,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ) {
        val flow = repository.getById(flowId) ?: return
        runFlow(flow, onActionStart)
    }

    private suspend fun runFlow(
        flow: AutomationFlow,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ) {
        // Skip if this flow is already executing — rapid repeated triggers must not stack
        // overlapping runs of the same flow.
        if (!runningFlows.add(flow.id)) return
        try {
            val startMs = System.currentTimeMillis()
            val result = try {
                interpreter.execute(flow, onActionStart)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InterpreterResult.Failure("Unexpected error in flow '${flow.name}': ${e.message}", e)
            }
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
        } finally {
            runningFlows.remove(flow.id)
        }
    }
}
