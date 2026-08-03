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

    /**
     * Conditional visibility: the field is only rendered (and only then captures input) while the
     * value under [showWhenKey] equals [showWhenValue]. A null key means "always shown".
     *
     * Fields that never need it inherit the default rather than declaring it, so a field list
     * reads as a flat list of inputs until one genuinely depends on another.
     */
    open val showWhenKey: String? get() = null
    open val showWhenValue: String? get() = null

    /** Whether this field should be shown given the dialog's current values. */
    fun isVisible(values: Map<String, String>): Boolean {
        val key = showWhenKey ?: return true
        return values[key] == showWhenValue
    }

    data class TextInput(
        override val key: String,
        override val label: String,
        val hint: String = "",
        val multiline: Boolean = false,
        override val showWhenKey: String? = null,
        override val showWhenValue: String? = null,
    ) : ConfigField()

    data class Dropdown(
        override val key: String,
        override val label: String,
        /** List of (storedValue, displayLabel) pairs. */
        val options: List<Pair<String, String>>,
    ) : ConfigField()

    /**
     * Structured boolean-condition editor: `value A` [operator] `value B`.
     * Serialized back to the interpreter's expression string (e.g. `{{battery}} < 20`).
     * Leaving `value B` empty stores just `value A`, which the interpreter treats as
     * a truthy check. Both operands accept variable references via the insert menu, so
     * the user never has to hand-type `{{name}}` or the comparison operator.
     */
    data class ConditionInput(
        override val key: String,
        override val label: String,
    ) : ConfigField()

    /**
     * Free-text input for a variable *name* (as written, e.g. `counter` or `g:shared`) with a
     * dropdown to pick an existing local or global variable. Unlike the reference insert menu it
     * stores the bare name, not a `{{...}}` token — used by SET_VARIABLE, which can create a new
     * *local* variable by typing a name that doesn't exist yet. A `g:` name must already exist as
     * a global: the dialog blocks saving an unknown one, and the engine fails the run on it.
     */
    data class VariableNameInput(
        override val key: String,
        override val label: String,
        val hint: String = "",
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
        override val showWhenKey: String? = null,
        override val showWhenValue: String? = null,
    ) : ConfigField()

    /**
     * Two-level picker for another app's shortcuts (manifest shortcuts parsed from
     * shortcuts.xml plus ACTION_CREATE_SHORTCUT configuration activities).
     * [key] stores the launch intent as an intent: URI; [labelKey] the display label;
     * [packageKey] the source app's package name.
     */
    data class ShortcutPicker(
        override val key: String,
        override val label: String,
        val labelKey: String,
        val packageKey: String,
        override val showWhenKey: String? = null,
        override val showWhenValue: String? = null,
    ) : ConfigField()

    /**
     * Multi-select day-of-week chips.
     * Stored as comma-separated IDs: "MON,WED,FRI".
     */
    data class DayPicker(
        override val key: String,
        override val label: String,
        override val showWhenKey: String? = null,
        override val showWhenValue: String? = null,
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

    /**
     * Picks raw screen coordinates by tapping a screenshot instead of making the user read
     * numbers off "Pointer location" in Developer options. The user shoots a normal system
     * screenshot of the target screen, picks it here, and taps the spot they mean; the tap's
     * position *as a fraction of the image* is scaled to the device's display size.
     *
     * Writes into [xKey]/[yKey] (and [endXKey]/[endYKey] when set, which also switches the
     * picker to two-point swipe mode) — [key] itself stores nothing.
     */
    data class ScreenCoordinatePicker(
        override val key: String,
        override val label: String,
        val xKey: String,
        val yKey: String,
        val endXKey: String? = null,
        val endYKey: String? = null,
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

    /**
     * System photo-picker button (no storage permission needed). Stores the selected
     * image's content:// URI as a string, taking a persistable read grant so the flow
     * can still read it later from the background.
     */
    data class ImagePicker(
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
