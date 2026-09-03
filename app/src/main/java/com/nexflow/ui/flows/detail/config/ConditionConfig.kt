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
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.R
import com.nexflow.core.automation.model.ConditionType

data class ConditionInfo(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<ConfigField>,
)

/** Picker groupings for conditions, in display order. */
enum class ConditionCategory(@param:StringRes val labelRes: Int) {
    TIME(R.string.cat_time),
    DEVICE(R.string.cat_device_state),
    CONNECTIVITY(R.string.cat_connectivity),
    ADVANCED(R.string.cat_advanced),
}

val ConditionType.category: ConditionCategory
    get() = when (this) {
        ConditionType.TIME_RANGE, ConditionType.DAY_OF_WEEK -> ConditionCategory.TIME
        ConditionType.BATTERY_LEVEL, ConditionType.CHARGING,
        ConditionType.SCREEN_STATE,
        -> ConditionCategory.DEVICE

        ConditionType.WIFI_CONNECTED, ConditionType.BLUETOOTH_CONNECTED -> ConditionCategory.CONNECTIVITY
        ConditionType.EXPRESSION -> ConditionCategory.ADVANCED
    }

/**
 * The key under which the editor carries [com.nexflow.core.automation.model.Condition.negate]
 * through the generic config dialog. Stripped from the config map before saving — it is a field
 * of the condition, not part of its configuration.
 */
const val NEGATE_KEY = "_negate"

private fun negateToggle(context: Context) = ConfigField.Toggle(
    NEGATE_KEY,
    context.getString(R.string.cfg_negate),
    context.getString(R.string.cfg_negate_desc),
)

private fun connectedStateOptions(context: Context) = listOf(
    "CONNECTED" to context.getString(R.string.opt_connected),
    "DISCONNECTED" to context.getString(R.string.opt_disconnected),
)

fun ConditionType.info(context: Context): ConditionInfo = when (this) {
    ConditionType.TIME_RANGE -> ConditionInfo(
        context.getString(R.string.cnd_time_range_label), Icons.Outlined.Schedule,
        context.getString(R.string.cnd_time_range_desc),
        listOf(
            ConfigField.TimePicker("start", context.getString(R.string.cfg_start_time)),
            ConfigField.TimePicker("end", context.getString(R.string.cfg_end_time)),
            negateToggle(context),
        ),
    )
    ConditionType.DAY_OF_WEEK -> ConditionInfo(
        context.getString(R.string.cnd_day_of_week_label), Icons.Outlined.DateRange,
        context.getString(R.string.cnd_day_of_week_desc),
        listOf(
            ConfigField.DayPicker("days", context.getString(R.string.cfg_days_of_week)),
            negateToggle(context),
        ),
    )
    ConditionType.BATTERY_LEVEL -> ConditionInfo(
        context.getString(R.string.cnd_battery_label), Icons.Outlined.BatteryAlert,
        context.getString(R.string.cnd_battery_desc),
        listOf(
            ConfigField.InfoText(
                "_battery_condition_info",
                context.getString(R.string.cfg_info_note_label),
                context.getString(R.string.cfg_info_battery_condition_body),
            ),
            ConfigField.Dropdown(
                "direction", context.getString(R.string.cfg_battery_when), listOf(
                    "BELOW" to context.getString(R.string.opt_at_or_below),
                    "ABOVE" to context.getString(R.string.opt_at_or_above),
                ),
            ),
            ConfigField.Slider("level", context.getString(R.string.cfg_battery_level), 0, 100, "%"),
            negateToggle(context),
        ),
    )
    ConditionType.CHARGING -> ConditionInfo(
        context.getString(R.string.cnd_charging_label), Icons.Outlined.BatteryChargingFull,
        context.getString(R.string.cnd_charging_desc),
        listOf(
            ConfigField.Dropdown(
                "state", context.getString(R.string.cfg_state), listOf(
                    "CHARGING" to context.getString(R.string.opt_charging),
                    "NOT_CHARGING" to context.getString(R.string.opt_not_charging),
                ),
            ),
            negateToggle(context),
        ),
    )
    ConditionType.WIFI_CONNECTED -> ConditionInfo(
        context.getString(R.string.cnd_wifi_label), Icons.Outlined.Wifi,
        context.getString(R.string.cnd_wifi_desc),
        listOf(
            ConfigField.Dropdown("state", context.getString(R.string.cfg_state), connectedStateOptions(context)),
            ConfigField.WifiSsidInput("ssid", context.getString(R.string.cfg_network_name_optional)),
            ConfigField.InfoText(
                "_wifi_ssid_info",
                context.getString(R.string.cfg_info_note_label),
                context.getString(R.string.cfg_info_wifi_ssid_body),
            ),
            negateToggle(context),
        ),
    )
    ConditionType.BLUETOOTH_CONNECTED -> ConditionInfo(
        context.getString(R.string.cnd_bluetooth_label), Icons.Outlined.Bluetooth,
        context.getString(R.string.cnd_bluetooth_desc),
        listOf(
            ConfigField.Dropdown("state", context.getString(R.string.cfg_state), connectedStateOptions(context)),
            ConfigField.TextInput(
                "device_name", context.getString(R.string.cfg_device_name_optional),
                hint = context.getString(R.string.cfg_hint_any_device),
            ),
            ConfigField.InfoText(
                "_bluetooth_audio_info",
                context.getString(R.string.cfg_info_note_label),
                context.getString(R.string.cfg_info_bluetooth_audio_body),
            ),
            negateToggle(context),
        ),
    )
    ConditionType.SCREEN_STATE -> ConditionInfo(
        context.getString(R.string.cnd_screen_label), Icons.Outlined.PhoneAndroid,
        context.getString(R.string.cnd_screen_desc),
        listOf(
            ConfigField.Dropdown(
                "state", context.getString(R.string.cfg_state), listOf(
                    "ON" to context.getString(R.string.opt_screen_on),
                    "OFF" to context.getString(R.string.opt_screen_off),
                ),
            ),
            negateToggle(context),
        ),
    )
    ConditionType.EXPRESSION -> ConditionInfo(
        context.getString(R.string.cnd_expression_label), Icons.AutoMirrored.Outlined.Rule,
        context.getString(R.string.cnd_expression_desc),
        listOf(
            ConfigField.ConditionInput("expression", context.getString(R.string.cfg_condition)),
            negateToggle(context),
        ),
    )
}

