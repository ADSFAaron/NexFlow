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
package com.nexflow.ui.logs

import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Collapsing a loop's later rounds is the only thing standing between a 5×-repeat flow and a log
 * the user has to scroll past five times to reach the end. These cover the folding rule itself,
 * which is easy to get subtly wrong once loops nest.
 */
class RunDetailRowsTest {

    private var seq = 0

    private fun step(
        type: ActionType,
        depth: Int = 0,
        iteration: Int = 0,
        actionId: String = "a$seq",
        note: String? = null,
    ) = ExecutionStep(
        logId = "log-1",
        seq = seq++,
        actionId = actionId,
        actionType = type,
        depth = depth,
        iteration = iteration,
        status = ExecutionStatus.SUCCESS,
        errorMessage = null,
        note = note,
        resolvedConfig = null,
        durationMs = 1,
    )

    /** A loop of [rounds] rounds over a two-action body, as the interpreter would report it. */
    private fun loop(rounds: Int, loopId: String = "loop"): List<ExecutionStep> = buildList {
        add(step(ActionType.REPEAT_BLOCK, actionId = loopId, note = "${FlowInterpreter.NOTE_REPEAT}$rounds"))
        repeat(rounds) { round ->
            add(step(ActionType.TOAST, depth = 1, iteration = round))
            add(step(ActionType.SET_VARIABLE, depth = 1, iteration = round))
        }
    }

    @Test
    fun `a collapsed loop shows only its first round`() {
        val rows = visibleRows(loop(rounds = 4), expandedRepeats = emptyMap())

        // The header plus round 0's two actions — rounds 1..3 are folded away.
        assertEquals(3, rows.size)
        assertTrue(rows.drop(1).all { it.step.iteration == 0 })
        assertTrue(rows.first().isRepeatHeader, "the loop row must offer the control that unfolds it")
        assertEquals(4, rows.first().repeatRounds)
    }

    @Test
    fun `expanding the loop reveals every round`() {
        val rows = visibleRows(loop(rounds = 4), expandedRepeats = mapOf("loop" to true))

        assertEquals(1 + 4 * 2, rows.size)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), rows.drop(1).map { it.step.iteration })
    }

    @Test
    fun `a single-round loop offers no expander`() {
        val rows = visibleRows(loop(rounds = 1), expandedRepeats = emptyMap())

        // There is nothing behind the chevron, so showing one would be a dead control.
        assertEquals(3, rows.size)
        assertTrue(!rows.first().isRepeatHeader)
    }

    @Test
    fun `folding a round takes what is nested inside it`() {
        seq = 0
        val steps = buildList {
            add(step(ActionType.REPEAT_BLOCK, actionId = "outer", note = "${FlowInterpreter.NOTE_REPEAT}2"))
            repeat(2) { round ->
                add(step(ActionType.IF_BLOCK, depth = 1, iteration = round, note = FlowInterpreter.NOTE_IF_TRUE))
                add(step(ActionType.TOAST, depth = 2, iteration = round))
                add(step(ActionType.TOAST, depth = 2, iteration = round))
            }
        }

        val rows = visibleRows(steps, expandedRepeats = emptyMap())

        // Round 1's IF and both of the actions under it go together: an action nested inside a
        // hidden round is part of that round, however shallow its own iteration number looks.
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.step.iteration == 0 })
    }

    @Test
    fun `an inner loop's own rounds fold independently of the outer one`() {
        seq = 0
        val steps = buildList {
            add(step(ActionType.REPEAT_BLOCK, actionId = "outer", note = "${FlowInterpreter.NOTE_REPEAT}2"))
            repeat(2) { outerRound ->
                add(
                    step(
                        ActionType.REPEAT_BLOCK, depth = 1, iteration = outerRound,
                        actionId = "inner", note = "${FlowInterpreter.NOTE_REPEAT}3",
                    ),
                )
                repeat(3) { innerRound ->
                    add(step(ActionType.TOAST, depth = 2, iteration = innerRound))
                }
            }
        }

        val collapsed = visibleRows(steps, expandedRepeats = emptyMap())
        // outer header, inner header (outer round 0), inner round 0's single action.
        assertEquals(3, collapsed.size)

        val innerOpen = visibleRows(steps, expandedRepeats = mapOf("inner" to true))
        // The outer loop stays folded, so only its first round's inner loop opens up.
        assertEquals(2 + 3, innerOpen.size)
    }

    @Test
    fun `a flat run passes through untouched`() {
        seq = 0
        val steps = listOf(
            step(ActionType.TOAST),
            step(ActionType.HTTP_REQUEST),
            step(ActionType.NOTIFICATION),
        )

        val rows = visibleRows(steps, expandedRepeats = emptyMap())
        assertEquals(steps.map { it.seq }, rows.map { it.step.seq })
        assertTrue(rows.none { it.isRepeatHeader })
    }

    @Test
    fun `a run with no steps yields no rows`() {
        assertTrue(visibleRows(emptyList(), expandedRepeats = emptyMap()).isEmpty())
    }
}
