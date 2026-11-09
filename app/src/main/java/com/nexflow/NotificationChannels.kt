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
package com.nexflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.nexflow.executor.NotificationActionExecutor.Companion.CHANNEL_ACTIONS
import com.nexflow.service.FlowExecutionService.Companion.CHANNEL_SERVICE
import com.nexflow.service.RateLimitNotifier.Companion.CHANNEL_ALERTS

/**
 * The app's notification channels. Created when the app starts, and on their own by the
 * instrumented tests: those run under HiltTestApplication, which never runs NexFlowApplication's
 * onCreate, and without the service channel Android refuses the execution service's
 * foreground notification ("Bad notification for startForeground") and kills the process.
 * Creating a channel that exists only updates its name and description.
 */
object NotificationChannels {

    fun create(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "NexFlow Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Persistent notification while automation is running" },
                NotificationChannel(
                    CHANNEL_ACTIONS,
                    "Flow Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Notifications triggered by your automations" },
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.channel_alerts_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_alerts_description) },
            ),
        )
    }
}
