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
package com.nexflow.ui.flows.detail

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The IF condition builder must round-trip through the interpreter's expression format:
 * whatever a saved flow stores, [parseCondition] -> [serializeCondition] must reproduce
 * verbatim, so opening and re-saving an IF block never silently changes its meaning.
 */
class ConditionExpressionTest {

    @Test
    fun `parses a variable comparison`() {
        val (left, op, right) = parseCondition("{{battery}} < 20")
        assertEquals("{{battery}}", left)
        assertEquals("<", op)
        assertEquals("20", right)
    }

    @Test
    fun `two-char operators win over their prefixes`() {
        assertEquals(Triple("{{a}}", "<=", "5"), parseCondition("{{a}} <= 5"))
        assertEquals(Triple("{{a}}", ">=", "5"), parseCondition("{{a}} >= 5"))
        assertEquals(Triple("{{a}}", "!=", "ok"), parseCondition("{{a}} != ok"))
    }

    @Test
    fun `no operator means a truthy check on the whole string`() {
        assertEquals(Triple("true", "==", ""), parseCondition("true"))
        assertEquals(Triple("{{flag}}", "==", ""), parseCondition("{{flag}}"))
    }

    @Test
    fun `empty right operand serializes to just the left`() {
        assertEquals("true", serializeCondition("true", "==", ""))
        assertEquals("{{flag}}", serializeCondition("{{flag}}", "<", ""))
    }

    @Test
    fun `empty condition stays empty`() {
        assertEquals("", serializeCondition("", "==", ""))
    }

    @Test
    fun `round-trips every supported operator`() {
        listOf(
            "{{battery}} == 20",
            "{{battery}} != 20",
            "{{battery}} < 20",
            "{{battery}} <= 20",
            "{{battery}} > 20",
            "{{battery}} >= 20",
            "{{status}} == ok",
            "true",
        ).forEach { expr ->
            val (l, op, r) = parseCondition(expr)
            assertEquals(expr, serializeCondition(l, op, r), "round-trip failed for: $expr")
        }
    }
}
