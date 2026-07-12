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
import android.provider.Settings
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.service.GestureCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred

/**
 * Taps the screen at the configured pixel coordinates via the accessibility service's
 * dispatchGesture. Coordinates are device-specific — flows using this are not portable
 * across screen sizes.
 */
class SimulateTapActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coordinator: GestureCoordinator,
) : ActionExecutor {

    override val supportedType = ActionType.SIMULATE_TAP

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val x = action.config["x"]?.trim()?.toFloatOrNull()
        val y = action.config["y"]?.trim()?.toFloatOrNull()
        if (x == null || y == null || x < 0 || y < 0) {
            return ActionResult.Failure("Simulate tap needs numeric x and y screen coordinates")
        }
        if (!isAccessibilityEnabled()) {
            return ActionResult.Failure(
                "Simulate tap requires NexFlow Accessibility Service — " +
                    "enable it in Settings > Accessibility > Installed apps > NexFlow",
            )
        }
        val deferred = CompletableDeferred<Boolean>()
        coordinator.channel.send(GestureCoordinator.TapRequest(x, y, deferred))
        return if (deferred.await()) ActionResult.Success
        else ActionResult.Failure("Tap gesture was not dispatched")
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val target = "${context.packageName}/com.nexflow.service.NexFlowAccessibilityService"
        return flat.split(':').any { it.equals(target, ignoreCase = true) }
    }
}
