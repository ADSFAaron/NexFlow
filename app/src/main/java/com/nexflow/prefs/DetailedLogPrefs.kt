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
import androidx.core.content.edit

/**
 * Whether each run's log also records what every action's settings resolved to after variable
 * substitution — the URL actually called, the message actually sent, the value a variable landed on.
 *
 * Default OFF, and deliberately not the kind of default that gets flipped later: those resolved
 * values are the run's real data. A flow that sends an API key in a header, reads an SMS or builds
 * a message from a notification would be writing all of it into the database, where the user did
 * not put it and would not think to look for it. The per-action pass/fail log — which is what
 * makes a failure diagnosable at all — is always recorded and does not depend on this.
 */
object DetailedLogPrefs {
    private const val PREFS_NAME = "nexflow_settings"
    private const val KEY_DETAILED = "detailed_execution_log"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DETAILED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_DETAILED, enabled) }
    }
}
