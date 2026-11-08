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

import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Logs tab's day headings. The edge worth pinning is midnight in the *device's* zone: a run
 * at 00:30 local time is still the previous day in UTC, and grouping by UTC date would file it
 * under "Yesterday" for anyone east of Greenwich.
 */
class LogDaysTest {

    private val taipei = ZoneId.of("Asia/Taipei")

    private fun entry(id: String, at: LocalDateTime, zone: ZoneId = taipei) = LogEntry(
        log = ExecutionLog(
            id = id,
            flowId = "f",
            triggeredAt = at.atZone(zone).toInstant().toEpochMilli(),
            status = ExecutionStatus.SUCCESS,
            errorMessage = null,
            executionDurationMs = 10,
        ),
        flowName = "Flow",
    )

    @Test
    fun `runs split into days newest first, keeping their order within a day`() {
        val entries = listOf(
            entry("c", LocalDateTime.of(2026, 11, 8, 9, 0)),
            entry("b", LocalDateTime.of(2026, 11, 8, 0, 30)),
            entry("a", LocalDateTime.of(2026, 11, 7, 23, 59)),
            entry("z", LocalDateTime.of(2025, 12, 31, 8, 0)),
        )

        val days = groupByDay(entries, taipei)

        assertEquals(
            listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7), LocalDate.of(2025, 12, 31)),
            days.map { it.date },
        )
        assertEquals(listOf("c", "b"), days[0].entries.map { it.log.id })
        assertEquals(listOf("a"), days[1].entries.map { it.log.id })
    }

    @Test
    fun `a run just after local midnight belongs to the local day, not the UTC one`() {
        // 00:30 in Taipei is 16:30 the previous day in UTC.
        val days = groupByDay(listOf(entry("x", LocalDateTime.of(2026, 11, 8, 0, 30))), taipei)

        assertEquals(LocalDate.of(2026, 11, 8), days.single().date)
    }

    @Test
    fun `no runs, no days`() {
        assertEquals(emptyList<LogDay>(), groupByDay(emptyList(), taipei))
    }
}
