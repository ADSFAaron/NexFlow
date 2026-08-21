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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text the copy button puts on the clipboard. It is what gets pasted into a message or an
 * issue, so it has to stand on its own — read top to bottom, with no icons and nothing collapsed.
 *
 * Robolectric because the transcript names each action with its localized label.
 */
@RunWith(AndroidJUnit4::class)
class RunTranscriptTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun step(
        seq: Int,
        type: ActionType,
        status: ExecutionStatus = ExecutionStatus.SUCCESS,
        depth: Int = 0,
        note: String? = null,
        error: String? = null,
        config: String? = null,
        durationMs: Long = 0,
    ) = ExecutionStep(
        logId = "log-1",
        seq = seq,
        actionId = "a$seq",
        actionType = type,
        depth = depth,
        iteration = 0,
        status = status,
        errorMessage = error,
        note = note,
        resolvedConfig = config,
        durationMs = durationMs,
    )

    private fun detail(
        steps: List<ExecutionStep>,
        status: ExecutionStatus = ExecutionStatus.SUCCESS,
        error: String? = null,
        dropped: Int = 0,
    ) = RunDetail(
        loading = false,
        flowName = "Navigation mode",
        log = ExecutionLog(
            id = "log-1",
            flowId = "flow-1",
            triggeredAt = 1_755_000_000_000,
            status = status,
            errorMessage = error,
            executionDurationMs = 7_200,
        ),
        steps = steps,
        droppedSteps = dropped,
    )

    @Test
    fun `names the flow and how the run ended`() {
        val text = transcriptOf(context, detail(listOf(step(0, ActionType.TOAST))))

        assertTrue(text, text.startsWith("Navigation mode\n"))
        assertTrue(text, text.contains(statusLabel(context, ExecutionStatus.SUCCESS)))
        assertTrue("the run's duration belongs in the header: $text", text.contains("7.2s"))
    }

    @Test
    fun `writes every step in order, with its resolved settings`() {
        val text = transcriptOf(
            context,
            detail(
                listOf(
                    step(0, ActionType.VOLUME_ADJUST, config = "stream: MEDIA\nlevel: 15", durationMs = 6),
                    step(1, ActionType.OPEN_APP, config = "package_name: com.example", durationMs = 52),
                ),
            ),
        )

        assertTrue(text, text.indexOf("stream: MEDIA") < text.indexOf("package_name: com.example"))
        // A multi-line config must stay under its own step rather than flattening into one line.
        assertTrue(text, text.contains("level: 15"))
    }

    @Test
    fun `marks a failure so it can be found by eye in a wall of text`() {
        val text = transcriptOf(
            context,
            detail(
                listOf(step(0, ActionType.HTTP_REQUEST, status = ExecutionStatus.FAIL, error = "boom")),
                status = ExecutionStatus.FAIL,
                error = "boom",
            ),
        )

        assertTrue(text, text.contains("[FAIL]"))
        assertTrue(text, text.contains("boom"))
    }

    @Test
    fun `indents nested steps so the structure survives the paste`() {
        val text = transcriptOf(
            context,
            detail(
                listOf(
                    step(0, ActionType.IF_BLOCK, note = "if_true"),
                    step(1, ActionType.TOAST, depth = 1),
                ),
            ),
        )

        val toastLine = text.lines().first { it.contains("[ok]") && it.trimStart().isNotEmpty() && it.startsWith("  ") }
        assertTrue("a nested step must be indented: $text", toastLine.startsWith("  "))
        assertTrue("the branch taken is part of the record: $text", text.contains("[if_true]"))
    }

    @Test
    fun `says when the run was longer than what was kept`() {
        val kept = transcriptOf(context, detail(listOf(step(0, ActionType.TOAST)), dropped = 0))
        val truncated = transcriptOf(context, detail(listOf(step(0, ActionType.TOAST)), dropped = 12))

        // Asserted on the sentence, not on the bare count: the header carries a date, and a
        // digit test would pass or fail on whatever day the fixture timestamp lands.
        val notice = context.resources.getQuantityString(com.nexflow.R.plurals.run_steps_truncated, 12, 12)
        assertFalse("a complete run must not claim steps were dropped: $kept", kept.contains(notice))
        assertTrue("a truncated run must say so, or the paste reads as the whole run: $truncated",
            truncated.contains(notice))
    }
}
