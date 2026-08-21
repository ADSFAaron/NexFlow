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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Answers Gemini's `search_installed_apps` calls locally. Same launcher-intent query as
 * AppPickerDialog, so the AI sees exactly the apps the manual picker shows (covered by the
 * manifest `<queries>` filter).
 *
 * A bare label plus a package name only identifies an app the model already recognises from
 * pre-training; for a regional, in-house or oddly named app it is an opaque string. So every
 * entry also carries what the device itself knows about the app's purpose — see [AppEntry].
 */
@Singleton
class InstalledAppsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * @param category canonical `ApplicationInfo.CATEGORY_*` name, or null when neither the app's
     *   manifest nor the installer supplied one.
     * @param roles the `Intent.CATEGORY_APP_*` roles the app claims, e.g. `["MUSIC"]`. Empty for
     *   most apps, but never wrong when present: the app declared it about itself.
     * @param description the app's own `android:description`, when it declares one.
     */
    data class AppEntry(
        val label: String,
        val packageName: String,
        val category: String? = null,
        val roles: List<String> = emptyList(),
        val description: String? = null,
    )

    /** Kept before [AppEntry] so the costly per-app description load happens after [search] cuts. */
    private class Candidate(
        val label: String,
        val info: ApplicationInfo,
        val category: String?,
        val roles: List<String>,
    ) {
        fun matchesName(query: String): Boolean =
            label.contains(query, ignoreCase = true) || info.packageName.contains(query, ignoreCase = true)

        /** Lets the model resolve "a music app" by searching the kind instead of guessing names. */
        fun matchesPurpose(query: String): Boolean =
            category?.contains(query, ignoreCase = true) == true ||
                roles.any { it.contains(query, ignoreCase = true) }
    }

    /**
     * Off the main thread, like [AppShortcutsSource.shortcutsFor]: this enumerates every
     * launchable activity on the device and then reads a label per package, each of which opens
     * that app's resources. On a phone with a couple of hundred apps it is hundreds of
     * milliseconds — a visible freeze if it runs where the UI lives.
     */
    suspend fun search(query: String, limit: Int = 20): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launchablePackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).also { it.addCategory(Intent.CATEGORY_LAUNCHER) },
            0,
        ).map { it.activityInfo.packageName }.toSet()
        val rolesByPackage = rolesByPackage(pm)

        val trimmed = query.trim()
        val candidates = launchablePackages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                Candidate(
                    label = pm.getApplicationLabel(info).toString(),
                    info = info,
                    category = CATEGORY_NAMES[info.category],
                    roles = rolesByPackage[pkg].orEmpty(),
                )
            }.getOrNull()
        }

        // Name matches rank first and keep their old ordering: searching "spotify" must not be
        // crowded out of the limit by every other app that happens to be in the AUDIO category.
        val byName = candidates.filter { trimmed.isEmpty() || it.matchesName(trimmed) }
        val named = byName.map { it.info.packageName }.toSet()
        val byPurpose = when {
            trimmed.isEmpty() -> emptyList()
            else -> candidates.filter { it.info.packageName !in named && it.matchesPurpose(trimmed) }
        }

        (byName.sortedBy { it.label.lowercase() } + byPurpose.sortedBy { it.label.lowercase() })
            .take(limit)
            .map { it.toEntry(pm) }
    }

    private fun Candidate.toEntry(pm: PackageManager) = AppEntry(
        label = label,
        packageName = info.packageName,
        category = category,
        roles = roles,
        // Loading this opens the app's resources, so it is done only for entries that survived
        // the limit. Most apps declare no description and this is simply null.
        description = runCatching { info.loadDescription(pm)?.toString() }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(DESCRIPTION_LIMIT),
    )

    /**
     * package name → the [Intent] app roles it claims. An app whose launcher activity declares
     * `CATEGORY_APP_MUSIC` is stating "I am a music player" in its own manifest, which beats
     * [ApplicationInfo.category] in one important way: it does not depend on the installer having
     * filled in a category hint.
     *
     * These packages are all already visible through the manifest's launcher `<queries>` filter,
     * and package visibility is per-package rather than per-query, so nothing here is filtered out.
     */
    private fun rolesByPackage(pm: PackageManager): Map<String, List<String>> {
        val roles = mutableMapOf<String, MutableList<String>>()
        APP_ROLES.forEach { (role, category) ->
            val intent = Intent(Intent.ACTION_MAIN).also { it.addCategory(category) }
            runCatching { pm.queryIntentActivities(intent, 0) }.getOrNull()?.forEach {
                roles.getOrPut(it.activityInfo.packageName) { mutableListOf() }.add(role)
            }
        }
        return roles
    }

    private companion object {
        /** Long enough for a real one-line purpose, short enough not to bloat the prompt. */
        const val DESCRIPTION_LIMIT = 180

        /**
         * Canonical names rather than the localized `ApplicationInfo.getCategoryTitle`, so the
         * model always sees one stable vocabulary and the mapping stays unit-testable.
         */
        val CATEGORY_NAMES: Map<Int, String> = buildMap {
            put(ApplicationInfo.CATEGORY_GAME, "GAME")
            put(ApplicationInfo.CATEGORY_AUDIO, "AUDIO")
            put(ApplicationInfo.CATEGORY_VIDEO, "VIDEO")
            put(ApplicationInfo.CATEGORY_IMAGE, "IMAGE")
            put(ApplicationInfo.CATEGORY_SOCIAL, "SOCIAL")
            put(ApplicationInfo.CATEGORY_NEWS, "NEWS")
            put(ApplicationInfo.CATEGORY_MAPS, "MAPS")
            put(ApplicationInfo.CATEGORY_PRODUCTIVITY, "PRODUCTIVITY")
            // API 31, and minSdk is 30 — an older platform cannot parse this appCategory value,
            // so on those devices no app ever reports it and leaving it out changes nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                put(ApplicationInfo.CATEGORY_ACCESSIBILITY, "ACCESSIBILITY")
            }
        }

        /** Role name → the `Intent.CATEGORY_APP_*` an app declares to claim that role. */
        val APP_ROLES: Map<String, String> = buildMap {
            put("BROWSER", Intent.CATEGORY_APP_BROWSER)
            put("CALCULATOR", Intent.CATEGORY_APP_CALCULATOR)
            put("CALENDAR", Intent.CATEGORY_APP_CALENDAR)
            put("CONTACTS", Intent.CATEGORY_APP_CONTACTS)
            put("EMAIL", Intent.CATEGORY_APP_EMAIL)
            put("FILES", Intent.CATEGORY_APP_FILES)
            put("GALLERY", Intent.CATEGORY_APP_GALLERY)
            put("MAPS", Intent.CATEGORY_APP_MAPS)
            put("MARKET", Intent.CATEGORY_APP_MARKET)
            put("MESSAGING", Intent.CATEGORY_APP_MESSAGING)
            put("MUSIC", Intent.CATEGORY_APP_MUSIC)
            // Both API 33; see the CATEGORY_ACCESSIBILITY note above.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                put("WEATHER", Intent.CATEGORY_APP_WEATHER)
                put("FITNESS", Intent.CATEGORY_APP_FITNESS)
            }
        }
    }
}
