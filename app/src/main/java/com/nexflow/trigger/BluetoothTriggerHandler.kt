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
package com.nexflow.trigger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.core.automation.trigger.TriggerVariables
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when a Bluetooth device connects or disconnects, optionally only for one device name.
 * Reports `{{trigger.device}}` and `{{trigger.event}}`; the name is empty when it cannot be read
 * without the BLUETOOTH_CONNECT permission.
 */
@Singleton
class BluetoothTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.BLUETOOTH

    private fun canReadDeviceName(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val targetDeviceName = trigger.config["device_name"]?.trim()
        val targetEvent = trigger.config["event"]?.uppercase() ?: "CONNECTED"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val action = intent.action ?: return
                val eventMatches = when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> targetEvent == "CONNECTED"
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> targetEvent == "DISCONNECTED"
                    else -> false
                }
                if (!eventMatches) return

                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // Reading the name needs BLUETOOTH_CONNECT on API 31+ (before that it was an
                // install-time permission). canReadDeviceName() checks it; lint only recognises a
                // check written in the same function. Still guarded: some OEM stacks throw anyway.
                @SuppressLint("MissingPermission")
                val deviceName = if (canReadDeviceName()) runCatching { device?.name }.getOrNull() else null

                if (!targetDeviceName.isNullOrBlank()) {
                    if (deviceName == null || !deviceName.contains(targetDeviceName, ignoreCase = true)) return
                }

                trySend(
                    TriggerEvent(
                        triggerId = trigger.id,
                        flowId = "",
                        metadata = mapOf(
                            TriggerVariables.DEVICE to deviceName.orEmpty(),
                            TriggerVariables.EVENT to targetEvent,
                        ),
                    ),
                )
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
