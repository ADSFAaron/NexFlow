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

import com.nexflow.core.automation.interpreter.StepReport
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepCollectorTest {

    private fun report(
        id: String = "a",
        status: ExecutionStatus = ExecutionStatus.SUCCESS,
        message: String? = null,
    ) = StepReport(
        actionId = id,
        actionType = ActionType.TOAST,
        depth = 0,
        iteration = 0,
        status = status,
        errorMessage = message,
    )

    @Test
    fun `steps are numbered in the order they were reported`() {
        val collector = StepCollector()
        repeat(3) { collector.add(report(id = "a$it")) }

        val steps = collector.toSteps("log-1")
        assertEquals(listOf(0, 1, 2), steps.map { it.seq })
        assertEquals(listOf("a0", "a1", "a2"), steps.map { it.actionId })
        assertTrue(steps.all { it.logId == "log-1" }, "the run's id is stamped on every step")
    }

    @Test
    fun `a run longer than the cap keeps the beginning and counts the rest`() {
        val collector = StepCollector(limit = 5)
        repeat(12) { collector.add(report(id = "a$it")) }

        val steps = collector.toSteps("log-1")
        assertEquals(5, steps.size)
        assertEquals(listOf("a0", "a1", "a2", "a3", "a4"), steps.map { it.actionId })
        assertEquals(7, collector.droppedCount)
    }

    @Test
    fun `the failure survives the cap`() {
        val collector = StepCollector(limit = 3)
        repeat(20) { collector.add(report(id = "a$it")) }
        collector.add(report(id = "boom", status = ExecutionStatus.FAIL, message = "no url"))

        val steps = collector.toSteps("log-1")
        // Everything past the cap was dropped except the one step the user opened the log for.
        val failure = steps.singleOrNull { it.status == ExecutionStatus.FAIL }
        assertNotNull(failure, "the failing step must never be dropped")
        assertEquals("no url", failure!!.errorMessage)
    }

    @Test
    fun `seq keeps counting through dropped steps so the gap is honest`() {
        val collector = StepCollector(limit = 2)
        repeat(9) { collector.add(report(id = "a$it")) }
        collector.add(report(id = "boom", status = ExecutionStatus.FAIL))

        val steps = collector.toSteps("log-1")
        // The failure was the tenth step reported, and says so — which is what lets the detail
        // screen work out how many rows are missing without storing a second counter.
        assertEquals(9, steps.last().seq)
        assertEquals(2 + 1, steps.size)
        assertEquals(
            collector.droppedCount,
            steps.last().seq + 1 - steps.size,
            "the screen derives the dropped count this way; the two must agree",
        )
    }

    @Test
    fun `oversized text is truncated rather than stored whole`() {
        val collector = StepCollector()
        collector.add(
            report(status = ExecutionStatus.FAIL, message = "x".repeat(StepCollector.MAX_TEXT * 3)),
        )

        // A 256 KB HTTP body reaching this far would otherwise be written to the database and
        // read back on every open of the run it belongs to.
        assertEquals(StepCollector.MAX_TEXT, collector.toSteps("log-1").single().errorMessage!!.length)
    }

    @Test
    fun `a run that reported nothing produces no rows`() {
        assertTrue(StepCollector().toSteps("log-1").isEmpty())
        assertEquals(0, StepCollector().droppedCount)
    }
}
