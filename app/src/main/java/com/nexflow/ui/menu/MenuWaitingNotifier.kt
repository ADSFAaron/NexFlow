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
package com.nexflow.ui.menu

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexflow.R
import com.nexflow.service.RateLimitNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The way to a menu the system would not let NexFlow open by itself.
 *
 * A flow started by a background trigger has no window, and starting [MenuPickerActivity] from
 * there is a background activity launch — refused, without an exception or a log line the app
 * can see, unless something exempts it (the accessibility service being on does). The menu would
 * then wait unseen until it timed out. A notification is the documented way round: an activity
 * started from a notification tap is always allowed.
 */
@Singleton
class MenuWaitingNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun show(menuTitle: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Nothing to show it with; the menu still times out and the log says so.
            return
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MenuPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = menuTitle.ifBlank { context.getString(R.string.menu_waiting_notification_text) }
        val notification = NotificationCompat.Builder(context, RateLimitNotifier.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.menu_waiting_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager().notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
    }

    /** The menu is over — answered, dismissed or timed out — so the way to it goes too. */
    fun clear() {
        manager().cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    private fun manager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        /** Tagged, so a flow's own NOTIFICATION action can never land on the same slot. */
        const val NOTIFICATION_TAG = "menu_waiting"

        /** One slot: menus are shown one at a time, so only one can be waiting unseen. */
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE = 0x4E4D
    }
}