/**
 * One-line summary shown under the condition's name in the flow editor.
 * [negate] is rendered as a leading "NOT" so an inverted condition can't be misread as a plain one.
 */
fun ConditionType.configSummary(
    context: Context,
    config: Map<String, String>,
    negate: Boolean = false,
): String {
    val body = when (this) {
        ConditionType.TIME_RANGE -> {
            val start = config["start"]?.takeIf { it.isNotBlank() } ?: "00:00"
            val end = config["end"]?.takeIf { it.isNotBlank() } ?: "24:00"
            "$start – $end"
        }
        ConditionType.DAY_OF_WEEK ->
            config["days"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_day)
        ConditionType.BATTERY_LEVEL -> context.getString(
            if (config["direction"]?.uppercase() == "ABOVE") R.string.sum_battery_at_or_above
            else R.string.sum_battery_at_or_below,
            config["level"] ?: "20",
        )
        ConditionType.CHARGING -> context.getString(
            if (config["state"]?.uppercase() == "NOT_CHARGING") R.string.opt_not_charging else R.string.opt_charging,
        )
        ConditionType.WIFI_CONNECTED -> {
            val ssid = config["ssid"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_any_network)
            context.getString(R.string.sum_device_event, ssid, connectedLabel(context, config["state"]))
        }
        ConditionType.BLUETOOTH_CONNECTED -> {
            val device = config["device_name"]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.sum_any_device)
            context.getString(R.string.sum_device_event, device, connectedLabel(context, config["state"]))
        }
        ConditionType.SCREEN_STATE -> context.getString(
            if (config["state"]?.uppercase() == "OFF") R.string.opt_screen_off else R.string.opt_screen_on,
        )
        ConditionType.EXPRESSION ->
            config["expression"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_not_set)
    }
    return if (negate) context.getString(R.string.sum_negated, body) else body
}

private fun connectedLabel(context: Context, value: String?): String = context.getString(
    when (value?.uppercase()) {
        "DISCONNECTED" -> R.string.opt_disconnected
        else -> R.string.opt_connected
    },
)
