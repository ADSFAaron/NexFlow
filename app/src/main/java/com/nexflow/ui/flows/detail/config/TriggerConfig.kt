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

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.R
import com.nexflow.core.automation.model.TriggerType

data class TriggerInfo(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<ConfigField>,
)

/** Picker groupings, in display order. */
enum class TriggerCategory(@param:StringRes val labelRes: Int) {
    MANUAL(R.string.cat_manual),
    TIME(R.string.cat_time),
    DEVICE(R.string.cat_device_state),
    CONNECTIVITY(R.string.cat_connectivity),
    COMMUNICATION(R.string.cat_communication),
    APPS(R.string.cat_apps),
    LOCATION(R.string.cat_location),
}

val TriggerType.category: TriggerCategory
    get() = when (this) {
        TriggerType.MANUAL -> TriggerCategory.MANUAL

        TriggerType.TIME -> TriggerCategory.TIME

        TriggerType.BATTERY, TriggerType.SCREEN,
        TriggerType.DEVICE_BOOT, TriggerType.HEADSET_PLUG,
        TriggerType.SHAKE, TriggerType.AMBIENT_LIGHT,
        -> TriggerCategory.DEVICE

        TriggerType.BLUETOOTH, TriggerType.WIFI, TriggerType.NFC_TAG,
        -> TriggerCategory.CONNECTIVITY

        TriggerType.INCOMING_CALL, TriggerType.SMS_RECEIVED, TriggerType.NOTIFICATION_RECEIVED,
        -> TriggerCategory.COMMUNICATION

        TriggerType.APP_LAUNCH -> TriggerCategory.APPS

        TriggerType.GEOFENCE -> TriggerCategory.LOCATION
    }

private fun connectOptions(context: Context) = listOf(
    "CONNECTED" to context.getString(R.string.opt_connected),
    "DISCONNECTED" to context.getString(R.string.opt_disconnected),
)

