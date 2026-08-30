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

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.trigger.TimeTriggerScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Upgrades already-scheduled TIME alarms the moment the user grants "Alarms & reminders".
 *
 * Alarms are scheduled once, with whatever precision was available at the time. Without this,
 * a user who grants the permission after seeing the flow's warning would keep the inexact
 * alarms they already have — still minutes late — until they happened to edit the flow or
 * reboot. Re-running [TimeTriggerScheduler.sync] replaces each one with an exact alarm.
 *
 * The system does not broadcast on revocation (it cancels the exact alarms itself), so this
 * only ever runs in the granting direction.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: FlowRepository
    @Inject lateinit var timeTriggerScheduler: TimeTriggerScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        // Re-check rather than trust the broadcast: the docs require it, because the user can
        // revoke again before this arrives. sync() would then just re-write inexact alarms.
        if (!timeTriggerScheduler.canScheduleExact()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                timeTriggerScheduler.sync(repository.observeEnabled().first())
            } finally {
                pending.finish()
            }
        }
    }
}
