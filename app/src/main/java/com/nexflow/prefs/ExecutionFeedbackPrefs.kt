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

import android.content.Context

/**
 * Whether flow executions announce themselves with a Toast. Default ON — background
 * triggers (screenshot, taps, ...) firing silently several times surprised users more
 * than the Toast ever could.
 */
object ExecutionFeedbackPrefs {
    private const val PREFS_NAME = "nexflow_settings"
    private const val KEY_TOAST = "execution_toast_enabled"

    fun isToastEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOAST, true)

    fun setToastEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOAST, enabled)
            .apply()
    }
}
