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

/**
 * Interprets and executes a Flow's action list, including control-flow constructs
 * (IF/ELSE/END_IF, REPEAT/END_REPEAT) and variable substitution.
 *
 * This class is pure Kotlin with no Android dependencies so it can be unit-tested on the JVM.
 */
class FlowInterpreter(
    private val executors: Map<com.nexflow.core.automation.model.ActionType, ActionExecutor>,
) {
    suspend fun execute(flow: Flow): InterpreterResult {
        val variables = buildVariableMap(flow.variables)
        val actions = flow.actions.sortedBy { it.order }
        return executeBlock(actions, variables, startIndex = 0, endIndex = actions.size)
    }

    private suspend fun executeBlock(
        actions: List<Action>,
        variables: MutableMap<String, String>,
        startIndex: Int,
        endIndex: Int,
    ): InterpreterResult {
        var i = startIndex
        while (i < endIndex) {
            val action = actions[i]
            if (!action.enabled) { i++; continue }

            when (action.type) {
                ActionType.IF_BLOCK -> {
                    val expression = action.config["expression"] ?: ""
                    val conditionMet = evaluateExpression(expression, variables)
                    val elseIndex = findMatchingElse(actions, i)
                    val endIfIndex = findMatchingEndIf(actions, i)

                    if (conditionMet) {
                        val blockEnd = if (elseIndex != -1) elseIndex else endIfIndex
                        val result = executeBlock(actions, variables, i + 1, blockEnd)
                        if (result is InterpreterResult.Failure) return result
                    } else if (elseIndex != -1) {
                        val result = executeBlock(actions, variables, elseIndex + 1, endIfIndex)
                        if (result is InterpreterResult.Failure) return result
                    }
                    i = endIfIndex + 1
                }

                ActionType.REPEAT_BLOCK -> {
                    val count = action.config["count"]?.toIntOrNull() ?: 1
                    val endRepeatIndex = findMatchingEndRepeat(actions, i)
                    repeat(count) {
                        val result = executeBlock(actions, variables, i + 1, endRepeatIndex)
                        if (result is InterpreterResult.Failure) return result
                    }
                    i = endRepeatIndex + 1
                }

                ActionType.SET_VARIABLE -> {
                    // UI writes "variable_name"; older flows and MacroDroid imports may use "name"
                    val name = action.config["variable_name"] ?: action.config["name"]
                        ?: return InterpreterResult.Failure("SET_VARIABLE missing name")
                    val rawValue = action.config["value"] ?: ""
                    variables[name] = interpolate(rawValue, variables)
                    i++
                }

                ActionType.ELSE_BLOCK, ActionType.END_IF, ActionType.END_REPEAT -> i++

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
    internal fun interpolate(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    private fun interpolateAction(action: Action, variables: Map<String, String>): Action {
        val interpolatedConfig = action.config.mapValues { (_, v) -> interpolate(v, variables) }
        return action.copy(config = interpolatedConfig)
    }

    /**
     * Evaluates a boolean expression after variable interpolation.
     *
     * Supported forms (see docs/FLOW_SCHEMA.md):
     * - "true" / "false" literals (case-insensitive)
     * - binary comparisons: ==, !=, <=, >=, <, > — numeric when both sides parse
     *   as numbers, otherwise case-insensitive string comparison
     */
    internal fun evaluateExpression(expression: String, variables: Map<String, String>): Boolean {
        val resolved = interpolate(expression.trim(), variables)

        // Two-char operators must be matched before their single-char prefixes.
        for (op in listOf("==", "!=", "<=", ">=", "<", ">")) {
            val idx = resolved.indexOf(op)
            if (idx <= 0) continue
            val left = resolved.substring(0, idx).trim().removeSurrounding("\"")
            val right = resolved.substring(idx + op.length).trim().removeSurrounding("\"")
            val leftNum = left.toDoubleOrNull()
            val rightNum = right.toDoubleOrNull()
            return if (leftNum != null && rightNum != null) {
                when (op) {
                    "==" -> leftNum == rightNum
                    "!=" -> leftNum != rightNum
                    "<=" -> leftNum <= rightNum
                    ">=" -> leftNum >= rightNum
                    "<" -> leftNum < rightNum
                    else -> leftNum > rightNum
                }
            } else {
                val cmp = left.compareTo(right, ignoreCase = true)
                when (op) {
                    "==" -> cmp == 0
                    "!=" -> cmp != 0
                    "<=" -> cmp <= 0
                    ">=" -> cmp >= 0
                    "<" -> cmp < 0
                    else -> cmp > 0
                }
            }
        }
        return resolved.equals("true", ignoreCase = true)
    }

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

    private fun buildVariableMap(variables: List<Variable>): MutableMap<String, String> =
        variables.associate { it.name to it.defaultValue }.toMutableMap()
}

sealed class InterpreterResult {
    data object Success : InterpreterResult()
    data class Failure(val message: String, val cause: Throwable? = null) : InterpreterResult()
}
