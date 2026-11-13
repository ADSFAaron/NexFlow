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
package com.nexflow.trigger

import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.trigger.TriggerVariables
import java.util.UUID

/**
 * What happens when a TIME trigger's alarm goes off — everything [com.nexflow.receiver.TimeAlarmReceiver]
 * decides, kept free of Android so it can be tested on the JVM.
 *
 * The point of pulling it out is the two ways a scheduled run used to vanish without a trace:
 * the master switch was off (skipped on purpose, but nothing said so), or Android refused to
 * let the app start its service from the background — which it does for every alarm but an
 * exact one, and the refusal was caught and only written to logcat. A user saw a flow simply
 * not run at its time. Both now leave a line in the run log, and a refusal also tells the user
 * how to fix it.
 */
class TimeAlarmHandler(
    private val repository: FlowRepository,
    /** Chains the next occurrence; [TimeTriggerScheduler.scheduleNext] in the app. */
    private val scheduleNext: (flowId: String, triggerId: String, config: Map<String, String>) -> Unit,
    /** The master switch. */
    private val serviceEnabled: () -> Boolean,
    /** Starts the run; false when Android would not let the service start. */
    private val startRun: (flowId: String, variables: Map<String, String>, triggerId: String) -> Boolean,
    /** Tells the user a scheduled run was blocked, and how to let it through. */
    private val notifyBlocked: (flow: Flow, time: String) -> Unit,
    private val messages: Messages,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The run-log lines, resolved from string resources by the caller. */
    data class Messages(val skippedServiceOff: String, val blockedInBackground: String)

    suspend fun onAlarm(flowId: String, triggerId: String) {
        val flow = repository.getById(flowId)
        val trigger = flow?.triggers?.firstOrNull { it.id == triggerId && it.type == TriggerType.TIME }
        // Flow deleted/disabled or trigger removed since the alarm was set: drop it, and do not
        // chain — a sync puts the alarm back if the flow is turned on again.
        if (flow == null || !flow.enabled || trigger == null) return
        val time = trigger.config["time"].orEmpty()

        if (!serviceEnabled()) {
            // Skipped on purpose — an alarm-driven run is automation, and automation is off —
            // but said in the log, so "it didn't run at nine" has an answer.
            record(flowId, ExecutionStatus.SKIPPED, messages.skippedServiceOff)
        } else {
            val started = startRun(
                flowId,
                mapOf(TriggerVariables.TYPE to TriggerType.TIME.name, TriggerVariables.TIME to time),
                triggerId,
            )
            if (!started) {
                record(flowId, ExecutionStatus.FAIL, messages.blockedInBackground)
                notifyBlocked(flow, time)
            }
        }

        // Keep chaining either way, so turning automation back on (or granting the permission)
        // resumes the schedule without the flow having to be edited.
        if (trigger.config["repeat"] != "ONCE") scheduleNext(flowId, triggerId, trigger.config)
    }

    private suspend fun record(flowId: String, status: ExecutionStatus, reason: String) {
        repository.saveExecutionLog(
            ExecutionLog(
                id = UUID.randomUUID().toString(),
                flowId = flowId,
                triggeredAt = now(),
                status = status,
                errorMessage = reason,
                executionDurationMs = 0,
            ),
        )
    }
}
