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

/**
 * Variable substitution and boolean expressions, shared by the If action inside
 * [FlowInterpreter] and by the flow-level EXPRESSION condition (which runs before the
 * interpreter is even involved). One implementation so the two can never drift apart.
 */
object ExpressionEvaluator {

    /** Replaces `{{varName}}` tokens with current variable values. Unknown names are left as-is. */
    fun interpolate(template: String, variables: Map<String, String>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    /**
     * Evaluates a boolean expression after variable interpolation.
     *
     * Supported forms (see docs/FLOW_SCHEMA.md):
     * - "true" / "false" literals (case-insensitive)
     * - binary comparisons: ==, !=, <=, >=, <, > — numeric when both sides parse
     *   as numbers, otherwise case-insensitive string comparison
     */
    fun evaluate(expression: String, variables: Map<String, String>): Boolean {
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
}
