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
package com.nexflow.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Built-in icon catalog for flows. Icons are persisted by string key so the set can only
 * grow — never rename or remove a key, or existing flows/widgets will fall back to default.
 */
object FlowIcons {

    const val DEFAULT_KEY = "bolt"

    /** Ordered for display in the picker grid. */
    val catalog: List<Pair<String, ImageVector>> = listOf(
        "bolt" to Icons.Outlined.Bolt,
        "sparkle" to Icons.Outlined.AutoAwesome,
        "play" to Icons.Outlined.PlayArrow,
        "star" to Icons.Outlined.Star,
        "heart" to Icons.Outlined.Favorite,
        "rocket" to Icons.Outlined.Rocket,
        "alarm" to Icons.Outlined.Alarm,
        "schedule" to Icons.Outlined.Schedule,
        "timer" to Icons.Outlined.Timer,
        "bedtime" to Icons.Outlined.Bedtime,
        "sunny" to Icons.Outlined.WbSunny,
        "home" to Icons.Outlined.Home,
        "work" to Icons.Outlined.Work,
        "school" to Icons.Outlined.School,
        "location" to Icons.Outlined.LocationOn,
        "car" to Icons.Outlined.DirectionsCar,
        "flight" to Icons.Outlined.Flight,
        "wifi" to Icons.Outlined.Wifi,
        "bluetooth" to Icons.Outlined.Bluetooth,
        "nfc" to Icons.Outlined.Nfc,
        "battery" to Icons.Outlined.BatteryChargingFull,
        "power" to Icons.Outlined.Power,
        "brightness" to Icons.Outlined.BrightnessMedium,
        "volume" to Icons.Outlined.VolumeUp,
        "dnd" to Icons.Outlined.DoNotDisturbOn,
        "airplane" to Icons.Outlined.AirplanemodeActive,
        "notification" to Icons.Outlined.Notifications,
        "sms" to Icons.Outlined.Sms,
        "call" to Icons.Outlined.Call,
        "music" to Icons.Outlined.MusicNote,
        "headphones" to Icons.Outlined.Headphones,
        "camera" to Icons.Outlined.CameraAlt,
        "screenshot" to Icons.Outlined.Screenshot,
        "share" to Icons.Outlined.Share,
        "link" to Icons.Outlined.Link,
        "cloud" to Icons.Outlined.Cloud,
        "code" to Icons.Outlined.Code,
        "file" to Icons.Outlined.Description,
        "folder" to Icons.Outlined.Folder,
        "lock" to Icons.Outlined.Lock,
        "settings" to Icons.Outlined.Settings,
        "lightbulb" to Icons.Outlined.Lightbulb,
        "fitness" to Icons.Outlined.FitnessCenter,
        "coffee" to Icons.Outlined.Coffee,
        "pets" to Icons.Outlined.Pets,
        "shopping" to Icons.Outlined.ShoppingCart,
        "game" to Icons.Outlined.SportsEsports,
    )

    private val byKey: Map<String, ImageVector> = catalog.toMap()

    /** Shortcuts-style background palette, persisted as ARGB hex strings. */
    val colorPalette: List<String> = listOf(
        "#FFE5484D", // red
        "#FFF76B15", // orange
        "#FFFFB224", // amber
        "#FF46A758", // green
        "#FF12A594", // teal
        "#FF00A2C7", // cyan
        "#FF3E63DD", // blue
        "#FF6E56CF", // indigo
        "#FFAB4ABA", // purple
        "#FFE93D82", // pink
        "#FFAD7F58", // brown
        "#FF8D8D8D", // gray
    )

    fun vector(key: String?): ImageVector = byKey[key] ?: Icons.Outlined.Bolt

    /** Parses a stored "#AARRGGBB" string; null/invalid yields null (use theme default). */
    fun color(hex: String?): Color? = hex
        ?.removePrefix("#")
        ?.toULongOrNull(16)
        ?.let { Color(it.toLong() or 0xFF000000) }
}
