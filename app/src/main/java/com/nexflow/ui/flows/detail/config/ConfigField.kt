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

sealed class ConfigField {
    abstract val key: String
    abstract val label: String

    data class TextInput(
        override val key: String,
        override val label: String,
        val hint: String = "",
        val multiline: Boolean = false,
    ) : ConfigField()

    data class Dropdown(
        override val key: String,
        override val label: String,
        /** List of (storedValue, displayLabel) pairs. */
        val options: List<Pair<String, String>>,
    ) : ConfigField()

    data class Slider(
        override val key: String,
        override val label: String,
        val min: Int,
        val max: Int,
        val unit: String = "",
    ) : ConfigField()

    data class TimePicker(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /** Searchable list of installed (non-system) apps; stores the package name. */
    data class AppPicker(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /**
     * Multi-select day-of-week chips.
     * Stored as comma-separated IDs: "MON,WED,FRI".
     * Only rendered when [showWhenKey] equals [showWhenValue] (both null = always shown).
     */
    data class DayPicker(
        override val key: String,
        override val label: String,
        val showWhenKey: String? = null,
        val showWhenValue: String? = null,
    ) : ConfigField()

    /**
     * Text input with an optional chip that pre-fills the currently connected Wi-Fi SSID.
     */
    data class WifiSsidInput(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /**
     * NFC tag scanner: shows instructions + a Scan button that activates NFC reader mode.
     * Stores the hex tag ID once a tag is detected.
     */
    data class NfcTagScan(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /**
     * A button that requests location permission and fills [latKey]/[lngKey] in the
     * values map with the device's last-known coordinates.
     */
    data class CurrentLocationButton(
        override val key: String = "_location_btn",
        override val label: String = "Use current location",
        val latKey: String,
        val lngKey: String,
    ) : ConfigField()

    /** Display-only informational text — captures no input. */
    data class InfoText(
        override val key: String,
        override val label: String,
        val body: String,
        val isWarning: Boolean = false,
    ) : ConfigField()

    /**
     * Dynamic list of text options for the SHOW_MENU action.
     * Stored as a JSON array string: ["Option A","Option B"].
     */
    data class MenuOptionList(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /** Boolean toggle (Switch); stores "true" or "false". */
    data class Toggle(
        override val key: String,
        override val label: String,
        val description: String = "",
    ) : ConfigField()

    /**
     * Slider with a selectable unit (e.g. ms/sec or %/raw).
     * [key] always stores the raw displayed value; [unitKey] stores the selected unit ID.
     * Switching units resets [key] to the new unit's [UnitDef.min].
     */
    data class UnitSlider(
        override val key: String,
        override val label: String,
        val unitKey: String,
        val units: List<UnitDef>,
    ) : ConfigField() {
        data class UnitDef(
            val id: String,
            val displayLabel: String,
            val min: Int,
            val max: Int,
            val suffix: String = "",
        )
    }
}
