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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon catalog for flows: every `Icons.Outlined.*` icon, keyed by the snake_case form of its
 * name (see the generated [FlowIconCatalog]).
 *
 * Icons are persisted by string key, so the set can only grow — never rename or remove a key,
 * or existing flows fall back to the default icon. Nothing here resolves an [ImageVector] until
 * it is actually drawn, so listing 2000+ keys costs a list of strings, not 2000 vectors.
 */
object FlowIcons {

    const val DEFAULT_KEY = "bolt"

    /** Every pickable key, ordered alphabetically. */
    val keys: List<String> get() = FlowIconCatalog.keys

    /**
     * Keys from the original 47-icon catalog whose name differed from the generated one, mapped
     * onto their current key. Flows (and exported JSON) written before the catalog was opened up
     * still carry these, so they must keep resolving.
     *
     * "camera" is deliberately absent: it now resolves to `Icons.Outlined.Camera` from the
     * catalog rather than the `CameraAlt` it used to mean — the catalog has to win, or picking
     * Camera in the picker would save a key that renders as something else.
     */
    private val LEGACY_ALIASES: Map<String, String> = mapOf(
        "airplane" to "airplanemode_active",
        "battery" to "battery_charging_full",
        "brightness" to "brightness_medium",
        "car" to "directions_car",
        "dnd" to "do_not_disturb_on",
        "file" to "description",
        "fitness" to "fitness_center",
        "game" to "sports_esports",
        "heart" to "favorite",
        "location" to "location_on",
        "music" to "music_note",
        "notification" to "notifications",
        "play" to "play_arrow",
        "shopping" to "shopping_cart",
        "sparkle" to "auto_awesome",
        "sunny" to "wb_sunny",
        "volume" to "volume_up",
    )

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

    fun vector(key: String?): ImageVector {
        if (key == null) return Icons.Outlined.Bolt
        FlowIconCatalog.vector(key)?.let { return it }
        val alias = LEGACY_ALIASES[key] ?: return Icons.Outlined.Bolt
        return FlowIconCatalog.vector(alias) ?: Icons.Outlined.Bolt
    }

    /**
     * Keys containing [query] as a substring, with separators stripped from both sides — so
     * "wbsunny", "wb sunny" and "wb_sunny" all find `wb_sunny`, and "car" finds both
     * `directions_car` and `car_rental`. A blank query returns the whole catalog.
     */
    fun search(query: String): List<String> {
        val needle = query.normalizeForSearch()
        if (needle.isEmpty()) return keys
        return searchIndex.mapNotNull { (key, haystack) -> key.takeIf { haystack.contains(needle) } }
    }

    /** key to its searchable form, built once — [search] runs on every keystroke. */
    private val searchIndex: List<Pair<String, String>> by lazy {
        keys.map { it to it.normalizeForSearch() }
    }

    private fun String.normalizeForSearch(): String =
        filter { it.isLetterOrDigit() }.lowercase()

    /** Parses a stored "#AARRGGBB" string; null/invalid yields null (use theme default). */
    fun color(hex: String?): Color? = hex
        ?.removePrefix("#")
        ?.toULongOrNull(16)
        ?.let { Color(it.toLong() or 0xFF000000) }
}
