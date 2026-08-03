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
package com.nexflow.core.automation.condition

import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionGateTest {

    private class FixedEvaluator(
        override val supportedType: ConditionType,
        private val result: Boolean,
        private val throws: Boolean = false,
    ) : ConditionEvaluator {
        override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
            if (throws) error("sensor unavailable")
            return result
        }
    }

    private fun gate(vararg evaluators: ConditionEvaluator) =
        ConditionGate(evaluators.associateBy { it.supportedType })

    private fun condition(
        type: ConditionType,
        negate: Boolean = false,
        config: Map<String, String> = emptyMap(),
    ) = Condition(id = "c-${type.name}", type = type.name, config = config, negate = negate)

    @Test
    fun `no conditions means the flow runs`() = runTest {
        assertEquals(ConditionResult.Satisfied, gate().evaluate(emptyList()))
    }

    @Test
    fun `every condition must hold`() = runTest {
        val g = gate(
            FixedEvaluator(ConditionType.CHARGING, true),
            FixedEvaluator(ConditionType.SCREEN_STATE, false),
        )

        assertEquals(
            ConditionResult.Satisfied,
            g.evaluate(listOf(condition(ConditionType.CHARGING))),
        )
        val result = g.evaluate(
            listOf(condition(ConditionType.CHARGING), condition(ConditionType.SCREEN_STATE)),
        )
        assertTrue(result is ConditionResult.Unsatisfied)
        assertEquals(ConditionType.SCREEN_STATE.name, (result as ConditionResult.Unsatisfied).condition.type)
    }

    @Test
    fun `negate inverts the evaluator's answer`() = runTest {
        val g = gate(FixedEvaluator(ConditionType.CHARGING, false))

        assertTrue(g.evaluate(listOf(condition(ConditionType.CHARGING))) is ConditionResult.Unsatisfied)
        assertEquals(
            ConditionResult.Satisfied,
            g.evaluate(listOf(condition(ConditionType.CHARGING, negate = true))),
        )
    }

    @Test
    fun `an unknown type blocks the run instead of being ignored`() = runTest {
        // The pre-constraint behaviour was to run anyway, which did the exact thing the user's
        // constraint said not to. Failing closed is the point of the gate.
        val result = gate().evaluate(
            listOf(Condition(id = "c1", type = "SOMETHING_FROM_THE_FUTURE", config = emptyMap(), negate = false)),
        )
        assertTrue(result is ConditionResult.Unsatisfied)
        assertTrue((result as ConditionResult.Unsatisfied).reason.contains("SOMETHING_FROM_THE_FUTURE"))
    }

    @Test
    fun `a type with no evaluator blocks the run`() = runTest {
        val result = gate().evaluate(listOf(condition(ConditionType.WIFI_CONNECTED)))
        assertTrue(result is ConditionResult.Unsatisfied)
        assertTrue((result as ConditionResult.Unsatisfied).reason.contains("WIFI_CONNECTED"))
    }

    @Test
    fun `an evaluator that throws blocks the run and reports why`() = runTest {
        val result = gate(FixedEvaluator(ConditionType.BATTERY_LEVEL, true, throws = true))
            .evaluate(listOf(condition(ConditionType.BATTERY_LEVEL)))

        assertTrue(result is ConditionResult.Unsatisfied)
        assertTrue((result as ConditionResult.Unsatisfied).reason.contains("sensor unavailable"))
    }

    @Test
    fun `negate does not resurrect a condition that could not be evaluated`() = runTest {
        // A NOT on an unreadable condition must not turn "cannot check" into "holds".
        val result = gate(FixedEvaluator(ConditionType.BATTERY_LEVEL, true, throws = true))
            .evaluate(listOf(condition(ConditionType.BATTERY_LEVEL, negate = true)))

        assertTrue(result is ConditionResult.Unsatisfied)
    }
}
