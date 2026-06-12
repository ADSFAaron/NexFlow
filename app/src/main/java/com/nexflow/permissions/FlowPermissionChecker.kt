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
package com.nexflow.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A permission the flow needs but the user has not granted.
 * [runtimePermissions] is non-empty when it can be requested with the
 * standard runtime-permission dialog; empty means a special access the
 * user must enable manually (Settings screen has the entry points).
 */
data class MissingPermission(
    val label: String,
    val runtimePermissions: List<String> = emptyList(),
)

/**
 * Checks which permissions a flow's triggers/actions need but the user
 * has not granted yet. Labels match the entries on the Settings screen.
 */
@Singleton
class FlowPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun missingPermissions(flow: Flow): List<MissingPermission> {
        val missing = linkedMapOf<String, MissingPermission>()

        fun add(label: String, vararg runtime: String) {
            missing.getOrPut(label) { MissingPermission(label, runtime.toList()) }
        }

        flow.triggers.forEach { trigger ->
            when (trigger.type) {
                TriggerType.APP_LAUNCH ->
                    if (!accessibilityEnabled()) add("Accessibility Service")
                TriggerType.NOTIFICATION_RECEIVED ->
                    if (!notificationListenerEnabled()) add("Notification access")
                TriggerType.SMS_RECEIVED ->
                    if (!granted(Manifest.permission.RECEIVE_SMS)) {
                        add("Receive SMS", Manifest.permission.RECEIVE_SMS)
                    }
                TriggerType.INCOMING_CALL ->
                    if (!granted(Manifest.permission.READ_PHONE_STATE)) {
                        add("Phone state", Manifest.permission.READ_PHONE_STATE)
                    }
                TriggerType.GEOFENCE -> {
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        add("Location", Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        add("Background location", Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
                TriggerType.WIFI ->
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        add("Location (Wi-Fi SSID detection)", Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                TriggerType.BLUETOOTH ->
                    if (!bluetoothGranted()) {
                        add("Nearby devices (Bluetooth)", Manifest.permission.BLUETOOTH_CONNECT)
                    }
                else -> Unit
            }
        }

        flow.actions.filter { it.enabled }.forEach { action ->
            when (action.type) {
                ActionType.SEND_SMS ->
                    if (!granted(Manifest.permission.SEND_SMS)) {
                        add("Send SMS", Manifest.permission.SEND_SMS)
                    }
                ActionType.CALL_PHONE ->
                    if (!granted(Manifest.permission.CALL_PHONE)) {
                        add("Call phone", Manifest.permission.CALL_PHONE)
                    }
                ActionType.NOTIFICATION ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !granted(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        add("Notifications", Manifest.permission.POST_NOTIFICATIONS)
                    }
                ActionType.DND_TOGGLE ->
                    if (!dndAccessGranted()) add("Do Not Disturb access")
                ActionType.BRIGHTNESS_ADJUST ->
                    if (!Settings.System.canWrite(context)) add("Modify system settings")
                ActionType.SCREENSHOT ->
                    if (!accessibilityEnabled()) add("Accessibility Service")
                ActionType.BLUETOOTH_TOGGLE ->
                    if (!bluetoothGranted()) {
                        add("Nearby devices (Bluetooth)", Manifest.permission.BLUETOOTH_CONNECT)
                    }
                ActionType.WIFI_TOGGLE, ActionType.AIRPLANE_TOGGLE ->
                    if (!granted("android.permission.WRITE_SECURE_SETTINGS")) {
                        add("Wi-Fi & Airplane mode (ADB)")
                    }
                else -> Unit
            }
        }

        return missing.values.toList()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun bluetoothGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun accessibilityEnabled(): Boolean =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(context.packageName) ?: false

    private fun notificationListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    private fun dndAccessGranted(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }
}
