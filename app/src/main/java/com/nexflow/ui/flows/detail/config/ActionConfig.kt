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
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SpeakerPhone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.R
import com.nexflow.core.automation.model.ActionType

data class ActionInfo(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<ConfigField>,
)

/** Picker groupings, in display order. */
enum class ActionCategory(@param:StringRes val labelRes: Int) {
    CONTROL_FLOW(R.string.cat_control_flow),
    CONNECTIVITY(R.string.cat_connectivity),
    DEVICE(R.string.cat_device),
    COMMUNICATION(R.string.cat_communication),
    ALERTS(R.string.cat_alerts),
    WEB(R.string.cat_web),
    APPS_MEDIA(R.string.cat_apps_media),
    FILES_DATA(R.string.cat_files_data),
}

val ActionType.category: ActionCategory
    get() = when (this) {
        ActionType.IF_BLOCK, ActionType.ELSE_BLOCK, ActionType.END_IF,
        ActionType.REPEAT_BLOCK, ActionType.END_REPEAT,
        ActionType.DELAY, ActionType.SET_VARIABLE,
        ActionType.SHOW_MENU, ActionType.MENU_CASE, ActionType.END_MENU,
        -> ActionCategory.CONTROL_FLOW

        ActionType.WIFI_TOGGLE, ActionType.BLUETOOTH_TOGGLE, ActionType.AIRPLANE_TOGGLE,
        -> ActionCategory.CONNECTIVITY

        ActionType.VOLUME_ADJUST, ActionType.BRIGHTNESS_ADJUST,
        ActionType.DND_TOGGLE, ActionType.SCREENSHOT, ActionType.SET_WALLPAPER,
        ActionType.SIMULATE_TAP, ActionType.SPEAKERPHONE,
        -> ActionCategory.DEVICE

        ActionType.SEND_SMS, ActionType.CALL_PHONE,
        -> ActionCategory.COMMUNICATION

        ActionType.NOTIFICATION, ActionType.TOAST, ActionType.TTS,
        -> ActionCategory.ALERTS

        ActionType.HTTP_REQUEST, ActionType.OPEN_URL,
        -> ActionCategory.WEB

        ActionType.OPEN_APP, ActionType.MEDIA_PLAY_PAUSE, ActionType.SHARE,
        -> ActionCategory.APPS_MEDIA

        ActionType.WRITE_FILE, ActionType.CLIPBOARD_COPY,
        -> ActionCategory.FILES_DATA
    }

private fun toggleOptions(context: Context) = listOf(
    "ON" to context.getString(R.string.opt_on),
    "OFF" to context.getString(R.string.opt_off),
    "TOGGLE" to context.getString(R.string.opt_toggle),
)

private fun wallpaperTargets(context: Context) = listOf(
    "BOTH" to context.getString(R.string.opt_wallpaper_both),
    "HOME" to context.getString(R.string.opt_wallpaper_home),
    "LOCK" to context.getString(R.string.opt_wallpaper_lock),
)

// HTTP verbs are protocol tokens, not localizable.
private val httpMethods = listOf("GET" to "GET", "POST" to "POST", "PUT" to "PUT", "DELETE" to "DELETE", "PATCH" to "PATCH")

