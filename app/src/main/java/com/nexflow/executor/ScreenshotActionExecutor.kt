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
import android.os.Build
import android.provider.Settings
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.service.ScreenshotCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject

class ScreenshotActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: ScreenshotCoordinator,
) : ActionExecutor {

    override val supportedType = ActionType.SCREENSHOT

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ActionResult.Failure("Screenshot requires Android 12 or higher")
        }
        if (!isAccessibilityEnabled()) {
            return ActionResult.Failure(
                "Screenshot requires NexFlow Accessibility Service — " +
                    "enable it in Settings > Accessibility > Installed apps > NexFlow",
            )
        }
        val deferred = CompletableDeferred<Boolean>()
        coordinator.channel.send(deferred)
        return if (deferred.await()) ActionResult.Success
        else ActionResult.Failure("Screenshot capture failed")
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
