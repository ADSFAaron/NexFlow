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

/**
 * Decides when [FlowExecutionService] may stop after serving one-off requests (a widget tap, a
 * shortcut, a tag) while the master switch is off.
 *
 * "Run one flow, then leave" used to be a plain `stopSelf()` at the end of each run. That stops
 * the service unconditionally, and stopping it cancels every run still in progress. The common
 * way to hit it was not even two flows: tapping a widget twice. The second tap finds the flow
 * already running, ends at once, and its `stopSelf()` killed the first tap's run halfway.
 *
 * So the service stops only once the last request has finished, and through
 * `stopSelfResult(startId)` with the newest start id: a request delivered but not yet handled
 * (it waits in the main looper behind this call) has a newer id, so the stop is refused and that
 * request is served.
 *
 * Main thread only — [FlowExecutionService.onStartCommand] runs there, and keeping the bookkeeping
 * on the same thread is what makes "no runs left" and "no request pending" agree.
 */
internal class StopWhenIdle(
    /** `Service.stopSelfResult`. */
    private val stop: (startId: Int) -> Unit,
    /** True while the service should stay up regardless (the master switch is on). */
    private val keepRunning: () -> Boolean,
) {
    private var active = 0
    private var lastStartId = 0

    /** Every [android.app.Service.onStartCommand], before anything else. */
    fun onStartCommand(startId: Int) {
        lastStartId = startId
    }

    fun runStarted() {
        active++
    }

    fun runEnded() {
        active--
        if (active <= 0 && !keepRunning()) stop(lastStartId)
    }

    /** Nothing was started for this request; leave if nothing else is going on either. */
    fun nothingToDo() {
        if (active <= 0 && !keepRunning()) stop(lastStartId)
    }
}
