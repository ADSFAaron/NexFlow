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
import androidx.annotation.StringRes
import com.nexflow.R

enum class LogRetentionOption(
    @param:StringRes val displayNameRes: Int,
    val days: Int,
    val maxCount: Int,
) {
    LIGHT(R.string.log_retention_light, 7, 100),
    STANDARD(R.string.log_retention_standard, 30, 200),
    FULL(R.string.log_retention_full, 90, 500),
    EXTENDED(R.string.log_retention_extended, 90, 1000),
}

object LogRetentionPrefs {
    private const val PREFS_NAME = "nexflow_settings"
    private const val KEY = "log_retention"

    fun get(context: Context): LogRetentionOption {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, LogRetentionOption.STANDARD.name)
        return LogRetentionOption.entries.find { it.name == name } ?: LogRetentionOption.STANDARD
    }

    fun set(context: Context, option: LogRetentionOption) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, option.name)
            .apply()
    }
}
