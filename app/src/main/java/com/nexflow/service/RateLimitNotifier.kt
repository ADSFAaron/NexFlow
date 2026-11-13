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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.core.automation.interpreter.RateLimitCategory
import com.nexflow.core.automation.model.Flow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user that a flow hit an action limit, at most once per flow per hour.
 *
 * Without the notice a skipped SMS looks like a flow that silently stopped working; without the
 * throttle a flow stuck in a loop would replace one flood with another.
 */
@Singleton
class RateLimitNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lastNotified = HashMap<String, Long>()

    @Synchronized
    fun onLimited(flow: Flow, category: RateLimitCategory, nowMillis: Long = System.currentTimeMillis()) {
        val last = lastNotified[flow.id]
        if (last != null && nowMillis - last < THROTTLE_MS) return
        lastNotified[flow.id] = nowMillis

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // The skipped steps are still in the run log; the notice is a convenience on top.
            return
        }
        val text = context.resources.getQuantityString(
            when (category) {
                RateLimitCategory.SMS -> R.plurals.rate_limit_notification_sms
                RateLimitCategory.CALL -> R.plurals.rate_limit_notification_call
                RateLimitCategory.HTTP -> R.plurals.rate_limit_notification_http
            },
            category.perMinute,
            category.perMinute,
            flow.name,
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.rate_limit_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // One slot per flow: a later notice for the same flow replaces the earlier one.
        nm.notify(NOTIFICATION_TAG, flow.id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ALERTS = "nexflow_alerts"
        private const val NOTIFICATION_TAG = "rate_limit"
        private const val THROTTLE_MS = 60 * 60 * 1000L
    }
}
