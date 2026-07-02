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

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Sets the home screen and/or lock screen wallpaper from a picked image.
 *
 * SET_WALLPAPER is a normal (install-time) permission, so this runs fine from the
 * background flow engine — no runtime dialog and no foreground requirement. We only
 * *write* the wallpaper: reading/backing up the current one is deliberately avoided
 * (it needs storage permission and can't capture live wallpapers). To "restore" a
 * wallpaper, add a second flow — e.g. Geofence EXIT — that sets it back to another image.
 */
class SetWallpaperActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.SET_WALLPAPER

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val source = action.config["image"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure("Set wallpaper: no image selected")

        val which = when (action.config["target"]) {
            "LOCK" -> WallpaperManager.FLAG_LOCK
            "HOME" -> WallpaperManager.FLAG_SYSTEM
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val wm = WallpaperManager.getInstance(context)
                // setStream applies the same image to every requested category in one call,
                // preserving the original bytes (no re-encode via Bitmap).
                openStream(source).use { input ->
                    wm.setStream(input, /* visibleCropHint = */ null, /* allowBackup = */ true, which)
                }
                ActionResult.Success
            }.getOrElse { ActionResult.Failure("Set wallpaper failed: ${it.message}") }
        }
    }

    private fun openStream(source: String) =
        if (source.startsWith("content://")) {
            // Legacy/imported flows may still hold a content URI whose source file the
            // user could have moved or deleted — surface that clearly instead of NPE-ing.
            context.contentResolver.openInputStream(Uri.parse(source))
                ?: throw IllegalStateException("Wallpaper image is no longer available")
        } else {
            val file = File(source)
            if (!file.exists()) {
                throw IllegalStateException("Wallpaper image no longer exists — please re-select it")
            }
            file.inputStream()
        }
}
