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
package com.nexflow.ai

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers Gemini's `search_installed_apps` calls locally. Same launcher-intent query as
 * AppPickerDialog, so the AI sees exactly the apps the manual picker shows (covered by the
 * manifest `<queries>` filter).
 */
@Singleton
class InstalledAppsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    data class AppEntry(val label: String, val packageName: String)

    fun search(query: String, limit: Int = 20): List<AppEntry> {
        val pm = context.packageManager
        val launchablePackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_LAUNCHER) },
            0,
        ).map { it.activityInfo.packageName }.toSet()

        val trimmed = query.trim()
        return launchablePackages
            .mapNotNull { pkg ->
                runCatching {
                    AppEntry(
                        label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                        packageName = pkg,
                    )
                }.getOrNull()
            }
            .filter {
                trimmed.isEmpty() ||
                    it.label.contains(trimmed, ignoreCase = true) ||
                    it.packageName.contains(trimmed, ignoreCase = true)
            }
            .sortedBy { it.label.lowercase() }
            .take(limit)
    }
}
