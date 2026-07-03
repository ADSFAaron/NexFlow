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
package com.nexflow.prefs

/**
 * Persistent master switch for the automation service. This is the single source of
 * truth for whether automation should run:
 *
 *  - The service capsule (Flows top bar) and the notification Stop action write it.
 *  - MainActivity only auto-starts the service when it is on.
 *  - BootReceiver only resumes the service after boot when it (and auto-start) is on.
 *  - TimeAlarmReceiver skips firing TIME flows while it is off (alarms keep chaining,
 *    so switching back on resumes the schedule).
 *  - Manual runs (widget / QS tile / play buttons) are always allowed; when the switch
 *    is off the service runs the single flow and stops itself again.
 */
import android.content.Context

object ServiceEnabledPrefs {
    private const val PREFS_NAME = "nexflow_settings"
    private const val KEY_SERVICE_ENABLED = "automation_service_enabled"

    fun get(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)

    fun set(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
    }
}
