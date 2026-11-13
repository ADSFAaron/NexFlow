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

/**
 * Puts automation back after something outside the app stopped it: a reboot, or an update of the
 * app itself.
 *
 * An update kills the process like a reboot does, but nothing used to bring the engine back
 * afterwards — every Play auto-update left Wi-Fi, battery, headset and sensor flows silently dead
 * until the user happened to open NexFlow. [Intent.ACTION_MY_PACKAGE_REPLACED] is delivered to
 * the newly installed version and, like BOOT_COMPLETED, exempts the receiver from the background
 * foreground-service start restriction.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var bootTriggerHandler: BootTriggerHandler
    @Inject lateinit var repository: FlowRepository
    @Inject lateinit var timeTriggerScheduler: TimeTriggerScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) return
        // "Device started" flows are about the device, not about NexFlow being updated.
        if (action == Intent.ACTION_BOOT_COMPLETED) bootTriggerHandler.notifyBoot()

        // AlarmManager clears all alarms on reboot, so TIME triggers must be re-registered here
        // regardless of the auto-start preference (the alarm itself, not a running service, is
        // what later wakes the app). After an update the alarms normally survive; syncing again
        // costs nothing and covers a scheduler change between versions.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                timeTriggerScheduler.sync(repository.observeEnabled().first())
            } finally {
                pending.finish()
            }
        }

        if (shouldResumeService(action, AutoStartPrefs.get(context), ServiceEnabledPrefs.get(context))) {
            FlowExecutionService.start(context)
        }
    }

    companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        /**
         * Never against the master switch: a reboot or update must not override an explicit stop.
         * After a reboot the service also waits for the auto-start preference, which is about
         * boot. An update did not reboot anything — it only killed a service the switch says
         * should be running — so it comes straight back.
         */
        fun shouldResumeService(action: String?, autoStart: Boolean, serviceEnabled: Boolean): Boolean =
            when (action) {
                Intent.ACTION_BOOT_COMPLETED -> autoStart && serviceEnabled
                Intent.ACTION_MY_PACKAGE_REPLACED -> serviceEnabled
                else -> false
            }
    }
}