fun ActionType.info(context: Context): ActionInfo = when (this) {
    ActionType.TOAST -> ActionInfo(
        context.getString(R.string.act_toast_label), Icons.Outlined.Chat, context.getString(R.string.act_toast_desc),
        listOf(ConfigField.TextInput("message", context.getString(R.string.cfg_message))),
    )
    ActionType.NOTIFICATION -> ActionInfo(
        context.getString(R.string.act_notification_label), Icons.Outlined.Notifications, context.getString(R.string.act_notification_desc),
        listOf(
            ConfigField.TextInput("title", context.getString(R.string.cfg_title)),
            ConfigField.TextInput("message", context.getString(R.string.cfg_message), multiline = true),
        ),
    )
    ActionType.DELAY -> ActionInfo(
        context.getString(R.string.act_delay_label), Icons.Outlined.Timer, context.getString(R.string.act_delay_desc),
        listOf(
            ConfigField.UnitSlider(
                key = "duration_value",
                label = context.getString(R.string.cfg_delay_duration),
                unitKey = "duration_unit",
                units = listOf(
                    ConfigField.UnitSlider.UnitDef("MS", "ms", 0, 60_000, "ms"),
                    ConfigField.UnitSlider.UnitDef("SEC", "s", 0, 3_600, "s"),
                ),
            ),
        ),
    )
    ActionType.WIFI_TOGGLE -> ActionInfo(
        context.getString(R.string.act_wifi_label), Icons.Outlined.Wifi, context.getString(R.string.act_wifi_desc),
        listOf(ConfigField.Dropdown("state", context.getString(R.string.cfg_action), toggleOptions(context))),
    )
    ActionType.BLUETOOTH_TOGGLE -> ActionInfo(
        context.getString(R.string.act_bluetooth_label), Icons.Outlined.Bluetooth, context.getString(R.string.act_bluetooth_desc),
        listOf(ConfigField.Dropdown("state", context.getString(R.string.cfg_action), toggleOptions(context))),
    )
    ActionType.DND_TOGGLE -> ActionInfo(
        context.getString(R.string.act_dnd_label), Icons.Outlined.DoNotDisturb, context.getString(R.string.act_dnd_desc),
        listOf(ConfigField.Dropdown("state", context.getString(R.string.cfg_action), toggleOptions(context))),
    )
    ActionType.VOLUME_ADJUST -> ActionInfo(
        context.getString(R.string.act_volume_label), Icons.Outlined.VolumeUp, context.getString(R.string.act_volume_desc),
        listOf(
            ConfigField.Dropdown(
                "stream", context.getString(R.string.cfg_stream), listOf(
                    "RING" to context.getString(R.string.opt_ringtone),
                    "MEDIA" to context.getString(R.string.opt_media),
                    "ALARM" to context.getString(R.string.opt_alarm),
                    "NOTIFICATION" to context.getString(R.string.opt_notification),
                ),
            ),
            ConfigField.Slider("level", context.getString(R.string.cfg_volume_level), 0, 15),
        ),
    )
    ActionType.OPEN_APP -> ActionInfo(
        context.getString(R.string.act_open_app_label), Icons.Outlined.TravelExplore, context.getString(R.string.act_open_app_desc),
        listOf(ConfigField.AppPicker("package_name", context.getString(R.string.cfg_app))),
    )
    ActionType.OPEN_URL -> ActionInfo(
        context.getString(R.string.act_open_url_label), Icons.Outlined.OpenInBrowser, context.getString(R.string.act_open_url_desc),
        listOf(ConfigField.TextInput("url", context.getString(R.string.cfg_url), hint = "https://")),
    )
    ActionType.TTS -> ActionInfo(
        context.getString(R.string.act_tts_label), Icons.Outlined.RecordVoiceOver, context.getString(R.string.act_tts_desc),
        listOf(
            ConfigField.InfoText(
                "_lang",
                context.getString(R.string.cfg_info_language_label),
                context.getString(R.string.cfg_info_language_body),
            ),
            ConfigField.TextInput("text", context.getString(R.string.cfg_text_to_speak), multiline = true),
        ),
    )
    ActionType.CLIPBOARD_COPY -> ActionInfo(
        context.getString(R.string.act_clipboard_label), Icons.Outlined.ContentCopy, context.getString(R.string.act_clipboard_desc),
        listOf(ConfigField.TextInput("text", context.getString(R.string.cfg_text_to_copy), multiline = true)),
    )
    ActionType.HTTP_REQUEST -> ActionInfo(
        context.getString(R.string.act_http_label), Icons.Outlined.Cloud, context.getString(R.string.act_http_desc),
        listOf(
            ConfigField.TextInput("url", context.getString(R.string.cfg_url), hint = "https://api.example.com/endpoint"),
            ConfigField.Dropdown("method", context.getString(R.string.cfg_method), httpMethods),
            ConfigField.TextInput("body", context.getString(R.string.cfg_body_optional), multiline = true),
        ),
    )
    ActionType.BRIGHTNESS_ADJUST -> ActionInfo(
        context.getString(R.string.act_brightness_label), Icons.Outlined.Brightness6, context.getString(R.string.act_brightness_desc),
        listOf(
            ConfigField.InfoText(
                "_perm",
                context.getString(R.string.cfg_info_permission_label),
                context.getString(R.string.cfg_info_permission_body),
                isWarning = true,
            ),
            ConfigField.Toggle("extra_dim", context.getString(R.string.cfg_extra_dim), context.getString(R.string.cfg_extra_dim_desc)),
            ConfigField.UnitSlider(
                key = "level",
                label = context.getString(R.string.cfg_brightness_level),
                unitKey = "brightness_unit",
                units = listOf(
                    ConfigField.UnitSlider.UnitDef("PCT", "%", 0, 100, "%"),
                    ConfigField.UnitSlider.UnitDef("RAW", "raw", 0, 255, ""),
                ),
            ),
        ),
    )
    ActionType.AIRPLANE_TOGGLE -> ActionInfo(
        context.getString(R.string.act_airplane_label), Icons.Outlined.AirplanemodeActive, context.getString(R.string.act_airplane_desc),
        listOf(ConfigField.Dropdown("state", context.getString(R.string.cfg_action), toggleOptions(context))),
    )
    ActionType.MEDIA_PLAY_PAUSE -> ActionInfo(
        context.getString(R.string.act_media_label), Icons.Outlined.PlayArrow, context.getString(R.string.act_media_desc),
        listOf(
            ConfigField.Dropdown("action", context.getString(R.string.cfg_action), listOf(
                "PLAY" to context.getString(R.string.opt_play),
                "PAUSE" to context.getString(R.string.opt_pause),
                "TOGGLE" to context.getString(R.string.opt_play_pause),
            )),
        ),
    )
    ActionType.SEND_SMS -> ActionInfo(
        context.getString(R.string.act_send_sms_label), Icons.Outlined.Sms, context.getString(R.string.act_send_sms_desc),
        listOf(
            ConfigField.TextInput("number", context.getString(R.string.cfg_phone_number)),
            ConfigField.TextInput("message", context.getString(R.string.cfg_message), multiline = true),
        ),
    )
    ActionType.CALL_PHONE -> ActionInfo(
        context.getString(R.string.act_call_label), Icons.Outlined.Call, context.getString(R.string.act_call_desc),
        listOf(ConfigField.TextInput("number", context.getString(R.string.cfg_phone_number))),
    )
    ActionType.SET_VARIABLE -> ActionInfo(
        context.getString(R.string.act_set_variable_label), Icons.Outlined.Code, context.getString(R.string.act_set_variable_desc),
        listOf(
            ConfigField.TextInput("variable_name", context.getString(R.string.cfg_variable_name)),
            ConfigField.TextInput("value", context.getString(R.string.cfg_value)),
        ),
    )
    ActionType.WRITE_FILE -> ActionInfo(
        context.getString(R.string.act_write_file_label), Icons.Outlined.SaveAlt, context.getString(R.string.act_write_file_desc),
        listOf(
            ConfigField.TextInput("path", context.getString(R.string.cfg_file_path), hint = "/storage/emulated/0/nexflow/output.txt"),
            ConfigField.TextInput("content", context.getString(R.string.cfg_content), multiline = true),
        ),
    )
    ActionType.SHARE -> ActionInfo(
        context.getString(R.string.act_share_label), Icons.Outlined.Share, context.getString(R.string.act_share_desc),
        listOf(ConfigField.TextInput("text", context.getString(R.string.cfg_text_to_share), multiline = true)),
    )
    ActionType.SCREENSHOT -> ActionInfo(
        context.getString(R.string.act_screenshot_label), Icons.Outlined.Screenshot, context.getString(R.string.act_screenshot_desc),
        listOf(
            ConfigField.InfoText(
                "_screen_info",
                context.getString(R.string.cfg_info_requirement_label),
                context.getString(R.string.cfg_info_screenshot_body),
                isWarning = true,
            ),
        ),
    )
    ActionType.SIMULATE_TAP -> ActionInfo(
        context.getString(R.string.act_tap_label), Icons.Outlined.TouchApp, context.getString(R.string.act_tap_desc),
        listOf(
            ConfigField.TextInput("x", context.getString(R.string.cfg_tap_x), hint = "540"),
            ConfigField.TextInput("y", context.getString(R.string.cfg_tap_y), hint = "1200"),
            ConfigField.InfoText(
                "_tap_info",
                context.getString(R.string.cfg_info_requirement_label),
                context.getString(R.string.cfg_info_tap_body),
            ),
        ),
    )
    ActionType.SPEAKERPHONE -> ActionInfo(
        context.getString(R.string.act_speaker_label), Icons.Outlined.SpeakerPhone, context.getString(R.string.act_speaker_desc),
        listOf(
            ConfigField.Dropdown(
                "state", context.getString(R.string.cfg_action), listOf(
                    "ON" to context.getString(R.string.opt_on),
                    "OFF" to context.getString(R.string.opt_off),
                ),
            ),
        ),
    )
    ActionType.SET_WALLPAPER -> ActionInfo(
        context.getString(R.string.act_set_wallpaper_label), Icons.Outlined.Wallpaper, context.getString(R.string.act_set_wallpaper_desc),
        listOf(
            ConfigField.ImagePicker("image", context.getString(R.string.cfg_wallpaper_image)),
            ConfigField.Dropdown("target", context.getString(R.string.cfg_wallpaper_target), wallpaperTargets(context)),
            ConfigField.InfoText(
                "_wallpaper_info",
                context.getString(R.string.cfg_info_note_label),
                context.getString(R.string.cfg_info_wallpaper_body),
            ),
        ),
    )
    ActionType.IF_BLOCK -> ActionInfo(
        context.getString(R.string.act_if_label), Icons.Outlined.MergeType, context.getString(R.string.act_if_desc),
        listOf(
            ConfigField.InfoText(
                "_expr_help",
                context.getString(R.string.cfg_info_condition_label),
                context.getString(R.string.cfg_info_condition_body),
            ),
            ConfigField.TextInput("expression", context.getString(R.string.cfg_condition), hint = "{{battery}} < 20"),
        ),
    )
    ActionType.ELSE_BLOCK -> ActionInfo(
        context.getString(R.string.act_else_label), Icons.Outlined.CallSplit, context.getString(R.string.act_else_desc), emptyList(),
    )
    ActionType.END_IF -> ActionInfo(
        context.getString(R.string.act_end_if_label), Icons.Outlined.Done, context.getString(R.string.act_end_if_desc), emptyList(),
    )
    ActionType.REPEAT_BLOCK -> ActionInfo(
        context.getString(R.string.act_repeat_label), Icons.Outlined.Repeat, context.getString(R.string.act_repeat_desc),
        listOf(ConfigField.Slider("count", context.getString(R.string.cfg_repeat_count), 1, 100, "×")),
    )
    ActionType.END_REPEAT -> ActionInfo(
        context.getString(R.string.act_end_repeat_label), Icons.Outlined.Done, context.getString(R.string.act_end_repeat_desc), emptyList(),
    )
    ActionType.SHOW_MENU -> ActionInfo(
        context.getString(R.string.act_show_menu_label), Icons.Outlined.List, context.getString(R.string.act_show_menu_desc),
        listOf(
            ConfigField.TextInput("title", context.getString(R.string.fd_prompt_title), hint = context.getString(R.string.fd_menu_title_placeholder)),
            ConfigField.MenuOptionList("options", context.getString(R.string.fd_menu_options)),
        ),
    )
    ActionType.MENU_CASE -> ActionInfo(
        context.getString(R.string.act_menu_case_label), Icons.Outlined.CallSplit, context.getString(R.string.act_menu_case_desc),
        listOf(ConfigField.TextInput("option", context.getString(R.string.cfg_option_label))),
    )
    ActionType.END_MENU -> ActionInfo(
        context.getString(R.string.act_end_menu_label), Icons.Outlined.Done, context.getString(R.string.act_end_menu_desc), emptyList(),
    )
}

