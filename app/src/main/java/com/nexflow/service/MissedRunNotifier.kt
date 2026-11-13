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

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.trigger.TimeTriggerScheduler

/**
 * Tells the user a scheduled flow did not run because Android would not let NexFlow start in
 * the background, and takes them to the one switch that fixes it.
 *
 * That refusal only happens for an inexact alarm — an exact one exempts the app — so the tap
 * goes to "Alarms & reminders" while that is still off; with it on, it just opens the app.
 * The run log has the same line, but nobody reads the log of a flow they believe ran.
 */
class MissedRunNotifier(private val context: Context) {

    fun notifyBlocked(flow: Flow, time: String, canScheduleExact: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // the run log still says why
        }
        // Below API 31 exact alarms cannot be refused (canScheduleExact is always true there) and
        // the grant screen does not exist; the version check says so where lint can see it.
        val target = if (canScheduleExact || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Intent(context, MainActivity::class.java)
        } else {
            TimeTriggerScheduler.requestExactAlarmPermissionIntent(context.packageName)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(
            context, flow.id.hashCode(), target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.time_missed_text)
        val notification = NotificationCompat.Builder(context, RateLimitNotifier.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.time_missed_title, flow.name, time))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // One slot per flow: tomorrow's miss replaces today's rather than piling up.
        nm.notify(NOTIFICATION_TAG, flow.id.hashCode(), notification)
    }

    private companion object {
        const val NOTIFICATION_TAG = "time_missed"
    }
}
