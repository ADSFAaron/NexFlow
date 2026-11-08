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

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.theme.NexFlowTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The Logs tab grouped by day. "Today" and "Yesterday" are the strings the locales exercise;
 * the third heading is a formatted date, whose field order (Nov 6 vs 11月6日) is the other thing
 * that differs per language. Today is pinned so the headings never drift under the images.
 *
 * Each status appears once, and the failed run carries an error line — the tallest tile and the
 * one coloured summary on the page.
 */

private val today = LocalDate.of(2026, 11, 8)
private val zone = ZoneOffset.UTC

private fun run(
    id: String,
    name: String,
    at: LocalDateTime,
    status: ExecutionStatus = ExecutionStatus.SUCCESS,
    error: String? = null,
    durationMs: Long = 240,
) = LogEntry(
    log = ExecutionLog(
        id = id,
        flowId = id,
        triggeredAt = at.toInstant(zone).toEpochMilli(),
        status = status,
        errorMessage = error,
        executionDurationMs = durationMs,
    ),
    flowName = name,
)

@PreviewTest
@LocaleThemePreviews
@Composable
fun LogsGroupedByDay() {
    val entries = listOf(
        run("1", "Morning routine", today.atTime(8, 30, 5)),
        run(
            "2", "Arrive at the office", today.atTime(8, 2, 41),
            status = ExecutionStatus.FAIL,
            error = "Wi-Fi: permission WRITE_SETTINGS not granted",
            durationMs = 1830,
        ),
        run("3", "Charging reminder", today.atTime(0, 15, 0), status = ExecutionStatus.SKIPPED, durationMs = 3),
        run("4", "Bedtime", today.minusDays(1).atTime(23, 0, 0)),
        run("5", "Morning routine", today.minusDays(1).atTime(8, 30, 2)),
        run("6", "Weekly backup", today.minusDays(6).atTime(21, 0, 0), durationMs = 12_400),
    )
    NexFlowTheme(dynamicColor = false) {
        LogsContent(
            days = groupByDay(entries, zone),
            today = today,
            zone = zone,
            onEntryClick = {},
            onClearAll = {},
        )
    }
}
