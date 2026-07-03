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
import com.nexflow.prefs.AutoStartPrefs
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.service.FlowExecutionService
import com.nexflow.trigger.BootTriggerHandler
import com.nexflow.trigger.TimeTriggerScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var bootTriggerHandler: BootTriggerHandler
    @Inject lateinit var repository: FlowRepository
    @Inject lateinit var timeTriggerScheduler: TimeTriggerScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        bootTriggerHandler.notifyBoot()

        // AlarmManager clears all alarms on reboot, so TIME triggers must be re-registered here
        // regardless of the auto-start preference (the alarm itself, not a running service, is
        // what later wakes the app). BOOT_COMPLETED exempts this from background-start limits.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                timeTriggerScheduler.sync(repository.observeEnabled().first())
            } finally {
                pending.finish()
            }
        }

        // Resume the service only when auto-start is on AND the master switch wasn't
        // turned off by the user — a reboot must not override an explicit stop.
        if (AutoStartPrefs.get(context) && ServiceEnabledPrefs.get(context)) {
            FlowExecutionService.start(context)
        }
    }
}
