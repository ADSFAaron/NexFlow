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

import android.annotation.SuppressLint
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
import com.nexflow.core.automation.LogText

class WriteFileActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.WRITE_FILE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val path = action.config["path"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure(LogText.of("write_no_path"))
        val content = action.config["content"] ?: ""

        // Security: reject path-traversal segments. Combined with flow import, an unsanitised
        // path could otherwise escape its intended directory and overwrite app-private files.
        if (path.split('/').any { it == ".." }) {
            return ActionResult.Failure(LogText.of("write_dotdot"))
        }
        if (isAppPrivate(path)) {
            return ActionResult.Failure(LogText.of("write_private"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val sharedRoot = SHARED_STORAGE_ROOTS.firstOrNull { path.startsWith(it) }
                if (sharedRoot != null) {
                    // Use MediaStore for scoped storage on Android 10+
                    val relativePath = path.removePrefix(sharedRoot)
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
                    ) ?: throw IllegalStateException(LogText.of("write_mediastore"))
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
            }.getOrElse { failureFrom("write_failed", it) }
        }
    }

    /**
     * Whether [path] lands in NexFlow's private storage: the database, shared preferences (the AI
     * key and endpoint live there) and everything else the app trusts. A flow writing there could
     * plant settings that outlive the flow itself.
     *
     * Compared by canonical path, so `/data/data/<pkg>` (a symlink to `/data/user/0/<pkg>`) and
     * any other alias of the same directory are caught. Device-protected storage is separate.
     */
    internal fun isAppPrivate(path: String): Boolean {
        val target = runCatching { File(path).canonicalPath }.getOrElse { return true }
        val roots = listOfNotNull(
            context.dataDir,
            runCatching { context.createDeviceProtectedStorageContext().dataDir }.getOrNull(),
        ).map { it.canonicalPath }
        return roots.any { root -> target == root || target.startsWith("$root/") }
    }

    private companion object {
        /**
         * How people write shared storage in a flow. Only ever compared as text to pick the
         * MediaStore path — never opened as a file — so the hardcoded /sdcard/ lint warns about
         * is the point here.
         */
        @SuppressLint("SdCardPath")
        val SHARED_STORAGE_ROOTS = listOf("/storage/emulated/0/", "/sdcard/")
    }
}
