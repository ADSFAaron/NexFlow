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
package com.nexflow.executor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexflow.R
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Posts a notification, optionally with somewhere to go when it is tapped (`tap_action`).
 *
 * The tap target exists for the "and then, whenever you get round to it, do this" case: the flow
 * leaves a notification, and tapping it hands the user straight to the app, page or shortcut where
 * the next step happens — no time trigger needed, because the user's tap is the trigger.
 */
class NotificationActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.NOTIFICATION

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.Failure("Notification permission not granted — go to Settings → Permissions to enable it")
        }

        val title = action.config["title"]?.takeIf { it.isNotBlank() } ?: "NexFlow"
        val message = action.config["message"]?.trim() ?: ""

        // Unique per post, so several outstanding notifications don't overwrite each other and
        // each keeps its own tap target.
        val notificationId = System.currentTimeMillis().toInt()
        val tapTarget = resolveTapTarget(action)

        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply {
                if (message.isNotBlank()) {
                    setContentText(message)
                    setStyle(NotificationCompat.BigTextStyle().bigText(message))
                }
                (tapTarget as? TapTarget.Go)?.let { setContentIntent(pendingIntentFor(it.intent, notificationId)) }
            }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)

        // Posted either way — a broken tap target is no reason to withhold the reminder itself.
        // The run is still reported as failed so the log says why tapping does nothing.
        return when (tapTarget) {
            is TapTarget.Broken -> ActionResult.Failure(tapTarget.message)
            else -> ActionResult.Success
        }
    }

    private sealed interface TapTarget {
        /** No tap action configured: tapping only dismisses the notification. */
        data object None : TapTarget

        /** Somewhere to send the user. */
        data class Go(val intent: Intent) : TapTarget

        /** Configured, but the target no longer resolves (app uninstalled, blank URL, …). */
        data class Broken(val message: String) : TapTarget
    }

    private fun pendingIntentFor(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun resolveTapTarget(action: Action): TapTarget {
        val intent: Intent = when (action.config["tap_action"]?.trim()?.uppercase() ?: TAP_NONE) {
            TAP_OPEN_APP -> {
                val pkg = action.config["tap_package"]?.takeIf { it.isNotBlank() }
                    ?: return TapTarget.Broken("Notification tap: no app selected")
                context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return TapTarget.Broken("Notification tap: app not found: $pkg")
            }
            TAP_OPEN_URL -> {
                val url = action.config["tap_url"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return TapTarget.Broken("Notification tap: no URL set")
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            TAP_OPEN_SHORTCUT -> {
                val uri = action.config["tap_shortcut_uri"]?.takeIf { it.isNotBlank() }
                    ?: return TapTarget.Broken("Notification tap: no shortcut selected")
                // The shortcut's app may have been uninstalled or changed since it was picked.
                runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                    ?: return TapTarget.Broken("Notification tap: invalid shortcut")
            }
            else -> return TapTarget.None
        }
        // Launched from a notification, so there is no task of ours to run in.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return TapTarget.Go(intent)
    }

    companion object {
        const val CHANNEL_ACTIONS = "nexflow_actions"

        /** `tap_action` config values — what tapping the notification does. */
        const val TAP_NONE = "NONE"
        const val TAP_OPEN_APP = "OPEN_APP"
        const val TAP_OPEN_URL = "OPEN_URL"
        const val TAP_OPEN_SHORTCUT = "OPEN_SHORTCUT"
    }
}
