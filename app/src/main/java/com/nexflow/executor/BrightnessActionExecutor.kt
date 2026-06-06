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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BrightnessActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.BRIGHTNESS_ADJUST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        if (!Settings.System.canWrite(context)) {
            return ActionResult.Failure(
                "WRITE_SETTINGS not granted — enable in Settings > Special app access > Modify system settings",
            )
        }

        val extraDim = action.config["extra_dim"] == "true"
        val level: Int = if (extraDim) {
            1 // minimum backlight
        } else {
            val rawLevel = resolveVariables(action.config["level"] ?: "", variables)
                .toIntOrNull() ?: return ActionResult.Failure("No brightness level specified")
            when (action.config["brightness_unit"]) {
                "PCT" -> (rawLevel.coerceIn(0, 100) * 255 / 100)
                else -> rawLevel.coerceIn(0, 255)
            }
        }

        val cr = context.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, level)
        return ActionResult.Success
    }
}
