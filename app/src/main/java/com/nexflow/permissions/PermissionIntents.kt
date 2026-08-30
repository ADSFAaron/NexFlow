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
package com.nexflow.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.nexflow.service.NexFlowAccessibilityService
import com.nexflow.service.NexFlowNotificationListenerService

/**
 * Builds the system-settings intent that takes the user to the exact toggle for a
 * [SpecialAccess], and — where the platform supports it — scrolls to and flashes that
 * entry so they know which switch to enable among a long list.
 */
object PermissionIntents {

    // Semi-official AOSP Settings "highlight" extras. When honoured, Settings scrolls to the
    // keyed preference and flashes it ~3 times. ROMs that don't recognise them simply ignore
    // the extras and still open the correct page, so this degrades gracefully.
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    /**
     * @return the intent to launch, or `null` when there is no system UI to send the user to
     *   (e.g. [SpecialAccess.WRITE_SECURE_SETTINGS], which is ADB-only).
     */
    fun forSpecial(context: Context, special: SpecialAccess): Intent? = when (special) {
        SpecialAccess.ACCESSIBILITY -> {
            val service = ComponentName(context, NexFlowAccessibilityService::class.java)
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).highlight(service.flattenToString())
        }
        SpecialAccess.NOTIFICATION_LISTENER -> {
            // Official deep link straight to this listener's on/off detail page (API 30+).
            val listener = ComponentName(context, NexFlowNotificationListenerService::class.java)
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        }
        SpecialAccess.DND ->
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).highlight(context.packageName)
        SpecialAccess.WRITE_SETTINGS ->
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, appUri(context))
        SpecialAccess.BACKGROUND_LOCATION ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))
        SpecialAccess.WRITE_SECURE_SETTINGS -> null
        // Official deep link to this app's "Alarms & reminders" toggle. The page (and the
        // constant) only exist on API 31+; below that SCHEDULE_EXACT_ALARM is granted at
        // install, so the checker never reports it and this branch is unreachable anyway.
        SpecialAccess.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri(context))
            } else {
                null
            }
    }

    /**
     * The app's own details page. Used as the fallback for a runtime permission the user has
     * permanently denied ("Don't ask again"), where the runtime dialog will no longer appear.
     */
    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))

    private fun appUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    private fun Intent.highlight(key: String): Intent {
        putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
        putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) })
        return this
    }
}
