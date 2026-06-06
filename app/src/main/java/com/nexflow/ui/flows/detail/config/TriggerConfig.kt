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
package com.nexflow.ui.flows.detail.config

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.core.automation.model.TriggerType

data class TriggerInfo(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<ConfigField>,
)

private val connectOptions = listOf("CONNECTED" to "Connected", "DISCONNECTED" to "Disconnected")

val TriggerType.info: TriggerInfo
    get() = when (this) {
        TriggerType.MANUAL -> TriggerInfo(
            "Manual", Icons.Outlined.TouchApp, "Run by tapping the play button", emptyList(),
        )
        TriggerType.TIME -> TriggerInfo(
            "Time", Icons.Outlined.Schedule, "At a specific time of day",
            listOf(
                ConfigField.TimePicker("time", "Time"),
                ConfigField.Dropdown(
                    "repeat", "Repeat", listOf(
                        "DAILY" to "Every day",
                        "WEEKDAYS" to "Weekdays only",
                        "WEEKENDS" to "Weekends only",
                        "CUSTOM" to "Custom days…",
                        "ONCE" to "Once",
                    ),
                ),
                ConfigField.DayPicker(
                    "days", "Custom days",
                    showWhenKey = "repeat", showWhenValue = "CUSTOM",
                ),
            ),
        )
        TriggerType.BATTERY -> TriggerInfo(
            "Battery", Icons.Outlined.BatteryAlert, "When battery level changes",
            listOf(
                ConfigField.Slider("level", "Battery level", 0, 100, "%"),
                ConfigField.Dropdown("direction", "Trigger when", listOf("BELOW" to "Falls below", "ABOVE" to "Rises above")),
                ConfigField.Dropdown(
                    "charging", "Charging state", listOf(
                        "ANY" to "Any",
                        "CHARGING" to "Charging",
                        "DISCHARGING" to "Not charging",
                    ),
                ),
            ),
        )
        TriggerType.BLUETOOTH -> TriggerInfo(
            "Bluetooth", Icons.Outlined.Bluetooth, "When a Bluetooth device connects",
            listOf(
                ConfigField.TextInput("device_name", "Device name (optional)", hint = "Leave blank for any device"),
                ConfigField.Dropdown("event", "Event", connectOptions),
            ),
        )
        TriggerType.WIFI -> TriggerInfo(
            "Wi-Fi", Icons.Outlined.Wifi, "When Wi-Fi network changes",
            listOf(
                ConfigField.WifiSsidInput("ssid", "Network name (optional)"),
                ConfigField.Dropdown("event", "Event", connectOptions),
            ),
        )
        TriggerType.SCREEN -> TriggerInfo(
            "Screen", Icons.Outlined.PhoneAndroid, "When screen turns on or off",
            listOf(
                ConfigField.Dropdown(
                    "event", "Event", listOf(
                        "ON" to "Screen on",
                        "OFF" to "Screen off",
                        "UNLOCKED" to "Screen unlocked",
                    ),
                ),
            ),
        )
        TriggerType.APP_LAUNCH -> TriggerInfo(
            "App launch", Icons.Outlined.TravelExplore, "When an app is opened",
            listOf(ConfigField.AppPicker("package_name", "App")),
        )
        TriggerType.INCOMING_CALL -> TriggerInfo(
            "Incoming call", Icons.Outlined.Call, "When the phone receives a call",
            listOf(ConfigField.TextInput("contact", "Contact (optional)", hint = "Leave blank for any caller")),
        )
        TriggerType.SMS_RECEIVED -> TriggerInfo(
            "SMS received", Icons.Outlined.Sms, "When an SMS message arrives",
            listOf(ConfigField.TextInput("sender", "Sender (optional)", hint = "Leave blank for any sender")),
        )
        TriggerType.NOTIFICATION_RECEIVED -> TriggerInfo(
            "Notification", Icons.Outlined.Notifications, "When a notification arrives",
            listOf(ConfigField.AppPicker("package_name", "App (optional — leave empty for any)")),
        )
        TriggerType.DEVICE_BOOT -> TriggerInfo(
            "Device boot", Icons.Outlined.PowerSettingsNew, "When the device starts up", emptyList(),
        )
        TriggerType.HEADSET_PLUG -> TriggerInfo(
            "Headset", Icons.Outlined.Headset, "When headset is plugged or unplugged",
            listOf(ConfigField.Dropdown("event", "Event", connectOptions)),
        )
        TriggerType.NFC_TAG -> TriggerInfo(
            "NFC tag", Icons.Outlined.Nfc, "When an NFC tag is scanned",
            listOf(ConfigField.NfcTagScan("tag_id", "Tag ID (optional)")),
        )
        TriggerType.GEOFENCE -> TriggerInfo(
            "Geofence", Icons.Outlined.LocationOn, "When entering or leaving an area",
            listOf(
                ConfigField.CurrentLocationButton(latKey = "lat", lngKey = "lng"),
                ConfigField.TextInput("lat", "Latitude", hint = "37.4219"),
                ConfigField.TextInput("lng", "Longitude", hint = "-122.0840"),
                ConfigField.Slider("radius_m", "Radius", 50, 5000, "m"),
                ConfigField.Dropdown("event", "Event", listOf("ENTER" to "Entering area", "EXIT" to "Leaving area")),
            ),
        )
    }

fun TriggerType.configSummary(config: Map<String, String>): String = when (this) {
    TriggerType.MANUAL -> "Tap to run"
    TriggerType.TIME -> buildString {
        val t = config["time"]?.takeIf { it.isNotBlank() } ?: "?"
        val r = config["repeat"] ?: "DAILY"
        if (r == "CUSTOM") {
            val days = config["days"]?.takeIf { it.isNotBlank() } ?: "no days"
            append("$t · $days")
        } else {
            append("$t · ${r.lowercase().replaceFirstChar { it.uppercase() }}")
        }
    }
    TriggerType.BATTERY -> {
        val dir = if (config["direction"] == "ABOVE") "above" else "below"
        "${config["level"] ?: "?"}% $dir"
    }
    TriggerType.BLUETOOTH -> {
        val d = config["device_name"]?.takeIf { it.isNotBlank() } ?: "any device"
        val e = config["event"]?.lowercase() ?: "connected"
        "$d $e"
    }
    TriggerType.WIFI -> {
        val s = config["ssid"]?.takeIf { it.isNotBlank() } ?: "any network"
        val e = config["event"]?.lowercase() ?: "connected"
        "$s $e"
    }
    TriggerType.SCREEN -> config["event"]?.lowercase()?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercase() } ?: "Screen on"
    TriggerType.APP_LAUNCH -> config["package_name"]?.takeIf { it.isNotBlank() } ?: "Not set"
    TriggerType.INCOMING_CALL -> config["contact"]?.takeIf { it.isNotBlank() } ?: "Any caller"
    TriggerType.SMS_RECEIVED -> config["sender"]?.takeIf { it.isNotBlank() } ?: "Any sender"
    TriggerType.NOTIFICATION_RECEIVED -> config["package_name"]?.takeIf { it.isNotBlank() } ?: "Any app"
    TriggerType.DEVICE_BOOT -> "On boot"
    TriggerType.HEADSET_PLUG -> config["event"]?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Connected"
    TriggerType.NFC_TAG -> config["tag_id"]?.takeIf { it.isNotBlank() } ?: "Any tag"
    TriggerType.GEOFENCE -> buildString {
        append("${config["lat"] ?: "?"},${config["lng"] ?: "?"}")
        config["radius_m"]?.let { append(" · ${it}m") }
    }
}
