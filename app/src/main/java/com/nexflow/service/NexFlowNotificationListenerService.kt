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

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nexflow.event.NotificationEvent
import com.nexflow.event.NotificationEventSource

/**
 * Receives all posted notifications via the Android Notification Listener API.
 * User must grant access in Settings → Notifications → Notification access.
 */
class NexFlowNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        // Skip our own notifications to avoid feedback loops
        if (pkg == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        NotificationEventSource.emit(
            NotificationEvent(
                packageName = pkg,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
