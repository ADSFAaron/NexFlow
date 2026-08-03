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
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Variable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

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
     */
    suspend fun execute(
        flow: Flow,
        globalVariables: Map<String, String> = emptyMap(),
        triggerVariables: Map<String, String> = emptyMap(),
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
        onGlobalVariableSet: (suspend (name: String, value: String) -> Unit)? = null,
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
        )
    }

    private suspend fun executeBlock(
        actions: List<Action>,
        variables: MutableMap<String, String>,
        startIndex: Int,
        endIndex: Int,
        knownGlobals: Set<String>,
        onActionStart: (suspend (actionId: String) -> Unit)? = null,
        onGlobalVariableSet: (suspend (name: String, value: String) -> Unit)? = null,
    ): InterpreterResult {
        var i = startIndex
        while (i < endIndex) {
            val action = actions[i]
            if (!action.enabled) { i++; continue }
            onActionStart?.invoke(action.id)

            when (action.type) {
                ActionType.IF_BLOCK -> {
                    val expression = action.config["expression"] ?: ""
                    val conditionMet = evaluateExpression(expression, variables)
                    val elseIndex = findMatchingElse(actions, i)
                    val endIfIndex = findMatchingEndIf(actions, i)

                    if (conditionMet) {
                        val blockEnd = if (elseIndex != -1) elseIndex else endIfIndex
                        val result = executeBlock(actions, variables, i + 1, blockEnd, knownGlobals, onActionStart, onGlobalVariableSet)
                        if (result is InterpreterResult.Failure) return result
                    } else if (elseIndex != -1) {
                        val result = executeBlock(actions, variables, elseIndex + 1, endIfIndex, knownGlobals, onActionStart, onGlobalVariableSet)
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
                    repeat(count) {
                        // Cooperatively yield so a long loop stays cancellable (e.g. when the
                        // user stops the flow or the service is torn down).
                        yield()
                        val result = executeBlock(actions, variables, i + 1, endRepeatIndex, knownGlobals, onActionStart, onGlobalVariableSet)
                        if (result is InterpreterResult.Failure) return result
                    }
                    i = endRepeatIndex + 1
                }

                ActionType.SET_VARIABLE -> {
                    // UI writes "variable_name"; older flows and MacroDroid imports may use "name"
                    val name = action.config["variable_name"] ?: action.config["name"]
                        ?: return InterpreterResult.Failure("SET_VARIABLE missing name")
                    val newValue = interpolate(action.config["value"] ?: "", variables)
                    if (name.startsWith(GLOBAL_PREFIX)) {
                        // A g:-write must target a global the user actually declared. Failing here
                        // (rather than writing a run-local copy nobody persists) keeps the typo case
                        // consistent: the name is never readable, and the run's log names it.
                        val bare = name.removePrefix(GLOBAL_PREFIX)
                        if (bare !in knownGlobals) {
                            return InterpreterResult.Failure(unknownGlobalMessage(bare))
                        }
                        variables[name] = newValue
                        onGlobalVariableSet?.invoke(bare, newValue)
                    } else {
                        variables[name] = newValue
                    }
                    i++
                }

                ActionType.SHOW_MENU -> {
                    val interpolatedAction = interpolateAction(action, variables)
                    val executor = executors[ActionType.SHOW_MENU]
                        ?: return InterpreterResult.Failure("No executor for SHOW_MENU")
                    val result = try {
                        executor.execute(interpolatedAction, variables)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ActionResult.Failure("SHOW_MENU threw: ${e.message}", e)
                    }
                    if (result is ActionResult.Failure) {
                        return InterpreterResult.Failure(result.message, result.cause)
                    }
                    val choice = variables["__menu_choice__"] ?: ""
                    val endMenuIndex = findMatchingEndMenu(actions, i)
                    val caseIndex = findMenuCase(actions, i, endMenuIndex, choice)
                    if (caseIndex != -1) {
                        val nextBoundary = findNextMenuCaseOrEnd(actions, caseIndex + 1, endMenuIndex)
                        val blockResult = executeBlock(actions, variables, caseIndex + 1, nextBoundary, knownGlobals, onActionStart, onGlobalVariableSet)
                        if (blockResult is InterpreterResult.Failure) return blockResult
                    }
                    i = endMenuIndex + 1
                }

                ActionType.ELSE_BLOCK, ActionType.END_IF, ActionType.END_REPEAT,
                ActionType.MENU_CASE, ActionType.END_MENU -> i++

                else -> {
                    val interpolatedAction = interpolateAction(action, variables)
                    val executor = executors[action.type]
                        ?: return InterpreterResult.Failure("No executor for ${action.type}")
                    val result = try {
                        executor.execute(interpolatedAction, variables)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ActionResult.Failure("${action.type} threw: ${e.message}", e)
                    }
                    if (result is ActionResult.Failure) {
                        return InterpreterResult.Failure(result.message, result.cause)
                    }
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
    }
}

sealed class InterpreterResult {
    data object Success : InterpreterResult()
    data class Failure(val message: String, val cause: Throwable? = null) : InterpreterResult()
}
