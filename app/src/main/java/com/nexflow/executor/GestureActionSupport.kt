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

import android.content.Context
import android.graphics.PointF
import android.provider.Settings
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.service.GestureCoordinator
import kotlinx.coroutines.CompletableDeferred

/**
 * Shared plumbing for the gesture actions (SIMULATE_TAP / SIMULATE_SWIPE): they all end up
 * as one stroke handed to NexFlowAccessibilityService, and all fail the same way when the
 * accessibility service is off.
 */
internal object GestureActionSupport {

    const val DEFAULT_TAP_DURATION_MS = 50L
    const val DEFAULT_SWIPE_DURATION_MS = 300L

    fun accessibilityEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val target = "${context.packageName}/com.nexflow.service.NexFlowAccessibilityService"
        return flat.split(':').any { it.equals(target, ignoreCase = true) }
    }

    fun accessibilityFailure(what: String) = ActionResult.Failure(
        "$what requires NexFlow Accessibility Service — " +
            "enable it in Settings > Accessibility > Installed apps > NexFlow",
    )

    /** Reads a non-negative pixel coordinate, or null when missing/invalid. */
    fun Action.coordinate(key: String): Float? =
        config[key]?.trim()?.toFloatOrNull()?.takeIf { it >= 0f && it.isFinite() }

    /** Reads a positive duration in ms, falling back to [default] when missing/invalid. */
    fun Action.durationMs(key: String, default: Long): Long =
        config[key]?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: default

    suspend fun dispatch(
        coordinator: GestureCoordinator,
        points: List<PointF>,
        durationMs: Long,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        coordinator.channel.send(GestureCoordinator.GestureRequest(points, durationMs, deferred))
        return deferred.await()
    }
}
