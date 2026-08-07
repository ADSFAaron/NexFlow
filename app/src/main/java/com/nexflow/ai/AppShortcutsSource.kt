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
import com.nexflow.shortcut.AppShortcutQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Answers Gemini's `search_app_shortcuts` calls locally, over the same
 * [AppShortcutQuery] the manual [com.nexflow.ui.common.ShortcutPickerDialog] uses — so the AI
 * offers exactly the shortcuts a user would find by hand, serialized the same way
 * ([Intent.toUri] with [Intent.URI_INTENT_SCHEME]) so [com.nexflow.executor.LaunchShortcutActionExecutor]
 * can parse it back.
 *
 * The picker's second source, ACTION_CREATE_SHORTCUT activities, is deliberately *not* offered:
 * those hand back an intent only after the target app's own UI runs, so there is nothing the AI
 * could fill in. They are reported as a count instead, for the model to mention.
 */
@Singleton
class AppShortcutsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    data class ShortcutEntry(val label: String, val intentUri: String)

    data class Result(
        val shortcuts: List<ShortcutEntry>,
        /** Static shortcuts whose target activity is not exported — unreachable from NexFlow. */
        val notLaunchableCount: Int,
        /** Shortcuts the target app only builds interactively; the user must pick them in the editor. */
        val configurableCount: Int,
    )

    suspend fun shortcutsFor(packageName: String): Result = withContext(Dispatchers.IO) {
        val static = AppShortcutQuery.staticShortcuts(context, packageName)
        Result(
            shortcuts = static.launchable.map {
                ShortcutEntry(label = it.label, intentUri = it.intent.toUri(Intent.URI_INTENT_SCHEME))
            },
            notLaunchableCount = static.notLaunchableCount,
            configurableCount = AppShortcutQuery.configurableShortcutSources(context, packageName).size,
        )
    }
}