fun ActionType.configSummary(context: Context, config: Map<String, String>): String = when (this) {
    ActionType.TOAST -> config["message"]?.take(40) ?: context.getString(R.string.sum_no_message)
    ActionType.NOTIFICATION -> config["title"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_notification)
    ActionType.DELAY -> {
        val v = config["duration_value"] ?: config["duration_ms"] ?: "0"
        val u = when (config["duration_unit"]) { "SEC" -> "s"; else -> "ms" }
        "$v$u"
    }
    ActionType.WIFI_TOGGLE,
    ActionType.BLUETOOTH_TOGGLE,
    ActionType.DND_TOGGLE,
    ActionType.AIRPLANE_TOGGLE -> context.getString(
        when (config["state"]) {
            "ON" -> R.string.opt_on
            "OFF" -> R.string.opt_off
            "TOGGLE" -> R.string.opt_toggle
            else -> R.string.sum_toggle
        },
    )
    ActionType.VOLUME_ADJUST -> {
        val s = context.getString(
            when (config["stream"]) {
                "RING" -> R.string.opt_ringtone
                "ALARM" -> R.string.opt_alarm
                "NOTIFICATION" -> R.string.opt_notification
                else -> R.string.opt_media
            },
        )
        context.getString(R.string.sum_stream_level, s, config["level"] ?: "?")
    }
    ActionType.OPEN_APP -> config["package_name"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_not_set)
    ActionType.OPEN_URL -> config["url"]?.take(40) ?: context.getString(R.string.sum_no_url)
    ActionType.TTS -> config["text"]?.take(40) ?: context.getString(R.string.sum_no_text)
    ActionType.CLIPBOARD_COPY -> config["text"]?.take(40) ?: context.getString(R.string.sum_no_text)
    ActionType.HTTP_REQUEST -> "${config["method"] ?: "GET"} ${config["url"]?.take(30) ?: context.getString(R.string.sum_no_url)}"
    ActionType.BRIGHTNESS_ADJUST -> {
        if (config["extra_dim"] == "true") context.getString(R.string.sum_extra_dim)
        else {
            val u = if (config["brightness_unit"] == "PCT") "%" else ""
            context.getString(R.string.sum_level, config["level"] ?: "?", u)
        }
    }
    ActionType.MEDIA_PLAY_PAUSE -> context.getString(
        when (config["action"]) {
            "PLAY" -> R.string.opt_play
            "PAUSE" -> R.string.opt_pause
            else -> R.string.opt_play_pause
        },
    )
    ActionType.SEND_SMS -> config["number"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_no_number)
    ActionType.CALL_PHONE -> config["number"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_no_number)
    ActionType.SET_VARIABLE -> "${config["variable_name"] ?: "?"} = ${config["value"] ?: "?"}"
    ActionType.WRITE_FILE -> config["path"]?.take(40) ?: context.getString(R.string.sum_no_path)
    ActionType.SHARE -> config["text"]?.take(40) ?: context.getString(R.string.sum_no_text)
    ActionType.SCREENSHOT -> context.getString(R.string.sum_take_screenshot)
    ActionType.SIMULATE_TAP -> "(${config["x"] ?: "?"}, ${config["y"] ?: "?"})"
    ActionType.SPEAKERPHONE -> context.getString(
        if (config["state"]?.uppercase() == "OFF") R.string.opt_off else R.string.opt_on,
    )
    ActionType.SET_WALLPAPER -> context.getString(
        when (config["target"]) {
            "HOME" -> R.string.opt_wallpaper_home
            "LOCK" -> R.string.opt_wallpaper_lock
            else -> R.string.opt_wallpaper_both
        },
    )
    ActionType.REPEAT_BLOCK -> "${config["count"] ?: "1"}×"
    ActionType.IF_BLOCK -> config["expression"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_no_condition)
    ActionType.ELSE_BLOCK -> context.getString(R.string.sum_otherwise)
    ActionType.END_IF -> context.getString(R.string.sum_closes_if)
    ActionType.END_REPEAT -> context.getString(R.string.sum_closes_repeat)
    ActionType.SHOW_MENU -> config["title"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_choose_option)
    ActionType.MENU_CASE -> config["option"]?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sum_option)
    ActionType.END_MENU -> context.getString(R.string.sum_closes_menu)
}
