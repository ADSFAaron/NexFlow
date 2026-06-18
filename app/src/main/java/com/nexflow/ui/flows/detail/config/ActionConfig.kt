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
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.core.automation.model.ActionType

data class ActionInfo(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val fields: List<ConfigField>,
)

/** Picker groupings, in display order. */
enum class ActionCategory(val label: String) {
    CONTROL_FLOW("Control Flow"),
    CONNECTIVITY("Connectivity"),
    DEVICE("Device"),
    COMMUNICATION("Communication"),
    ALERTS("Alerts"),
    WEB("Web"),
    APPS_MEDIA("Apps & Media"),
    FILES_DATA("Files & Data"),
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
        ActionType.DND_TOGGLE, ActionType.SCREENSHOT,
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

private val toggleOptions = listOf("ON" to "On", "OFF" to "Off", "TOGGLE" to "Toggle")
private val httpMethods = listOf("GET" to "GET", "POST" to "POST", "PUT" to "PUT", "DELETE" to "DELETE", "PATCH" to "PATCH")

val ActionType.info: ActionInfo
    get() = when (this) {
        ActionType.TOAST -> ActionInfo(
            "Toast", Icons.Outlined.Chat, "Show a brief on-screen message",
            listOf(ConfigField.TextInput("message", "Message")),
        )
        ActionType.NOTIFICATION -> ActionInfo(
            "Notification", Icons.Outlined.Notifications, "Post a system notification",
            listOf(
                ConfigField.TextInput("title", "Title"),
                ConfigField.TextInput("message", "Message", multiline = true),
            ),
        )
        ActionType.DELAY -> ActionInfo(
            "Delay", Icons.Outlined.Timer, "Wait before the next action",
            listOf(
                ConfigField.UnitSlider(
                    key = "duration_value",
                    label = "Delay duration",
                    unitKey = "duration_unit",
                    units = listOf(
                        ConfigField.UnitSlider.UnitDef("MS", "ms", 0, 60_000, "ms"),
                        ConfigField.UnitSlider.UnitDef("SEC", "s", 0, 3_600, "s"),
                    ),
                ),
            ),
        )
        ActionType.WIFI_TOGGLE -> ActionInfo(
            "Wi-Fi", Icons.Outlined.Wifi, "Turn Wi-Fi on or off",
            listOf(ConfigField.Dropdown("state", "Action", toggleOptions)),
        )
        ActionType.BLUETOOTH_TOGGLE -> ActionInfo(
            "Bluetooth", Icons.Outlined.Bluetooth, "Turn Bluetooth on or off",
            listOf(ConfigField.Dropdown("state", "Action", toggleOptions)),
        )
        ActionType.DND_TOGGLE -> ActionInfo(
            "Do Not Disturb", Icons.Outlined.DoNotDisturb, "Toggle Do Not Disturb mode",
            listOf(ConfigField.Dropdown("state", "Action", toggleOptions)),
        )
        ActionType.VOLUME_ADJUST -> ActionInfo(
            "Volume", Icons.Outlined.VolumeUp, "Adjust audio volume",
            listOf(
                ConfigField.Dropdown(
                    "stream", "Stream", listOf(
                        "RING" to "Ringtone", "MEDIA" to "Media",
                        "ALARM" to "Alarm", "NOTIFICATION" to "Notification",
                    ),
                ),
                ConfigField.Slider("level", "Volume level", 0, 15),
            ),
        )
        ActionType.OPEN_APP -> ActionInfo(
            "Open app", Icons.Outlined.TravelExplore, "Launch an application",
            listOf(ConfigField.AppPicker("package_name", "App")),
        )
        ActionType.OPEN_URL -> ActionInfo(
            "Open URL", Icons.Outlined.OpenInBrowser, "Open a link in the browser",
            listOf(ConfigField.TextInput("url", "URL", hint = "https://")),
        )
        ActionType.TTS -> ActionInfo(
            "Text to speech", Icons.Outlined.RecordVoiceOver, "Speak text aloud",
            listOf(
                ConfigField.InfoText(
                    "_lang",
                    "Language",
                    "Uses the device's default TTS engine language. To change it go to Settings → Accessibility → Text-to-speech output.",
                ),
                ConfigField.TextInput("text", "Text to speak", multiline = true),
            ),
        )
        ActionType.CLIPBOARD_COPY -> ActionInfo(
            "Copy to clipboard", Icons.Outlined.ContentCopy, "Copy text to the clipboard",
            listOf(ConfigField.TextInput("text", "Text to copy", multiline = true)),
        )
        ActionType.HTTP_REQUEST -> ActionInfo(
            "HTTP request", Icons.Outlined.Cloud, "Send an HTTP request",
            listOf(
                ConfigField.TextInput("url", "URL", hint = "https://api.example.com/endpoint"),
                ConfigField.Dropdown("method", "Method", httpMethods),
                ConfigField.TextInput("body", "Body (optional)", multiline = true),
            ),
        )
        ActionType.BRIGHTNESS_ADJUST -> ActionInfo(
            "Brightness", Icons.Outlined.Brightness6, "Set screen brightness",
            listOf(
                ConfigField.InfoText(
                    "_perm",
                    "Permission",
                    "Requires 'Modify system settings' — grant it in the Permissions section of the Settings tab.",
                    isWarning = true,
                ),
                ConfigField.Toggle("extra_dim", "Extra dim", "Set screen to minimum backlight intensity"),
                ConfigField.UnitSlider(
                    key = "level",
                    label = "Brightness level",
                    unitKey = "brightness_unit",
                    units = listOf(
                        ConfigField.UnitSlider.UnitDef("PCT", "%", 0, 100, "%"),
                        ConfigField.UnitSlider.UnitDef("RAW", "raw", 0, 255, ""),
                    ),
                ),
            ),
        )
        ActionType.AIRPLANE_TOGGLE -> ActionInfo(
            "Airplane mode", Icons.Outlined.AirplanemodeActive, "Toggle airplane mode",
            listOf(ConfigField.Dropdown("state", "Action", toggleOptions)),
        )
        ActionType.MEDIA_PLAY_PAUSE -> ActionInfo(
            "Media", Icons.Outlined.PlayArrow, "Control media playback",
            listOf(
                ConfigField.Dropdown("action", "Action", listOf(
                    "PLAY" to "Play", "PAUSE" to "Pause", "TOGGLE" to "Play/Pause",
                )),
            ),
        )
        ActionType.SEND_SMS -> ActionInfo(
            "Send SMS", Icons.Outlined.Sms, "Send a text message",
            listOf(
                ConfigField.TextInput("number", "Phone number"),
                ConfigField.TextInput("message", "Message", multiline = true),
            ),
        )
        ActionType.CALL_PHONE -> ActionInfo(
            "Call phone", Icons.Outlined.Call, "Make a phone call",
            listOf(ConfigField.TextInput("number", "Phone number")),
        )
        ActionType.SET_VARIABLE -> ActionInfo(
            "Set variable", Icons.Outlined.Code, "Assign a value to a flow variable",
            listOf(
                ConfigField.TextInput("variable_name", "Variable name"),
                ConfigField.TextInput("value", "Value"),
            ),
        )
        ActionType.WRITE_FILE -> ActionInfo(
            "Write file", Icons.Outlined.SaveAlt, "Write text to a file",
            listOf(
                ConfigField.TextInput("path", "File path", hint = "/storage/emulated/0/nexflow/output.txt"),
                ConfigField.TextInput("content", "Content", multiline = true),
            ),
        )
        ActionType.SHARE -> ActionInfo(
            "Share", Icons.Outlined.Share, "Share text via other apps",
            listOf(ConfigField.TextInput("text", "Text to share", multiline = true)),
        )
        ActionType.SCREENSHOT -> ActionInfo(
            "Screenshot", Icons.Outlined.Screenshot, "Take a screenshot",
            listOf(
                ConfigField.InfoText(
                    "_screen_info",
                    "Requirement",
                    "Screenshot requires the NexFlow Accessibility Service to be enabled. " +
                        "Go to Settings → Accessibility → Installed apps → NexFlow and toggle it on. " +
                        "Requires Android 12 or higher.",
                    isWarning = true,
                ),
            ),
        )
        ActionType.IF_BLOCK -> ActionInfo(
            "If", Icons.Outlined.MergeType, "Run following actions only when a condition holds",
            listOf(
                ConfigField.InfoText(
                    "_expr_help",
                    "Condition",
                    "Examples: {{battery}} < 20 · {{status}} == ok · true. " +
                        "Supports ==, !=, <, >, <=, >= — numbers compare numerically. " +
                        "Close the block with an End If action.",
                ),
                ConfigField.TextInput("expression", "Condition", hint = "{{battery}} < 20"),
            ),
        )
        ActionType.ELSE_BLOCK -> ActionInfo("Else", Icons.Outlined.CallSplit, "Else block", emptyList())
        ActionType.END_IF -> ActionInfo("End If", Icons.Outlined.Done, "End of If block", emptyList())
        ActionType.REPEAT_BLOCK -> ActionInfo(
            "Repeat", Icons.Outlined.Repeat, "Repeat block",
            listOf(ConfigField.Slider("count", "Repeat count", 1, 100, "×")),
        )
        ActionType.END_REPEAT -> ActionInfo("End Repeat", Icons.Outlined.Done, "End of Repeat block", emptyList())
        ActionType.SHOW_MENU -> ActionInfo(
            "Show Menu", Icons.Outlined.List, "Ask the user to pick an option, then run the matching branch",
            listOf(
                ConfigField.TextInput("title", "Prompt / title", hint = "Choose a payment method"),
                ConfigField.MenuOptionList("options", "Menu options"),
            ),
        )
        ActionType.MENU_CASE -> ActionInfo(
            "Menu Case", Icons.Outlined.CallSplit, "Branch executed when the user picks this option",
            listOf(ConfigField.TextInput("option", "Option label")),
        )
        ActionType.END_MENU -> ActionInfo("End Menu", Icons.Outlined.Done, "End of Show Menu block", emptyList())
    }

fun ActionType.configSummary(config: Map<String, String>): String = when (this) {
    ActionType.TOAST -> config["message"]?.take(40) ?: "No message"
    ActionType.NOTIFICATION -> config["title"]?.takeIf { it.isNotBlank() }?.let { "$it" } ?: "Notification"
    ActionType.DELAY -> {
        val v = config["duration_value"] ?: config["duration_ms"] ?: "0"
        val u = when (config["duration_unit"]) { "SEC" -> "s" else -> "ms" }
        "$v$u"
    }
    ActionType.WIFI_TOGGLE,
    ActionType.BLUETOOTH_TOGGLE,
    ActionType.DND_TOGGLE,
    ActionType.AIRPLANE_TOGGLE -> config["state"]?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Toggle"
    ActionType.VOLUME_ADJUST -> {
        val s = config["stream"]?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Media"
        "$s: ${config["level"] ?: "?"}"
    }
    ActionType.OPEN_APP -> config["package_name"]?.takeIf { it.isNotBlank() } ?: "Not set"
    ActionType.OPEN_URL -> config["url"]?.take(40) ?: "No URL"
    ActionType.TTS -> config["text"]?.take(40) ?: "No text"
    ActionType.CLIPBOARD_COPY -> config["text"]?.take(40) ?: "No text"
    ActionType.HTTP_REQUEST -> "${config["method"] ?: "GET"} ${config["url"]?.take(30) ?: "No URL"}"
    ActionType.BRIGHTNESS_ADJUST -> {
        if (config["extra_dim"] == "true") "Extra dim"
        else {
            val u = if (config["brightness_unit"] == "PCT") "%" else ""
            "Level: ${config["level"] ?: "?"}$u"
        }
    }
    ActionType.MEDIA_PLAY_PAUSE -> config["action"]?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Toggle"
    ActionType.SEND_SMS -> config["number"]?.takeIf { it.isNotBlank() } ?: "No number"
    ActionType.CALL_PHONE -> config["number"]?.takeIf { it.isNotBlank() } ?: "No number"
    ActionType.SET_VARIABLE -> "${config["variable_name"] ?: "?"} = ${config["value"] ?: "?"}"
    ActionType.WRITE_FILE -> config["path"]?.take(40) ?: "No path"
    ActionType.SHARE -> config["text"]?.take(40) ?: "No text"
    ActionType.SCREENSHOT -> "Take screenshot"
    ActionType.REPEAT_BLOCK -> "${config["count"] ?: "1"}×"
    ActionType.IF_BLOCK -> config["expression"]?.takeIf { it.isNotBlank() } ?: "No condition"
    ActionType.ELSE_BLOCK -> "Otherwise"
    ActionType.END_IF -> "Closes If"
    ActionType.END_REPEAT -> "Closes Repeat"
    ActionType.SHOW_MENU -> config["title"]?.takeIf { it.isNotBlank() } ?: "Choose an option"
    ActionType.MENU_CASE -> config["option"]?.takeIf { it.isNotBlank() } ?: "Option"
    ActionType.END_MENU -> "Closes Menu"
}
