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
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Images the user attached to the AI chat, kept as files in the app's private storage.
 *
 * Files, not database columns: a conversation is persisted as one Room row, and a few photos'
 * worth of base64 in it would push the row past the 2 MB CursorWindow — the whole conversation
 * would then fail to load. So the transcript and the model history only carry a file name, and
 * the bytes are read back when a request is built.
 *
 * Everything is re-encoded on the way in: a phone photo is 3–12 MB, and Gemini caps a whole
 * request at 20 MB while the history replays every image on every turn. Downscaling also
 * strips EXIF — location included — before anything leaves the device.
 */
@Singleton
class AiImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Copies a picked image in, downscaled to [MAX_EDGE_PX]. Returns its name, or null if unreadable. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software, because Bitmap.compress can't read hardware bitmaps back.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
            val name = "${UUID.randomUUID()}.jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            name
        }.onFailure { Log.w(TAG, "could not import image $uri", it) }.getOrNull()
    }

    /** The image as the API wants it, or null when the file is gone (cleared chat, pruned). */
    suspend fun base64(name: String): String? = withContext(Dispatchers.IO) {
        val f = file(name)
        if (!f.isFile) return@withContext null
        runCatching { Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    suspend fun delete(names: Collection<String>) = withContext(Dispatchers.IO) {
        names.forEach { file(it).delete() }
    }

    /**
     * Deletes images nothing refers to any more — a chat that was cleared, a flow that was
     * deleted along with its conversation. Recent files are spared: an image picked but not yet
     * sent, or sent in a turn still running, is not in any saved transcript yet.
     */
    suspend fun prune(referenced: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - PRUNE_GRACE_MS
        dir.listFiles().orEmpty()
            .filter { it.name !in referenced && it.lastModified() < cutoff }
            .forEach {
                Log.d(TAG, "pruning unreferenced ${it.name}")
                it.delete()
            }
    }

    companion object {
        private const val TAG = "AiImageStore"
        private const val DIR = "ai_images"
        const val MIME_TYPE = "image/jpeg"

        /** Long edge after import. Enough for a screenshot's small print to stay legible. */
        const val MAX_EDGE_PX = 1600
        private const val JPEG_QUALITY = 85
        private const val PRUNE_GRACE_MS = 24 * 60 * 60 * 1000L

        /** How many images one message may carry. */
        const val MAX_PER_MESSAGE = 4
    }
}