fun TriggerType.info(context: Context): TriggerInfo = when (this) {
    TriggerType.MANUAL -> TriggerInfo(
        context.getString(R.string.trg_manual_label), Icons.Outlined.TouchApp, context.getString(R.string.trg_manual_desc), emptyList(),
    )
    TriggerType.TIME -> TriggerInfo(
        context.getString(R.string.trg_time_label), Icons.Outlined.Schedule, context.getString(R.string.trg_time_desc),
        listOf(
            ConfigField.TimePicker("time", context.getString(R.string.cfg_time)),
            ConfigField.Dropdown(
                "repeat", context.getString(R.string.cfg_repeat), listOf(
                    "DAILY" to context.getString(R.string.opt_every_day),
                    "WEEKDAYS" to context.getString(R.string.opt_weekdays),
                    "WEEKENDS" to context.getString(R.string.opt_weekends),
                    "CUSTOM" to context.getString(R.string.opt_custom_days),
                    "ONCE" to context.getString(R.string.opt_once),
                ),
            ),
            ConfigField.DayPicker(
                "days", context.getString(R.string.cfg_custom_days),
                showWhenKey = "repeat", showWhenValue = "CUSTOM",
            ),
        ),
    )
    TriggerType.BATTERY -> TriggerInfo(
        context.getString(R.string.trg_battery_label), Icons.Outlined.BatteryAlert, context.getString(R.string.trg_battery_desc),
        listOf(
            ConfigField.Slider("level", context.getString(R.string.cfg_battery_level), 0, 100, "%"),
            ConfigField.Dropdown("direction", context.getString(R.string.cfg_trigger_when), listOf(
                "BELOW" to context.getString(R.string.opt_falls_below),
                "ABOVE" to context.getString(R.string.opt_rises_above),
            )),
            ConfigField.Dropdown(
                "charging", context.getString(R.string.cfg_charging_state), listOf(
                    "ANY" to context.getString(R.string.opt_any),
                    "CHARGING" to context.getString(R.string.opt_charging),
                    "DISCHARGING" to context.getString(R.string.opt_not_charging),
                ),
            ),
        ),
    )
    TriggerType.BLUETOOTH -> TriggerInfo(
        context.getString(R.string.trg_bluetooth_label), Icons.Outlined.Bluetooth, context.getString(R.string.trg_bluetooth_desc),
        listOf(
            ConfigField.TextInput("device_name", context.getString(R.string.cfg_device_name_optional), hint = context.getString(R.string.cfg_hint_any_device)),
            ConfigField.Dropdown("event", context.getString(R.string.cfg_event), connectOptions(context)),
        ),
    )
    TriggerType.WIFI -> TriggerInfo(
        context.getString(R.string.trg_wifi_label), Icons.Outlined.Wifi, context.getString(R.string.trg_wifi_desc),
        listOf(
            ConfigField.WifiSsidInput("ssid", context.getString(R.string.cfg_network_name_optional)),
            ConfigField.Dropdown("event", context.getString(R.string.cfg_event), connectOptions(context)),
        ),
    )
    TriggerType.SCREEN -> TriggerInfo(
        context.getString(R.string.trg_screen_label), Icons.Outlined.PhoneAndroid, context.getString(R.string.trg_screen_desc),
        listOf(
            ConfigField.Dropdown(
                "event", context.getString(R.string.cfg_event), listOf(
                    "ON" to context.getString(R.string.opt_screen_on),
                    "OFF" to context.getString(R.string.opt_screen_off),
                    "UNLOCKED" to context.getString(R.string.opt_screen_unlocked),
                ),
            ),
        ),
    )
    TriggerType.APP_LAUNCH -> TriggerInfo(
        context.getString(R.string.trg_app_launch_label), Icons.Outlined.TravelExplore, context.getString(R.string.trg_app_launch_desc),
        listOf(ConfigField.AppPicker("package_name", context.getString(R.string.cfg_app))),
    )
    TriggerType.INCOMING_CALL -> TriggerInfo(
        context.getString(R.string.trg_incoming_call_label), Icons.Outlined.Call, context.getString(R.string.trg_incoming_call_desc),
        listOf(ConfigField.TextInput("contact", context.getString(R.string.cfg_contact_optional), hint = context.getString(R.string.cfg_hint_any_caller))),
    )
    TriggerType.SMS_RECEIVED -> TriggerInfo(
        context.getString(R.string.trg_sms_label), Icons.Outlined.Sms, context.getString(R.string.trg_sms_desc),
        listOf(ConfigField.TextInput("sender", context.getString(R.string.cfg_sender_optional), hint = context.getString(R.string.cfg_hint_any_sender))),
    )
    TriggerType.NOTIFICATION_RECEIVED -> TriggerInfo(
        context.getString(R.string.trg_notification_label), Icons.Outlined.Notifications, context.getString(R.string.trg_notification_desc),
        listOf(ConfigField.AppPicker("package_name", context.getString(R.string.cfg_notif_app_optional))),
    )
    TriggerType.DEVICE_BOOT -> TriggerInfo(
        context.getString(R.string.trg_boot_label), Icons.Outlined.PowerSettingsNew, context.getString(R.string.trg_boot_desc), emptyList(),
    )
    TriggerType.HEADSET_PLUG -> TriggerInfo(
        context.getString(R.string.trg_headset_label), Icons.Outlined.Headset, context.getString(R.string.trg_headset_desc),
        listOf(ConfigField.Dropdown("event", context.getString(R.string.cfg_event), connectOptions(context))),
    )
    TriggerType.NFC_TAG -> TriggerInfo(
        context.getString(R.string.trg_nfc_label), Icons.Outlined.Nfc, context.getString(R.string.trg_nfc_desc),
        listOf(ConfigField.NfcTagScan("tag_id", context.getString(R.string.cfg_tag_id_optional))),
    )
    TriggerType.SHAKE -> TriggerInfo(
        context.getString(R.string.trg_shake_label), Icons.Outlined.Vibration, context.getString(R.string.trg_shake_desc),
        listOf(
            ConfigField.Dropdown(
                "sensitivity", context.getString(R.string.cfg_sensitivity), listOf(
                    "LOW" to context.getString(R.string.opt_sens_low),
                    "MEDIUM" to context.getString(R.string.opt_sens_medium),
                    "HIGH" to context.getString(R.string.opt_sens_high),
                ),
            ),
            ConfigField.InfoText(
                "_shake_battery_info",
                context.getString(R.string.cfg_info_note_label),
                context.getString(R.string.cfg_info_shake_battery_body),
                isWarning = true,
            ),
        ),
    )
    TriggerType.AMBIENT_LIGHT -> TriggerInfo(
        context.getString(R.string.trg_light_label), Icons.Outlined.WbSunny, context.getString(R.string.trg_light_desc),
        listOf(
            ConfigField.Dropdown(
                "mode", context.getString(R.string.cfg_trigger_when), listOf(
                    "BELOW" to context.getString(R.string.opt_darker_than),
                    "ABOVE" to context.getString(R.string.opt_brighter_than),
                ),
            ),
            ConfigField.TextInput("threshold_lux", context.getString(R.string.cfg_lux_threshold), hint = "50"),
        ),
    )
    TriggerType.GEOFENCE -> TriggerInfo(
        context.getString(R.string.trg_geofence_label), Icons.Outlined.LocationOn, context.getString(R.string.trg_geofence_desc),
        listOf(
            ConfigField.CurrentLocationButton(label = context.getString(R.string.cfg_use_current_location), latKey = "lat", lngKey = "lng"),
            ConfigField.TextInput("lat", context.getString(R.string.cfg_latitude), hint = "37.4219"),
            ConfigField.TextInput("lng", context.getString(R.string.cfg_longitude), hint = "-122.0840"),
            ConfigField.Slider("radius_m", context.getString(R.string.cfg_radius), 50, 5000, "m"),
            ConfigField.Dropdown("event", context.getString(R.string.cfg_event), listOf(
                "ENTER" to context.getString(R.string.opt_entering_area),
                "EXIT" to context.getString(R.string.opt_leaving_area),
            )),
        ),
    )
}

