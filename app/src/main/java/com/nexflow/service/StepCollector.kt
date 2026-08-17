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
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep

/**
 * Buffers one run's [StepReport]s and turns them into storable [ExecutionStep]s.
 *
 * A run is unbounded: a REPEAT of 10,000 rounds over a five-action body would otherwise write
 * 50,000 rows for one execution, every execution. So this keeps the first [limit] steps and counts
 * the rest — the same shape as a truncated build log, where the beginning is what explains how the
 * run got going and the tail is noise.
 *
 * The one step that always survives the cap is the failure. The interpreter aborts the run on the
 * first one, so there is at most a single such step and keeping it costs nothing — but it is the
 * only row the user actually opened the log to find, and dropping it would defeat the feature.
 *
 * Not thread-safe, and does not need to be: one instance belongs to one run, and the interpreter
 * reports steps sequentially from that run's coroutine.
 */
class StepCollector(private val limit: Int = DEFAULT_LIMIT) {

    private val steps = mutableListOf<ExecutionStep>()
    private var seq = 0
    private var dropped = 0

    /** How many steps were executed but not kept, because the run exceeded [limit]. */
    val droppedCount: Int get() = dropped

    fun add(report: StepReport) {
        // seq counts every step, kept or not, so the numbering in a truncated log still matches
        // the run: a gap is honest, renumbering the survivors would not be.
        val current = seq++
        if (steps.size >= limit && report.status != ExecutionStatus.FAIL) {
            dropped++
            return
        }
        steps += ExecutionStep(
            // Filled in by toSteps(): the run has no id until it finishes and is written.
            logId = "",
            seq = current,
            actionId = report.actionId,
            actionType = report.actionType,
            depth = report.depth,
            iteration = report.iteration,
            status = report.status,
            errorMessage = report.errorMessage?.take(MAX_TEXT),
            note = report.note?.take(MAX_TEXT),
            resolvedConfig = report.resolvedConfig?.take(MAX_TEXT),
            durationMs = report.durationMs,
        )
    }

    fun toSteps(logId: String): List<ExecutionStep> = steps.map { it.copy(logId = logId) }

    companion object {
        /**
         * Enough to cover any flow a person builds by hand start to finish, small enough that a
         * runaway loop cannot grow the database without bound.
         */
        const val DEFAULT_LIMIT = 200

        /**
         * Cap on each stored text field. An HTTP response can be 256 KB (see HttpActionExecutor);
         * a message that size is not readable in a log row and would be paid for on every read of
         * the run it belongs to.
         */
        const val MAX_TEXT = 2_000
    }
}
