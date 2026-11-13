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
package com.nexflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.R
import com.nexflow.service.FlowExecutionService
import com.nexflow.service.MissedRunNotifier
import com.nexflow.trigger.TimeAlarmHandler
import com.nexflow.trigger.TimeTriggerScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by [AlarmManager][android.app.AlarmManager] when a TIME trigger is due. Runs the flow
 * and chains the next occurrence. Survives process death — when this fires the OS recreates the
 * app process if needed.
 */
class TimeAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TimeAlarmEntryPoint {
        fun repository(): FlowRepository
        fun scheduler(): TimeTriggerScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val flowId = intent.getStringExtra(EXTRA_FLOW_ID) ?: return
        val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID) ?: return

        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext, TimeAlarmEntryPoint::class.java,
        )
        val handler = TimeAlarmHandler(
            repository = entry.repository(),
            scheduleNext = entry.scheduler()::scheduleNext,
            serviceEnabled = { ServiceEnabledPrefs.get(context) },
            startRun = { id, variables, trigger ->
                // Carries the trigger id so a TIME trigger counts towards a flow's ALL
                // combination like any in-process trigger.
                FlowExecutionService.runFlow(context, id, variables, trigger)
            },
            notifyBlocked = { flow, time ->
                MissedRunNotifier(context).notifyBlocked(flow, time, entry.scheduler().canScheduleExact())
            },
            messages = TimeAlarmHandler.Messages(
                skippedServiceOff = context.getString(R.string.time_skipped_service_off),
                blockedInBackground = context.getString(R.string.time_blocked_background),
            ),
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handler.onAlarm(flowId, triggerId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.nexflow.action.TIME_ALARM_FIRE"
        const val EXTRA_FLOW_ID = "flow_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"
    }
}
