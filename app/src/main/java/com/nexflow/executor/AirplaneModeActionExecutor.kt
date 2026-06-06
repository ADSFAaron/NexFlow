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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AirplaneModeActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.AIRPLANE_TOGGLE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        // WRITE_SECURE_SETTINGS is not grantable via the UI; must be granted once via ADB:
        //   adb shell pm grant com.nexflow android.permission.WRITE_SECURE_SETTINGS
        val hasPermission = context.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ActionResult.Failure(
                "WRITE_SECURE_SETTINGS not granted — run once: " +
                    "adb shell pm grant com.nexflow android.permission.WRITE_SECURE_SETTINGS",
            )
        }

        val cr = context.contentResolver
        val current = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        val targetOn = when (action.config["state"]?.uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> !current
        }

        if (targetOn == current) return ActionResult.Success

        return try {
            Settings.Global.putInt(cr, Settings.Global.AIRPLANE_MODE_ON, if (targetOn) 1 else 0)
            context.sendBroadcast(
                Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                    putExtra("state", targetOn)
                },
            )
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Airplane mode toggle failed: ${e.message}")
        }
    }
}
