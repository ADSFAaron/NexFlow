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
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.theme.NexFlowTheme
import java.time.ZoneOffset

/**
 * One failed run: the summary tile with its error, then the steps as a group — nesting under an
 * IF (the guide line), a REPEAT header with its round count and chevron, a skipped step, and the
 * failing step. Step labels and notes are the translated strings here, so the locales matter.
 *
 * The summary's time is rendered in UTC, not the render host's zone, so the image is the same
 * on every machine.
 */
private fun step(
    seq: Int,
    type: ActionType,
    depth: Int = 0,
    status: ExecutionStatus = ExecutionStatus.SUCCESS,
    note: String? = null,
    error: String? = null,
    durationMs: Long = 12,
) = ExecutionStep(
    logId = "run",
    seq = seq,
    actionId = "a$seq",
    actionType = type,
    depth = depth,
    iteration = 0,
    status = status,
    errorMessage = error,
    note = note,
    resolvedConfig = null,
    durationMs = durationMs,
)

@PreviewTest
@LocaleThemePreviews
@Composable
fun RunDetailFailed() {
    val detail = RunDetail(
        log = ExecutionLog(
            id = "run",
            flowId = "flow",
            triggeredAt = 1_793_000_000_000L,
            status = ExecutionStatus.FAIL,
            errorMessage = "Wi-Fi: permission WRITE_SETTINGS not granted",
            executionDurationMs = 1830,
        ),
        flowName = "Arrive at the office",
        steps = listOf(
            step(0, ActionType.IF_BLOCK, note = "if_true"),
            step(1, ActionType.NOTIFICATION, depth = 1),
            step(2, ActionType.REPEAT_BLOCK, note = "repeat:3", durationMs = 640),
            step(3, ActionType.DELAY, depth = 1, durationMs = 200),
            step(4, ActionType.HTTP_REQUEST, status = ExecutionStatus.SKIPPED, note = "disabled", durationMs = 0),
            step(
                5, ActionType.WIFI_TOGGLE,
                status = ExecutionStatus.FAIL,
                error = "permission WRITE_SETTINGS not granted",
                durationMs = 31,
            ),
        ),
        droppedSteps = 0,
    )
    NexFlowTheme(dynamicColor = false) {
        RunDetailContent(detail = detail, onBack = {}, zone = ZoneOffset.UTC)
    }
}
