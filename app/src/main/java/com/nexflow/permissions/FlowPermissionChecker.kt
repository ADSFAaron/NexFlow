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
import com.nexflow.R
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
    /**
     * True for ACCESS_BACKGROUND_LOCATION, which on Android 11+ cannot be obtained from the
     * normal runtime dialog (and never together with foreground location). The user must pick
     * "Allow all the time" on the app's system location settings page, so the reminder routes
     * this entry to that screen instead of launching a permission request.
     */
    val openLocationSettings: Boolean = false,
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

        fun add(labelRes: Int, vararg runtime: String) {
            val label = context.getString(labelRes)
            missing.getOrPut(label) { MissingPermission(label, runtime.toList()) }
        }

        fun addLocationSettings(labelRes: Int) {
            val label = context.getString(labelRes)
            missing.getOrPut(label) { MissingPermission(label, openLocationSettings = true) }
        }

        flow.triggers.forEach { trigger ->
            when (trigger.type) {
                TriggerType.APP_LAUNCH ->
                    if (!accessibilityEnabled()) add(R.string.perm_accessibility)
                TriggerType.NOTIFICATION_RECEIVED ->
                    if (!notificationListenerEnabled()) add(R.string.perm_notification_access)
                TriggerType.SMS_RECEIVED ->
                    if (!granted(Manifest.permission.RECEIVE_SMS)) {
                        add(R.string.perm_receive_sms, Manifest.permission.RECEIVE_SMS)
                    }
                TriggerType.INCOMING_CALL ->
                    if (!granted(Manifest.permission.READ_PHONE_STATE)) {
                        add(R.string.perm_phone_state, Manifest.permission.READ_PHONE_STATE)
                    }
                TriggerType.GEOFENCE -> {
                    // Staged request (Android 11+ rule): foreground location must be granted via
                    // the runtime dialog first; only then can the user grant background location,
                    // and that one is set manually as "Allow all the time" in system settings.
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        add(R.string.perm_location, Manifest.permission.ACCESS_FINE_LOCATION)
                    } else if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        addLocationSettings(R.string.perm_background_location)
                    }
                }
                TriggerType.WIFI ->
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        add(R.string.perm_location_wifi, Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                TriggerType.BLUETOOTH ->
                    if (!bluetoothGranted()) {
                        add(R.string.perm_bluetooth, Manifest.permission.BLUETOOTH_CONNECT)
                    }
                else -> Unit
            }
        }

        flow.actions.filter { it.enabled }.forEach { action ->
            when (action.type) {
                ActionType.SEND_SMS ->
                    if (!granted(Manifest.permission.SEND_SMS)) {
                        add(R.string.perm_send_sms, Manifest.permission.SEND_SMS)
                    }
                ActionType.CALL_PHONE ->
                    if (!granted(Manifest.permission.CALL_PHONE)) {
                        add(R.string.perm_call_phone, Manifest.permission.CALL_PHONE)
                    }
                ActionType.NOTIFICATION ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !granted(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        add(R.string.perm_notifications, Manifest.permission.POST_NOTIFICATIONS)
                    }
                ActionType.DND_TOGGLE ->
                    if (!dndAccessGranted()) add(R.string.perm_dnd)
                ActionType.BRIGHTNESS_ADJUST ->
                    if (!Settings.System.canWrite(context)) add(R.string.perm_write_settings)
                ActionType.SCREENSHOT ->
                    if (!accessibilityEnabled()) add(R.string.perm_accessibility)
                ActionType.BLUETOOTH_TOGGLE ->
                    if (!bluetoothGranted()) {
                        add(R.string.perm_bluetooth, Manifest.permission.BLUETOOTH_CONNECT)
                    }
                // WIFI_TOGGLE has a Settings-panel fallback (no permission needed), so it is not
                // listed here. AIRPLANE_TOGGLE strictly needs ADB-granted WRITE_SECURE_SETTINGS.
                ActionType.AIRPLANE_TOGGLE ->
                    if (!granted("android.permission.WRITE_SECURE_SETTINGS")) {
                        add(R.string.perm_airplane_adb)
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
