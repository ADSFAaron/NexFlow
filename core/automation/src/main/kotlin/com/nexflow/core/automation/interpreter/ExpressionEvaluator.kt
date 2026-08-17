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

import java.math.BigDecimal
import java.math.MathContext

/**
 * Variable substitution and boolean expressions, shared by the If action inside
 * [FlowInterpreter] and by the flow-level EXPRESSION condition (which runs before the
 * interpreter is even involved). One implementation so the two can never drift apart.
 */
object ExpressionEvaluator {

    /**
     * Replaces `{{varName}}` tokens with current variable values. Unknown names are left as-is, so
     * a typo stays visible in the result instead of turning into an empty string.
     *
     * One pass over the template rather than one `replace` per variable: replacing in sequence made
     * the result depend on the map's iteration order, and substituted `{{x}}` sequences that were
     * part of a *value* — reachable now that an HTTP response can be stored in a variable.
     */
    fun interpolate(template: String, variables: Map<String, String>): String =
        REFERENCE.replace(template) { match -> variables[match.groupValues[1].trim()] ?: match.value }

    /**
     * Interpolates [template] and reports whether every `{{name}}` in it resolved.
     *
     * "Did it resolve" has to be judged against the template, not the result: a value that itself
     * contains `{{…}}` — an API response, say — would otherwise look like an unresolved reference.
     */
    private fun resolve(template: String, variables: Map<String, String>): Resolved {
        var allResolved = true
        val text = REFERENCE.replace(template) { match ->
            variables[match.groupValues[1].trim()] ?: match.value.also { allResolved = false }
        }
        return Resolved(text, allResolved)
    }

    private class Resolved(val text: String, val allResolved: Boolean)

    /**
     * Evaluates [text] as arithmetic over decimal literals with `+ - * /`, or returns null when it
     * is not arithmetic — ordinary text assigned by a SET_VARIABLE has to pass through untouched.
     *
     * **Every operator must have whitespace on both sides.** That is what separates `{{count}} + 1`
     * from a value that merely contains an operator character: without the rule, storing the date
     * `2026-08-10` would quietly become `2008`, and a phone number or a version string would be
     * mangled the same way. Precedence is the usual one, `*` and `/` before `+` and `-`.
     *
     * [java.math.BigDecimal] rather than Double so a counter stays exact: `0.1 + 0.2` is `0.3`, and
     * `1 + 1` is `2` rather than `2.0`.
     *
     * @throws ArithmeticException on division by zero.
     */
    fun arithmeticOrNull(text: String): String? {
        // Mandatory whitespace makes the token split exact — no tokenizer to disagree with.
        val tokens = text.trim().split(WHITESPACE)
        if (tokens.size < 3 || tokens.size % 2 == 0) return null

        val values = mutableListOf<BigDecimal>()
        val operators = mutableListOf<String>()
        tokens.forEachIndexed { index, token ->
            if (index % 2 == 0) {
                values += token.toBigDecimalOrNull() ?: return null
            } else {
                if (token !in HIGH_PRECEDENCE && token !in LOW_PRECEDENCE) return null
                operators += token
            }
        }

        // Two left-to-right passes: `*` and `/` collapse first, then what remains is a flat chain.
        for (pass in listOf(HIGH_PRECEDENCE, LOW_PRECEDENCE)) {
            var i = 0
            while (i < operators.size) {
                if (operators[i] !in pass) {
                    i++
                    continue
                }
                values[i] = apply(values[i], operators[i], values[i + 1])
                values.removeAt(i + 1)
                operators.removeAt(i)
            }
        }
        return values.single().stripTrailingZeros().toPlainString()
    }

    private fun apply(left: BigDecimal, op: String, right: BigDecimal): BigDecimal = when (op) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        // A non-terminating quotient (1 / 3) throws without a rounding context.
        else -> left.divide(right, MathContext.DECIMAL64)
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
        val template = expression.trim()
        // Split on an operator the *user* wrote, before interpolation. Splitting the resolved string
        // let a value decide where the expression divides: an API field or a URL holding "==" would
        // be cut in half and compared against the wrong thing.
        val split = findOperator(template)
            // Nothing to split on: the whole thing is a truthy check. Resolve it first so a variable
            // holding "true" — or holding an entire expression — still behaves as it used to.
            ?: return interpolate(template, variables).let { resolved ->
                findOperator(resolved)
                    ?.let { compare(resolved, it, variables, alreadyResolved = true) }
                    ?: resolved.equals("true", ignoreCase = true)
            }
        return compare(template, split, variables, alreadyResolved = false)
    }

    private fun compare(
        text: String,
        split: Split,
        variables: Map<String, String>,
        alreadyResolved: Boolean,
    ): Boolean {
        val rawLeft = text.substring(0, split.index)
        val rawRight = text.substring(split.index + split.op.length)
        val left: String
        val right: String
        if (alreadyResolved) {
            left = rawLeft.trim().removeSurrounding("\"")
            right = rawRight.trim().removeSurrounding("\"")
        } else {
            val l = resolve(rawLeft, variables)
            val r = resolve(rawRight, variables)
            // A name that resolved to nothing is a typo, a trigger that reported no such value, or
            // an import against variables this device does not have. Comparing the literal text
            // "{{battery}}" against "20" answered *some* ordering question — "{{x}} > 5" came back
            // true because '{' outranks '5' — and the flow then took a branch on a value it never
            // had. An unanswerable condition must not fire.
            if (!l.allResolved || !r.allResolved) return false
            left = l.text.trim().removeSurrounding("\"")
            right = r.text.trim().removeSurrounding("\"")
        }

        val leftNum = left.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()
        return if (leftNum != null && rightNum != null) {
            when (split.op) {
                "==" -> leftNum == rightNum
                "!=" -> leftNum != rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> leftNum >= rightNum
                "<" -> leftNum < rightNum
                else -> leftNum > rightNum
            }
        } else {
            val cmp = left.compareTo(right, ignoreCase = true)
            when (split.op) {
                "==" -> cmp == 0
                "!=" -> cmp != 0
                "<=" -> cmp <= 0
                ">=" -> cmp >= 0
                "<" -> cmp < 0
                else -> cmp > 0
            }
        }
    }

    /**
     * The left-most operator, preferring the two-character form where both could match — otherwise
     * `<=` would be read as `<` with a right operand of `= 20`.
     */
    private fun findOperator(text: String): Split? = OPERATORS
        .mapNotNull { op -> text.indexOf(op).takeIf { it > 0 }?.let { Split(op, it) } }
        .minWithOrNull(compareBy({ it.index }, { -it.op.length }))

    private class Split(val op: String, val index: Int)

    private val OPERATORS = listOf("==", "!=", "<=", ">=", "<", ">")

    private val WHITESPACE = Regex("""\s+""")
    private val HIGH_PRECEDENCE = setOf("*", "/")
    private val LOW_PRECEDENCE = setOf("+", "-")

    /** @see FlowInterpreter.GLOBAL_PREFIX — `g:`-namespaced names are ordinary references here. */
    private val REFERENCE = Regex("""\{\{([^{}]+)\}\}""")
}
