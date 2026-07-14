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
package com.nexflow.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * A static (manifest) shortcut parsed from another app's shortcuts.xml.
 * Only manifest shortcuts are reachable this way: dynamic and pinned shortcuts
 * live in the system's ShortcutService, which only the default launcher may query.
 */
data class StaticShortcut(
    val id: String,
    val label: String,
    val icon: Drawable?,
    val intent: Intent,
)

/**
 * Static shortcuts of one app, split into what NexFlow can actually launch and a count
 * of those it can't. Launchers start shortcut targets through the system's
 * LauncherApps.startShortcut(), so apps routinely keep them non-exported (Google Maps
 * does); a plain startActivity() on those throws SecurityException, and offering them
 * in the picker would only produce flows that always fail.
 */
data class StaticShortcutsResult(
    val launchable: List<StaticShortcut>,
    val notLaunchableCount: Int,
)

/** An activity that offers user-configured shortcuts via ACTION_CREATE_SHORTCUT. */
data class ConfigurableShortcutSource(
    val label: String,
    val icon: Drawable?,
    val launchIntent: Intent,
)

object AppShortcutQuery {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val META_DATA_APP_SHORTCUTS = "android.app.shortcuts"

    /**
     * Parses the manifest shortcuts of [packageName], mirroring what the launcher
     * shows on long-press (minus dynamic shortcuts, which are not accessible).
     *
     * A shortcut may declare several <intent> elements forming a back stack; only the
     * last one (the activity the user actually lands on) is kept, matching what
     * launching the shortcut feels like from the home screen.
     */
    fun staticShortcuts(context: Context, packageName: String): StaticShortcutsResult {
        val pm = context.packageManager
        val launcherActivities = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
            PackageManager.GET_META_DATA,
        )
        val res = runCatching { pm.getResourcesForApplication(packageName) }.getOrNull()
            ?: return StaticShortcutsResult(emptyList(), 0)

        val all = launcherActivities.flatMap { resolveInfo ->
            runCatching {
                val parser = resolveInfo.activityInfo.loadXmlMetaData(pm, META_DATA_APP_SHORTCUTS)
                    ?: return@runCatching emptyList()
                // XmlResourceParser only became AutoCloseable in API 31; close manually for minSdk 30.
                try {
                    parseShortcutsXml(res, parser, packageName)
                } finally {
                    parser.close()
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.id }

        val (launchable, blocked) = all.partition { isLaunchable(pm, it.intent) }
        return StaticShortcutsResult(launchable, blocked.size)
    }

    /** Whether a plain startActivity() from NexFlow can reach the intent's target. */
    private fun isLaunchable(pm: PackageManager, intent: Intent): Boolean {
        val component = intent.component
        return if (component != null) {
            runCatching { pm.getActivityInfo(component, 0).exported }.getOrDefault(false)
        } else {
            pm.resolveActivity(intent, 0)?.activityInfo?.exported == true
        }
    }

    private fun parseShortcutsXml(
        res: Resources,
        parser: XmlPullParser,
        packageName: String,
    ): List<StaticShortcut> {
        val attrs = Xml.asAttributeSet(parser)
        val result = mutableListOf<StaticShortcut>()

        var id: String? = null
        var label: String? = null
        var icon: Drawable? = null
        var enabled = true
        var lastIntent: Intent? = null

        fun flush() {
            val intent = lastIntent
            if (enabled && id != null && intent != null) {
                result += StaticShortcut(id!!, label ?: id!!, icon, intent)
            }
            id = null; label = null; icon = null; enabled = true; lastIntent = null
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when {
                event == XmlPullParser.START_TAG && parser.name == "shortcut" -> {
                    flush()
                    id = attrs.getAttributeValue(ANDROID_NS, "shortcutId")
                    enabled = attrs.getAttributeBooleanValue(ANDROID_NS, "enabled", true)
                    label = attrs.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0)
                        .takeIf { it != 0 }
                        ?.let { runCatching { res.getString(it) }.getOrNull() }
                    icon = attrs.getAttributeResourceValue(ANDROID_NS, "icon", 0)
                        .takeIf { it != 0 }
                        ?.let { runCatching { res.getDrawable(it, null) }.getOrNull() }
                }
                event == XmlPullParser.START_TAG && parser.name == "intent" -> {
                    lastIntent = runCatching { Intent.parseIntent(res, parser, attrs) }.getOrNull()
                        ?.also { if (it.`package` == null && it.component == null) it.`package` = packageName }
                        ?: lastIntent
                }
            }
            event = parser.next()
        }
        flush()
        return result
    }

    /**
     * Activities of [packageName] handling ACTION_CREATE_SHORTCUT — the mechanism behind
     * launcher "add shortcut" pickers (and KWGT's shortcut option). Launching one opens the
     * app's own configuration UI, which returns a ready-to-launch intent.
     */
    fun configurableShortcutSources(
        context: Context,
        packageName: String,
    ): List<ConfigurableShortcutSource> {
        val pm = context.packageManager
        return pm.queryIntentActivities(
            Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(packageName),
            0,
        ).map { resolveInfo ->
            ConfigurableShortcutSource(
                label = resolveInfo.loadLabel(pm).toString(),
                icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull(),
                launchIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).setClassName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name,
                ),
            )
        }
    }
}