private fun connectEventLabel(context: Context, value: String?): String = context.getString(
    when (value?.uppercase()) {
        "DISCONNECTED" -> R.string.opt_disconnected
        else -> R.string.opt_connected
    },
)

fun TriggerType.configSummary(context: Context, config: Map<String, String>): String = when (this) {
    TriggerType.MANUAL -> context.getString(R.string.sum_tap_to_run)
    TriggerType.TIME -> buildString {
        val t = config["time"]?.takeIf { it.isNotBlank() } ?: "?"
        val r = config["repeat"] ?: "DAILY"
        if (r == "CUSTOM") {
            val days = config["days"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_no_days)
            append("$t · $days")
        } else {
            val label = context.getString(
                when (r) {
                    "WEEKDAYS" -> R.string.opt_weekdays
                    "WEEKENDS" -> R.string.opt_weekends
                    "ONCE" -> R.string.opt_once
                    else -> R.string.opt_every_day
                },
            )
            append("$t · $label")
        }
    }
    TriggerType.BATTERY -> {
        val dir = context.getString(if (config["direction"] == "ABOVE") R.string.sum_battery_above else R.string.sum_battery_below)
        context.getString(R.string.sum_battery, config["level"] ?: "?", dir)
    }
    TriggerType.BLUETOOTH -> {
        val d = config["device_name"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_device)
        context.getString(R.string.sum_device_event, d, connectEventLabel(context, config["event"]))
    }
    TriggerType.WIFI -> {
        val s = config["ssid"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_network)
        context.getString(R.string.sum_device_event, s, connectEventLabel(context, config["event"]))
    }
    TriggerType.SCREEN -> context.getString(
        when (config["event"]) {
            "OFF" -> R.string.opt_screen_off
            "UNLOCKED" -> R.string.opt_screen_unlocked
            else -> R.string.opt_screen_on
        },
    )
    TriggerType.APP_LAUNCH -> config["package_name"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_not_set)
    TriggerType.INCOMING_CALL -> config["contact"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_caller)
    TriggerType.SMS_RECEIVED -> config["sender"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_sender)
    TriggerType.NOTIFICATION_RECEIVED -> config["package_name"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_app)
    TriggerType.DEVICE_BOOT -> context.getString(R.string.sum_on_boot)
    TriggerType.HEADSET_PLUG -> connectEventLabel(context, config["event"])
    TriggerType.NFC_TAG -> config["tag_id"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_tag)
    TriggerType.SHAKE -> context.getString(
        when (config["sensitivity"]?.uppercase()) {
            "LOW" -> R.string.opt_sens_low
            "HIGH" -> R.string.opt_sens_high
            else -> R.string.opt_sens_medium
        },
    )
    TriggerType.AMBIENT_LIGHT -> {
        val op = if (config["mode"]?.uppercase() == "ABOVE") ">" else "<"
        "$op ${config["threshold_lux"]?.takeIf { it.isNotBlank() } ?: "50"} lx"
    }
    TriggerType.GEOFENCE -> buildString {
        append("${config["lat"] ?: "?"},${config["lng"] ?: "?"}")
        config["radius_m"]?.let { append(" · ${it}m") }
    }
}
