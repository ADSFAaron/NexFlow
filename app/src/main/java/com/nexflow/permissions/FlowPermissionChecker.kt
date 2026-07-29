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
 * A special access that cannot be obtained through the standard runtime-permission
 * dialog — the user must toggle it on a dedicated system Settings page.
 * [PermissionIntents] maps each of these to the intent that deep-links (and, where
 * supported, flashes) the exact entry to enable.
 */
enum class SpecialAccess {
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    DND,
    WRITE_SETTINGS,

    /**
     * ACCESS_BACKGROUND_LOCATION — on Android 11+ it cannot be obtained from the runtime
     * dialog (and never together with foreground location). The user must pick "Allow all
     * the time" on the app's location page, shown only after a prominent disclosure.
     */
    BACKGROUND_LOCATION,

    /** WRITE_SECURE_SETTINGS — grantable only via ADB, no system UI to send the user to. */
    WRITE_SECURE_SETTINGS,
}

/**
 * A permission the flow needs but the user has not granted.
 * [runtimePermissions] is non-empty when it can be requested with the standard
 * runtime-permission dialog; [special] is non-null when it needs a Settings page instead.
 */
data class MissingPermission(
    val label: String,
    val runtimePermissions: List<String> = emptyList(),
    val special: SpecialAccess? = null,
    /**
     * Set when the flow references an app that is not installed (shared/imported flows, or
     * the app was uninstalled later) — the wizard's action becomes "open its Play page".
     */
    val packageToInstall: String? = null,
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

        fun addSpecial(labelRes: Int, special: SpecialAccess) {
            val label = context.getString(labelRes)
            missing.getOrPut(label) { MissingPermission(label, special = special) }
        }

        fun requireApp(packageName: String?) {
            val pkg = packageName?.trim().orEmpty()
            if (pkg.isEmpty() || appInstalled(pkg)) return
            val label = context.getString(R.string.perm_app_missing, pkg)
            missing.getOrPut(label) { MissingPermission(label, packageToInstall = pkg) }
        }

        flow.triggers.forEach { trigger ->
            when (trigger.type) {
                TriggerType.APP_LAUNCH -> {
                    if (!accessibilityEnabled()) addSpecial(R.string.perm_accessibility, SpecialAccess.ACCESSIBILITY)
                    requireApp(trigger.config["package_name"])
                }
                TriggerType.NOTIFICATION_RECEIVED -> {
                    if (!notificationListenerEnabled()) {
                        addSpecial(R.string.perm_notification_access, SpecialAccess.NOTIFICATION_LISTENER)
                    }
                    requireApp(trigger.config["package_name"])
                }
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
                        addSpecial(R.string.perm_background_location, SpecialAccess.BACKGROUND_LOCATION)
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
                ActionType.OPEN_APP -> requireApp(action.config["package_name"])
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
                    if (!dndAccessGranted()) addSpecial(R.string.perm_dnd, SpecialAccess.DND)
                ActionType.BRIGHTNESS_ADJUST ->
                    if (!Settings.System.canWrite(context)) {
                        addSpecial(R.string.perm_write_settings, SpecialAccess.WRITE_SETTINGS)
                    }
                ActionType.SCREENSHOT ->
                    if (!accessibilityEnabled()) addSpecial(R.string.perm_accessibility, SpecialAccess.ACCESSIBILITY)
                ActionType.SIMULATE_TAP, ActionType.SIMULATE_SWIPE ->
                    if (!accessibilityEnabled()) addSpecial(R.string.perm_accessibility, SpecialAccess.ACCESSIBILITY)
                ActionType.BLUETOOTH_TOGGLE ->
                    if (!bluetoothGranted()) {
                        add(R.string.perm_bluetooth, Manifest.permission.BLUETOOTH_CONNECT)
                    }
                // WIFI_TOGGLE has a Settings-panel fallback (no permission needed), so it is not
                // listed here. AIRPLANE_TOGGLE strictly needs ADB-granted WRITE_SECURE_SETTINGS.
                ActionType.AIRPLANE_TOGGLE ->
                    if (!granted("android.permission.WRITE_SECURE_SETTINGS")) {
                        addSpecial(R.string.perm_airplane_adb, SpecialAccess.WRITE_SECURE_SETTINGS)
                    }
                else -> Unit
            }
        }

        return missing.values.toList()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun appInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess

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
