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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionEvaluatorTest {

    private fun eval(expression: String, vararg vars: Pair<String, String>) =
        ExpressionEvaluator.evaluate(expression, vars.toMap())

    // ----- interpolation -----

    @Test
    fun `unknown references are left visible rather than blanked`() {
        assertEquals("{{nope}}", ExpressionEvaluator.interpolate("{{nope}}", mapOf("a" to "1")))
    }

    @Test
    fun `a value is substituted once, not re-scanned as a template`() {
        // Was order-dependent: replacing per variable meant "{{b}}" arriving inside a's value got
        // substituted too, if b happened to be visited after a.
        val vars = mapOf("a" to "{{b}}", "b" to "boom")
        assertEquals("{{b}}", ExpressionEvaluator.interpolate("{{a}}", vars))
    }

    @Test
    fun `a dollar sign in a value survives substitution`() {
        assertEquals("cost: $5", ExpressionEvaluator.interpolate("cost: {{p}}", mapOf("p" to "$5")))
    }

    @Test
    fun `global and trigger namespaced names resolve like any other`() {
        val vars = mapOf("g:count" to "7", "trigger.body" to "hi")
        assertEquals("7 hi", ExpressionEvaluator.interpolate("{{g:count}} {{trigger.body}}", vars))
    }

    // ----- comparisons -----

    @Test
    fun `numeric and string comparisons still work`() {
        assertTrue(eval("{{battery}} < 20", "battery" to "15"))
        assertFalse(eval("{{battery}} > 20", "battery" to "15"))
        assertTrue(eval("{{battery}} <= 15", "battery" to "15"))
        assertTrue(eval("{{status}} == ok", "status" to "OK"))
        assertTrue(eval("9 < 10"))
        assertTrue(eval("true"))
        assertFalse(eval(""))
    }

    /**
     * The operator has to be found in what the user typed. Splitting the *resolved* string let the
     * data decide where the expression divides — reachable now that an HTTP response can be stored
     * in a variable, and for any URL or query string holding "==".
     */
    @Test
    fun `an operator inside a value does not split the expression`() {
        assertTrue(eval("{{url}} != x", "url" to "https://h/p?a==b"))
        assertTrue(eval("{{url}} == https://h/p?a==b", "url" to "https://h/p?a==b"))
        assertTrue(eval("{{tag}} == <b>", "tag" to "<b>"))
        assertTrue(eval("{{math}} == 3>2", "math" to "3>2"))
    }

    /**
     * The bug this replaces: comparing the literal text "{{battery}}" against "5" answered an
     * ordering question by ASCII, so '{' outranked every digit and "> 5" came back true. A flow
     * then branched on a value it never had.
     */
    @Test
    fun `an unresolved reference never satisfies a comparison`() {
        assertFalse(eval("{{missing}} > 5"))
        assertFalse(eval("{{missing}} < 5"))
        assertFalse(eval("{{missing}} >= 5"))
        assertFalse(eval("{{missing}} <= 5"))
        assertFalse(eval("{{missing}} == 5"))
        assertFalse(eval("{{missing}} != 5"))
        assertFalse(eval("5 > {{missing}}"))
    }

    @Test
    fun `an empty value is resolved, not unresolved`() {
        assertTrue(eval("{{note}} == ", "note" to ""))
        assertFalse(eval("{{note}} != ", "note" to ""))
    }

    /** `<=` must win over `<` at the same position, or the right operand becomes "= 20". */
    @Test
    fun `two-character operators win over their prefixes`() {
        assertTrue(eval("20 <= 20"))
        assertTrue(eval("20 >= 20"))
        assertFalse(eval("20 < 20"))
    }

    /** A variable may still hold a whole expression; that worked before and has to keep working. */
    @Test
    fun `a variable holding an expression is still evaluated`() {
        assertTrue(eval("{{expr}}", "expr" to "1 < 2"))
        assertFalse(eval("{{expr}}", "expr" to "2 < 1"))
        assertTrue(eval("{{flag}}", "flag" to "true"))
        assertFalse(eval("{{flag}}", "flag" to "false"))
    }

    @Test
    fun `quoted operands lose their quotes`() {
        assertTrue(eval("\"{{a}}\" == \"hi\"", "a" to "hi"))
    }

    // ----- arithmetic -----

    private fun math(text: String) = ExpressionEvaluator.arithmeticOrNull(text)

    @Test
    fun `a counter increments instead of accumulating text`() {
        assertEquals("6", math("5 + 1"))
        assertEquals("4", math("5 - 1"))
        assertEquals("15", math("5 * 3"))
        assertEquals("2.5", math("5 / 2"))
    }

    /** Integers must stay integers: a counter showing "2.0" would look broken. */
    @Test
    fun `integer results have no decimal tail`() {
        assertEquals("2", math("1 + 1"))
        assertEquals("100", math("10 * 10"))
        assertEquals("3", math("6 / 2"))
    }

    /** BigDecimal, not Double — 0.1 + 0.2 must not come out as 0.30000000000000004. */
    @Test
    fun `decimal arithmetic is exact`() {
        assertEquals("0.3", math("0.1 + 0.2"))
        assertEquals("0.35", math("0.1 + 0.25"))
    }

    @Test
    fun `multiplication and division bind tighter than addition`() {
        assertEquals("7", math("1 + 2 * 3"))
        assertEquals("7", math("2 * 3 + 1"))
        assertEquals("5", math("1 + 8 / 2"))
        assertEquals("11", math("1 + 2 * 3 + 4"))
    }

    @Test
    fun `subtraction and division are left-associative`() {
        assertEquals("4", math("10 - 5 - 1"))
        assertEquals("5", math("20 / 2 / 2"))
        assertEquals("2", math("10 / 5 * 1"))
    }

    @Test
    fun `a negative operand is accepted`() {
        assertEquals("-4", math("-5 + 1"))
        assertEquals("6", math("5 - -1"))
    }

    @Test
    fun `a non-terminating quotient is rounded rather than throwing`() {
        assertEquals("0.3333333333333333", math("1 / 3"))
    }

    /**
     * The reason every operator needs whitespace around it. Without the rule, storing a date would
     * silently become a subtraction — `2026-08-10` evaluating to `2008`.
     */
    @Test
    fun `values that merely contain an operator are left alone`() {
        assertNull(math("2026-08-10"))
        assertNull(math("0912-345-678"))
        assertNull(math("1.2.3"))
        assertNull(math("1+1"))
        assertNull(math("a/b"))
    }

    @Test
    fun `plain text and lone numbers are not arithmetic`() {
        assertNull(math("hello world"))
        assertNull(math("5"))
        assertNull(math(""))
        assertNull(math("3 apples"))
        assertNull(math("5 +"))
        assertNull(math("5 + + 1"))
    }

    @Test
    fun `division by zero is reported, not silently swallowed`() {
        assertThrows(ArithmeticException::class.java) { math("1 / 0") }
    }
}
