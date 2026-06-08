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
package com.nexflow.service

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.accessibility.AccessibilityEvent
import com.nexflow.event.AppLaunchEventSource
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NexFlowAccessibilityService : AccessibilityService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenshotEntryPoint {
        fun screenshotCoordinator(): ScreenshotCoordinator
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var coordinator: ScreenshotCoordinator

    override fun onCreate() {
        super.onCreate()
        coordinator = EntryPointAccessors
            .fromApplication(applicationContext, ScreenshotEntryPoint::class.java)
            .screenshotCoordinator()
    }

    override fun onServiceConnected() {
        scope.launch {
            for (deferred in coordinator.channel) {
                captureAndSave(deferred)
            }
        }
    }

    private fun captureAndSave(deferred: CompletableDeferred<Boolean>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Bitmap.wrapHardwareBuffer requires API 31 — fail gracefully on Android 11.
            deferred.complete(false)
            return
        }
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } else null
                    result.hardwareBuffer.close()
                    if (bitmap == null) { deferred.complete(false); return }

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME,
                            "NexFlow_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
                    }
                    val uri = contentResolver
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    val saved = uri != null && runCatching {
                        contentResolver.openOutputStream(uri)!!.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }.isSuccess
                    deferred.complete(saved)
                }

                override fun onFailure(errorCode: Int) {
                    deferred.complete(false)
                }
            },
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != applicationContext.packageName) {
                AppLaunchEventSource.emit(pkg)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
