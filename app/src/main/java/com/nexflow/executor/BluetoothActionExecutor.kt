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

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BluetoothActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.BLUETOOTH_TOGGLE

    @SuppressLint("MissingPermission")
    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = bm.adapter
            ?: return ActionResult.Failure("No Bluetooth adapter on this device")

        val current = adapter.isEnabled
        val targetOn = when (action.config["state"]?.uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> !current
        }

        if (targetOn == current) return ActionResult.Success

        // Android 13+ removed the ability for non-system apps to toggle Bluetooth silently.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
            return ActionResult.Success
        }

        // API 31–32: BLUETOOTH_CONNECT is a runtime-dangerous permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return ActionResult.Failure("BLUETOOTH_CONNECT permission not granted — enable in Settings > Permissions")
            }
        }

        @Suppress("DEPRECATION")
        val ok = if (targetOn) adapter.enable() else adapter.disable()
        return if (ok) ActionResult.Success else ActionResult.Failure("Bluetooth toggle failed")
    }
}
