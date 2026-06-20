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

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class WriteFileActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.WRITE_FILE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val path = action.config["path"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure("Write file: no path configured")
        val content = action.config["content"] ?: ""

        // Security: reject path-traversal segments. Combined with flow import, an unsanitised
        // path could otherwise escape its intended directory and overwrite app-private files.
        if (path.split('/').any { it == ".." }) {
            return ActionResult.Failure("Write file: path must not contain '..'")
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                if (path.startsWith("/storage/emulated/0/") || path.startsWith("/sdcard/")) {
                    // Use MediaStore for scoped storage on Android 10+
                    val relativePath = path
                        .removePrefix("/storage/emulated/0/")
                        .removePrefix("/sdcard/")
                    val fileName = relativePath.substringAfterLast('/')
                    val dir = relativePath.substringBeforeLast('/', "")
                    val mediaDir = if (dir.isBlank()) Environment.DIRECTORY_DOCUMENTS else dir

                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, mediaDir)
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Files.getContentUri("external"), values,
                    ) ?: throw IllegalStateException("Could not create file via MediaStore")
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        out.write(content.toByteArray())
                    }
                } else {
                    // App-internal or data-dir path — direct file write
                    val file = File(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                }
                ActionResult.Success
            }.getOrElse { ActionResult.Failure("Write file failed: ${it.message}") }
        }
    }
}
