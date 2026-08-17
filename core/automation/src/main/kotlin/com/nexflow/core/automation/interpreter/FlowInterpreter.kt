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
package com.nexflow.core.automation.interpreter

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Variable
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

/**
 * One action's outcome as the interpreter sees it, before a run has an id to file it under.
 *
 * The caller turns these into [com.nexflow.core.automation.model.ExecutionStep]s by adding the
 * run's id and a sequence number — the interpreter deliberately knows nothing about how (or
 * whether) they are stored.
 */
data class StepReport(
    val actionId: String,
    val actionType: ActionType,
    val depth: Int,
    val iteration: Int,
    val status: ExecutionStatus,
    val errorMessage: String? = null,
    val note: String? = null,
    val resolvedConfig: String? = null,
    val durationMs: Long = 0L,
)

/**
 * Interprets and executes a Flow's action list, including control-flow constructs
 * (IF/ELSE/END_IF, REPEAT/END_REPEAT) and variable substitution.
 *
 * This class is pure Kotlin with no Android dependencies so it can be unit-tested on the JVM.
 */
class FlowInterpreter(
    private val executors: Map<com.nexflow.core.automation.model.ActionType, ActionExecutor>,
) {
    /**
     * @param globalVariables cross-flow variables (name -> current value). They are merged into
     *   the run's variable map under the [GLOBAL_PREFIX] namespace, so a flow references them as
     *   `{{g:name}}` and they never collide with a flow's own variables.
     * @param triggerVariables what the trigger reported about the event that started this run
     *   (see [com.nexflow.core.automation.trigger.TriggerVariables]), merged under the
     *   [TRIGGER_PREFIX] namespace — a flow reads the incoming SMS body as `{{trigger.body}}`.
     *   Empty for a manual run that carries no event.
     * @param onGlobalVariableSet invoked (with the un-prefixed name) whenever a SET_VARIABLE action
     *   writes a `g:`-namespaced variable, so the caller can persist the new value for other flows.
     *   Only names present in [globalVariables] can be written — a write to an unknown `g:` name
     *   fails the run instead of silently creating a variable nobody declared (see
     *   [InterpreterResult.Failure]), so a typo surfaces in the execution log.
     * @param onStep invoked once per executed action with its outcome, in execution order. This is
     *   what the per-run build log is built from, so it fires for successes too — and for actions
     *   skipped because they are disabled, which is otherwise indistinguishable from "never
     *   reached". Control-flow end markers (END_IF, END_REPEAT, END_MENU, ELSE, MENU_CASE) are not
     *   reported: they do no work, and a log of them is noise the user has to read past.
     * @param recordResolvedConfig include each action's post-substitution config in its report.
     *   Off by default — that config is the actual URL, message body and header values the run
     *   used, which is exactly why it is useful for debugging and exactly why it should not be
     *   written to storage unless the user asked for it.
     */
    suspend fun execute(
        flow: Flow,
        globalVariables: Map<String, String> = emptyMap(),
        triggerVariables: Map<String, String> = emptyMap(),
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
        onGlobalVariableSet: (suspend (name: String, value: String) -> Unit)? = null,
        onStep: (suspend (StepReport) -> Unit)? = null,
        recordResolvedConfig: Boolean = false,
    ): InterpreterResult {
        val variables = buildVariableMap(flow.variables)
        globalVariables.forEach { (name, value) -> variables["$GLOBAL_PREFIX$name"] = value }
        triggerVariables.forEach { (name, value) -> variables["$TRIGGER_PREFIX$name"] = value }
        val actions = flow.actions.sortedBy { it.order }
        return executeBlock(
            actions = actions,
            variables = variables,
            startIndex = 0,
            endIndex = actions.size,
            knownGlobals = globalVariables.keys,
            onActionStart = onActionStart,
            onGlobalVariableSet = onGlobalVariableSet,
            sink = onStep?.let { StepSink(recordResolvedConfig, it) },
        )
    }

    /**
     * Per-run step reporting. Bundled into one object so the run-scoped setting travels with the
     * callback down the recursion instead of becoming another parameter at every call site — and
     * so `null` means "nobody is listening", letting the hot path skip the work entirely.
     */
    private class StepSink(
        val recordResolvedConfig: Boolean,
        val emit: suspend (StepReport) -> Unit,
    )

    /**
     * Records the step as failed and returns the run-level failure carrying the same message.
     * Every abort path goes through here so no failure can reach the run summary without also
     * marking the row that caused it — "the run failed" with no row highlighted is the exact
     * hole this whole mechanism exists to close.
     */
    private suspend fun reportFailure(
        sink: StepSink?,
        action: Action,
        depth: Int,
        iteration: Int,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        message: String,
        cause: Throwable? = null,
        resolvedConfig: String? = null,
    ): InterpreterResult {
        sink?.emit(
            StepReport(
                actionId = action.id,
                actionType = action.type,
                depth = depth,
                iteration = iteration,
                status = ExecutionStatus.FAIL,
                errorMessage = message,
                resolvedConfig = resolvedConfig,
                durationMs = startedAt.elapsedNow().inWholeMilliseconds,
            ),
        )
        return InterpreterResult.Failure(message, cause)
    }

    /** Renders an interpolated config for the log; `null` unless the user opted into detail. */
    private fun StepSink.configOf(action: Action): String? =
        if (recordResolvedConfig) {
            action.config.entries
                .filter { it.value.isNotBlank() }
                .joinToString("\n") { (k, v) -> "$k: $v" }
                .takeIf { it.isNotEmpty() }
        } else {
            null
        }

    private suspend fun executeBlock(
        actions: List<Action>,
        variables: MutableMap<String, String>,
        startIndex: Int,
        endIndex: Int,
        knownGlobals: Set<String>,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
        onGlobalVariableSet: (suspend (name: String, value: String) -> Unit)? = null,
        sink: StepSink? = null,
        depth: Int = 0,
        iteration: Int = 0,
    ): InterpreterResult {
        var i = startIndex
        while (i < endIndex) {
            val action = actions[i]
            if (!action.enabled) {
                // Reported, not silently passed over: "this action is switched off" and "the run
                // never got this far" look identical in a log that omits it, and they call for
                // opposite fixes.
                sink?.emit(
                    StepReport(
                        actionId = action.id,
                        actionType = action.type,
                        depth = depth,
                        iteration = iteration,
                        status = ExecutionStatus.SKIPPED,
                        note = NOTE_DISABLED,
                    ),
                )
                i++
                continue
            }
            onActionStart?.invoke(action.id)
            val startedAt = TimeSource.Monotonic.markNow()

            when (action.type) {
                ActionType.IF_BLOCK -> {
                    val expression = action.config["expression"] ?: ""
                    val conditionMet = evaluateExpression(expression, variables)
                    val elseIndex = findMatchingElse(actions, i)
                    val endIfIndex = findMatchingEndIf(actions, i)

                    // Emitted before the branch runs, so the log reads in execution order: the
                    // condition row, then whatever it let through. Which way it went is the whole
                    // reason a user opens the log of a flow that "did nothing".
                    sink?.emit(
                        StepReport(
                            actionId = action.id,
                            actionType = action.type,
                            depth = depth,
                            iteration = iteration,
                            status = ExecutionStatus.SUCCESS,
                            note = if (conditionMet) NOTE_IF_TRUE else NOTE_IF_FALSE,
                            resolvedConfig = sink.configOf(interpolateAction(action, variables)),
                            durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    )

                    if (conditionMet) {
                        val blockEnd = if (elseIndex != -1) elseIndex else endIfIndex
                        val result = executeBlock(actions, variables, i + 1, blockEnd, knownGlobals, onActionStart, onGlobalVariableSet, sink, depth + 1, iteration)
                        if (result is InterpreterResult.Failure) return result
                    } else if (elseIndex != -1) {
                        val result = executeBlock(actions, variables, elseIndex + 1, endIfIndex, knownGlobals, onActionStart, onGlobalVariableSet, sink, depth + 1, iteration)
                        if (result is InterpreterResult.Failure) return result
                    }
                    i = endIfIndex + 1
                }

                ActionType.REPEAT_BLOCK -> {
                    // Clamp to a sane bound: a malformed/imported flow with a huge count would
                    // otherwise block the single execution dispatcher indefinitely.
                    val count = (action.config["count"]?.toIntOrNull() ?: 1)
                        .coerceIn(0, MAX_REPEAT_COUNT)
                    val endRepeatIndex = findMatchingEndRepeat(actions, i)
                    sink?.emit(
                        StepReport(
                            actionId = action.id,
                            actionType = action.type,
                            depth = depth,
                            iteration = iteration,
                            status = ExecutionStatus.SUCCESS,
                            note = "$NOTE_REPEAT$count",
                            durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    )
                    repeat(count) { round ->
                        // Cooperatively yield so a long loop stays cancellable (e.g. when the
                        // user stops the flow or the service is torn down).
                        yield()
                        // The round number rides down with the block: it is what lets the log
                        // group a loop's steps by pass instead of listing count × body rows flat.
                        val result = executeBlock(actions, variables, i + 1, endRepeatIndex, knownGlobals, onActionStart, onGlobalVariableSet, sink, depth + 1, round)
                        if (result is InterpreterResult.Failure) return result
                    }
                    i = endRepeatIndex + 1
                }

                ActionType.SET_VARIABLE -> {
                    // Fails the step before it fails the run, so the build log points at this row
                    // rather than only carrying the message up to the run's summary.
                    suspend fun fail(message: String, cause: Throwable? = null): InterpreterResult {
                        sink?.emit(
                            StepReport(
                                actionId = action.id,
                                actionType = action.type,
                                depth = depth,
                                iteration = iteration,
                                status = ExecutionStatus.FAIL,
                                errorMessage = message,
                                durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                            ),
                        )
                        return InterpreterResult.Failure(message, cause)
                    }

                    // UI writes "variable_name"; older flows and MacroDroid imports may use "name"
                    val name = action.config["variable_name"] ?: action.config["name"]
                        ?: return fail("SET_VARIABLE missing name")
                    val interpolated = interpolate(action.config["value"] ?: "", variables)
                    // "{{counter}} + 1" has to come out as a number, not as the text "5 + 1" that
                    // grows by three characters every run. Anything that is not arithmetic — which
                    // is most values — is stored exactly as interpolated.
                    val newValue = try {
                        ExpressionEvaluator.arithmeticOrNull(interpolated) ?: interpolated
                    } catch (e: ArithmeticException) {
                        return fail("SET_VARIABLE '$name': cannot evaluate \"$interpolated\" (${e.message})", e)
                    }
                    if (name.startsWith(GLOBAL_PREFIX)) {
                        // A g:-write must target a global the user actually declared. Failing here
                        // (rather than writing a run-local copy nobody persists) keeps the typo case
                        // consistent: the name is never readable, and the run's log names it.
                        val bare = name.removePrefix(GLOBAL_PREFIX)
                        if (bare !in knownGlobals) {
                            return fail(unknownGlobalMessage(bare))
                        }
                        variables[name] = newValue
                        onGlobalVariableSet?.invoke(bare, newValue)
                    } else {
                        variables[name] = newValue
                    }
                    sink?.emit(
                        StepReport(
                            actionId = action.id,
                            actionType = action.type,
                            depth = depth,
                            iteration = iteration,
                            status = ExecutionStatus.SUCCESS,
                            // The assignment is this action's entire observable effect, so detail
                            // mode shows the value itself rather than the "{{x}} + 1" that made it.
                            resolvedConfig = if (sink.recordResolvedConfig) "$name = $newValue" else null,
                            durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    )
                    i++
                }

                ActionType.SHOW_MENU -> {
                    val interpolatedAction = interpolateAction(action, variables)
                    val executor = executors[ActionType.SHOW_MENU]
                        ?: return reportFailure(sink, action, depth, iteration, startedAt, "No executor for SHOW_MENU")
                    val result = try {
                        executor.execute(interpolatedAction, variables)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ActionResult.Failure("SHOW_MENU threw: ${e.message}", e)
                    }
                    if (result is ActionResult.Failure) {
                        return reportFailure(sink, action, depth, iteration, startedAt, result.message, result.cause)
                    }
                    val choice = variables["__menu_choice__"] ?: ""
                    val endMenuIndex = findMatchingEndMenu(actions, i)
                    val caseIndex = findMenuCase(actions, i, endMenuIndex, choice)
                    // What the user picked is the branch point, and unlike an IF it is not
                    // recoverable from the flow — nothing else in the run records the choice.
                    sink?.emit(
                        StepReport(
                            actionId = action.id,
                            actionType = action.type,
                            depth = depth,
                            iteration = iteration,
                            status = ExecutionStatus.SUCCESS,
                            note = "$NOTE_MENU$choice",
                            durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    )
                    if (caseIndex != -1) {
                        val nextBoundary = findNextMenuCaseOrEnd(actions, caseIndex + 1, endMenuIndex)
                        val blockResult = executeBlock(actions, variables, caseIndex + 1, nextBoundary, knownGlobals, onActionStart, onGlobalVariableSet, sink, depth + 1, iteration)
                        if (blockResult is InterpreterResult.Failure) return blockResult
                    }
                    i = endMenuIndex + 1
                }

                ActionType.ELSE_BLOCK, ActionType.END_IF, ActionType.END_REPEAT,
                ActionType.MENU_CASE, ActionType.END_MENU -> i++

                else -> {
                    val interpolatedAction = interpolateAction(action, variables)
                    val executor = executors[action.type]
                        ?: return reportFailure(sink, action, depth, iteration, startedAt, "No executor for ${action.type}")
                    val result = try {
                        executor.execute(interpolatedAction, variables)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ActionResult.Failure("${action.type} threw: ${e.message}", e)
                    }
                    if (result is ActionResult.Failure) {
                        return reportFailure(
                            sink, action, depth, iteration, startedAt, result.message, result.cause,
                            resolvedConfig = sink?.configOf(interpolatedAction),
                        )
                    }
                    sink?.emit(
                        StepReport(
                            actionId = action.id,
                            actionType = action.type,
                            depth = depth,
                            iteration = iteration,
                            status = ExecutionStatus.SUCCESS,
                            resolvedConfig = sink.configOf(interpolatedAction),
                            durationMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    )
                    i++
                }
            }
        }
        return InterpreterResult.Success
    }

    // Replaces {{varName}} tokens with current variable values.
    internal fun interpolate(template: String, variables: Map<String, String>): String =
        ExpressionEvaluator.interpolate(template, variables)

    private fun interpolateAction(action: Action, variables: Map<String, String>): Action {
        val interpolatedConfig = action.config.mapValues { (_, v) -> interpolate(v, variables) }
        return action.copy(config = interpolatedConfig)
    }

    /** @see ExpressionEvaluator.evaluate */
    internal fun evaluateExpression(expression: String, variables: Map<String, String>): Boolean =
        ExpressionEvaluator.evaluate(expression, variables)

    private fun findMatchingEndIf(actions: List<Action>, ifIndex: Int): Int {
        var depth = 0
        for (i in ifIndex until actions.size) {
            when (actions[i].type) {
                ActionType.IF_BLOCK -> depth++
                ActionType.END_IF -> { depth--; if (depth == 0) return i }
                else -> {}
            }
        }
        return actions.size - 1
    }

    private fun findMatchingElse(actions: List<Action>, ifIndex: Int): Int {
        var depth = 0
        for (i in ifIndex until actions.size) {
            when (actions[i].type) {
                ActionType.IF_BLOCK -> depth++
                ActionType.END_IF -> { depth--; if (depth == 0) return -1 }
                ActionType.ELSE_BLOCK -> { if (depth == 1) return i }
                else -> {}
            }
        }
        return -1
    }

    private fun findMatchingEndRepeat(actions: List<Action>, repeatIndex: Int): Int {
        var depth = 0
        for (i in repeatIndex until actions.size) {
            when (actions[i].type) {
                ActionType.REPEAT_BLOCK -> depth++
                ActionType.END_REPEAT -> { depth--; if (depth == 0) return i }
                else -> {}
            }
        }
        return actions.size - 1
    }

    private fun findMatchingEndMenu(actions: List<Action>, menuIndex: Int): Int {
        var depth = 0
        for (i in menuIndex until actions.size) {
            when (actions[i].type) {
                ActionType.SHOW_MENU -> depth++
                ActionType.END_MENU -> { depth--; if (depth == 0) return i }
                else -> {}
            }
        }
        return actions.size - 1
    }

    private fun findMenuCase(actions: List<Action>, menuIndex: Int, endMenuIndex: Int, choice: String): Int {
        var depth = 0
        for (i in menuIndex until endMenuIndex) {
            when (actions[i].type) {
                ActionType.SHOW_MENU -> depth++
                ActionType.END_MENU -> depth--
                ActionType.MENU_CASE -> if (depth == 1 && actions[i].config["option"] == choice) return i
                else -> {}
            }
        }
        return -1
    }

    private fun findNextMenuCaseOrEnd(actions: List<Action>, startIndex: Int, endMenuIndex: Int): Int {
        var depth = 0
        for (i in startIndex until endMenuIndex) {
            when (actions[i].type) {
                ActionType.SHOW_MENU -> depth++
                ActionType.END_MENU -> depth--
                ActionType.MENU_CASE -> if (depth == 0) return i
                else -> {}
            }
        }
        return endMenuIndex
    }

    private fun buildVariableMap(variables: List<Variable>): MutableMap<String, String> =
        variables.associate { it.name to it.defaultValue }.toMutableMap()

    companion object {
        /** Upper bound for a single REPEAT block's iteration count. */
        const val MAX_REPEAT_COUNT = 10_000

        /** Namespace prefix for global (cross-flow) variables, referenced as `{{g:name}}`. */
        const val GLOBAL_PREFIX = "g:"

        /**
         * Namespace prefix for values supplied by the trigger, referenced as `{{trigger.name}}`.
         * Mirrors [com.nexflow.core.automation.trigger.TriggerVariables.PREFIX], kept here so the
         * interpreter stays independent of the trigger package.
         */
        const val TRIGGER_PREFIX = "trigger."

        /** Failure text for a SET_VARIABLE that targets a `g:` name no global variable declares. */
        fun unknownGlobalMessage(bareName: String): String =
            "Unknown global variable '$GLOBAL_PREFIX$bareName' — create it in Settings → Global Variables first"

        // Tokens for StepReport.note. The UI turns these into localized text at render time, so a
        // log written in one language still reads correctly after the user switches to another.
        // The two carrying an argument keep the trailing colon in the constant.
        const val NOTE_DISABLED = "disabled"
        const val NOTE_IF_TRUE = "if_true"
        const val NOTE_IF_FALSE = "if_false"
        const val NOTE_REPEAT = "repeat:"
        const val NOTE_MENU = "menu:"
    }
}

sealed class InterpreterResult {
    data object Success : InterpreterResult()
    data class Failure(val message: String, val cause: Throwable? = null) : InterpreterResult()
}
