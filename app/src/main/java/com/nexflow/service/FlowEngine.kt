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

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.nexflow.R
import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.condition.ConditionGate
import com.nexflow.core.automation.condition.ConditionResult
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.interpreter.InterpreterResult
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.core.automation.trigger.TriggerVariables
import com.nexflow.prefs.DetailedLogPrefs
import com.nexflow.prefs.ExecutionFeedbackPrefs
import com.nexflow.trigger.TimeTriggerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val globalVariableRepository: GlobalVariableRepository,
    private val triggerHandlerSet: Set<@JvmSuppressWildcards TriggerHandler>,
    private val actionExecutorSet: Set<@JvmSuppressWildcards ActionExecutor>,
    private val conditionEvaluatorSet: Set<@JvmSuppressWildcards ConditionEvaluator>,
    private val timeTriggerScheduler: TimeTriggerScheduler,
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "FlowEngine"
    }

    private val triggerHandlers = triggerHandlerSet.associateBy { it.supportedType }
    private val interpreter by lazy { FlowInterpreter(actionExecutorSet.associateBy { it.supportedType }) }
    private val conditionGate by lazy { ConditionGate(conditionEvaluatorSet.associateBy { it.supportedType }) }
    private val allTriggersGate = AllTriggersGate()

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
                                        // Carry the id plus what the trigger reported about the
                                        // event; the freshest flow is re-fetched when the trigger
                                        // fires, so action edits take effect without needing to
                                        // re-subscribe.
                                        triggerHandlers[trigger.type]
                                            ?.observe(trigger)
                                            ?.map { event ->
                                                TriggeredRun(
                                                    flowId = flow.id,
                                                    triggerId = trigger.id,
                                                    triggerVariables = triggerVariables(trigger.type, event),
                                                )
                                            }
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
                                    .collect { run ->
                                        launch {
                                            runFlowById(
                                                flowId = run.flowId,
                                                triggerVariables = run.triggerVariables,
                                                triggerId = run.triggerId,
                                            )
                                        }
                                    }
                            }
                        }
                    }
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
        // Half-finished ALL combinations belong to the session that started them.
        allTriggersGate.clear()
    }

    /**
     * Directly execute a flow by ID — used by the manual Run button and alarm/tile/widget runs.
     *
     * @param triggerVariables what the caller knows about the event behind this run, exposed to
     *   the flow as `{{trigger.x}}`. A manual run passes nothing but the type.
     * @param triggerId the trigger this run came from, when there is one (the TIME alarm knows
     *   its own). Only a run attributable to a trigger takes part in [TriggerLogic.ALL]; a manual
     *   run means "run now", not "record one part of a combination".
     * @return how the run ended, or null when it never started (flow deleted, the same flow is
     *   already running, or ALL is still waiting for the flow's other triggers). Conditions apply
     *   to a manual run like any other, so the caller can tell the user why tapping Run appeared
     *   to do nothing.
     */
    suspend fun runNow(
        flowId: String,
        triggerVariables: Map<String, String> = emptyMap(),
        triggerId: String? = null,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ): ExecutionStatus? = runFlowById(flowId, triggerVariables, triggerId, onActionStart)

    private suspend fun runFlowById(
        flowId: String,
        triggerVariables: Map<String, String> = emptyMap(),
        triggerId: String? = null,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ): ExecutionStatus? {
        val flow = repository.getById(flowId) ?: return null

        // TriggerLogic.ALL: hold this fire until the flow's other triggers have fired too.
        // A single-trigger flow is trivially "all", and is short-circuited so it never touches
        // the gate's state.
        val values = if (
            triggerId != null &&
            flow.triggerLogic == TriggerLogic.ALL &&
            flow.triggers.size > 1
        ) {
            allTriggersGate.onFire(
                flowId = flow.id,
                requiredTriggerIds = flow.triggers.map { it.id },
                firedTriggerId = triggerId,
                variables = triggerVariables,
            ) ?: run {
                Log.d(TAG, "Flow '${flow.name}' waiting: ALL needs its other triggers to fire too")
                return null
            }
        } else {
            triggerVariables
        }

        return runFlow(flow, values, onActionStart)
    }

    private suspend fun runFlow(
        flow: AutomationFlow,
        triggerVariables: Map<String, String>,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
    ): ExecutionStatus? {
        // Skip if this flow is already executing — rapid repeated triggers must not stack
        // overlapping runs of the same flow.
        if (!runningFlows.add(flow.id)) return null
        try {
            val startMs = System.currentTimeMillis()
            val globals = globalVariableRepository.currentValues()
            val triggerValues = defaultTriggerVariables() + triggerVariables

            // The trigger says when, the conditions say whether. Checked before the toast and
            // before any action runs, so a flow held back by a constraint is silent — but it
            // still lands in the log as SKIPPED with the reason, otherwise "nothing happened"
            // is indistinguishable from a broken trigger.
            val gate = conditionGate.evaluate(
                conditions = flow.conditions,
                variables = conditionVariables(flow, globals, triggerValues),
            )
            if (gate is ConditionResult.Unsatisfied) {
                Log.d(TAG, "Flow '${flow.name}' skipped: ${gate.reason}")
                repository.saveExecutionLog(
                    ExecutionLog(
                        id = UUID.randomUUID().toString(),
                        flowId = flow.id,
                        triggeredAt = startMs,
                        status = ExecutionStatus.SKIPPED,
                        errorMessage = gate.reason,
                        executionDurationMs = System.currentTimeMillis() - startMs,
                    ),
                )
                return ExecutionStatus.SKIPPED
            }

            // Background triggers firing silently confused users — announce every run
            // (opt-out in Settings).
            if (ExecutionFeedbackPrefs.isToastEnabled(context)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_flow_running, flow.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            // Collected for every run, not just failing ones: a run that "did nothing" is the
            // hardest kind to diagnose, and it looks identical to a healthy one from the summary.
            val steps = StepCollector()
            val result = try {
                interpreter.execute(
                    flow = flow,
                    globalVariables = globals,
                    triggerVariables = triggerValues,
                    onActionStart = onActionStart,
                    // Only declared globals reach this callback — the interpreter fails the run on a
                    // write to an unknown g: name, so a typo lands in the execution log instead of
                    // silently creating a global nobody declared.
                    onGlobalVariableSet = globalVariableRepository::updateValue,
                    onStep = steps::add,
                    recordResolvedConfig = DetailedLogPrefs.isEnabled(context),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InterpreterResult.Failure("Unexpected error in flow '${flow.name}': ${e.message}", e)
            }
            val durationMs = System.currentTimeMillis() - startMs
            val status = if (result is InterpreterResult.Success) {
                ExecutionStatus.SUCCESS
            } else {
                ExecutionStatus.FAIL
            }

            val logId = UUID.randomUUID().toString()
            repository.saveExecutionLog(
                ExecutionLog(
                    id = logId,
                    flowId = flow.id,
                    triggeredAt = startMs,
                    status = status,
                    errorMessage = (result as? InterpreterResult.Failure)?.message,
                    executionDurationMs = durationMs,
                ),
                steps = steps.toSteps(logId),
            )
            return status
        } finally {
            runningFlows.remove(flow.id)
        }
    }

    /**
     * What the conditions are evaluated against: the same names the actions will see, so
     * `{{trigger.body}}` or `{{g:mode}}` mean the same thing in a constraint and in an action.
     * Flow variables contribute their declared defaults — nothing has run yet to change them.
     */
    private fun conditionVariables(
        flow: AutomationFlow,
        globals: Map<String, String>,
        triggerValues: Map<String, String>,
    ): Map<String, String> = buildMap {
        flow.variables.forEach { put(it.name, it.defaultValue) }
        globals.forEach { (name, value) -> put("${FlowInterpreter.GLOBAL_PREFIX}$name", value) }
        triggerValues.forEach { (name, value) -> put("${FlowInterpreter.TRIGGER_PREFIX}$name", value) }
    }

    /** Values every run carries even when the caller supplied none (manual runs, widget, tile). */
    private fun defaultTriggerVariables(): Map<String, String> = mapOf(
        TriggerVariables.TYPE to TriggerType.MANUAL.name,
        TriggerVariables.TIMESTAMP to System.currentTimeMillis().toString(),
    )

    /**
     * Trigger-reported values plus the two the engine always supplies. Engine values are applied
     * last so a handler cannot accidentally overwrite the type or the timestamp.
     */
    private fun triggerVariables(type: TriggerType, event: TriggerEvent): Map<String, String> =
        event.metadata + mapOf(
            TriggerVariables.TYPE to type.name,
            TriggerVariables.TIMESTAMP to event.timestampMs.toString(),
        )

    /** One trigger firing: which flow, which trigger, and what the trigger knew about the event. */
    private data class TriggeredRun(
        val flowId: String,
        val triggerId: String,
        val triggerVariables: Map<String, String>,
    )
}
