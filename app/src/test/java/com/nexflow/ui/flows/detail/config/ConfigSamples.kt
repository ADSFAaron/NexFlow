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

/**
 * Builds a plausible config map from a field list, the same way the ConfigDialog
 * would after a user fills in every input.
 */
fun sampleConfig(fields: List<ConfigField>): Map<String, String> {
    val values = mutableMapOf<String, String>()
    fields.forEach { field ->
        when (field) {
            is ConfigField.TextInput -> values[field.key] = "sample"
            is ConfigField.Dropdown -> values[field.key] = field.options.first().first
            is ConfigField.Slider -> values[field.key] = field.min.toString()
            is ConfigField.TimePicker -> values[field.key] = "08:30"
            is ConfigField.AppPicker -> values[field.key] = "com.example.app"
            is ConfigField.ShortcutPicker -> {
                values[field.key] = "intent:#Intent;action=android.intent.action.VIEW;package=com.example.app;end"
                values[field.labelKey] = "Sample shortcut"
                values[field.packageKey] = "com.example.app"
            }
            is ConfigField.DayPicker -> values[field.key] = "MON,FRI"
            is ConfigField.WifiSsidInput -> values[field.key] = "HomeWifi"
            is ConfigField.NfcTagScan -> values[field.key] = "04A1B2C3"
            is ConfigField.ImagePicker -> values[field.key] = "/data/user/0/com.nexflow/files/wallpapers/wp_sample.img"
            is ConfigField.MenuOptionList -> values[field.key] = """["Option A","Option B"]"""
            is ConfigField.CurrentLocationButton -> {
                values[field.latKey] = "25.033964"
                values[field.lngKey] = "121.564468"
            }
            is ConfigField.InfoText -> Unit // display-only
            is ConfigField.Toggle -> values[field.key] = "true"
            is ConfigField.UnitSlider -> {
                val unit = field.units.first()
                values[field.unitKey] = unit.id
                values[field.key] = unit.min.toString()
            }
        }
    }
    return values
}

/** Field types that capture user input (and so must have a unique, non-blank key). */
fun ConfigField.capturesInput(): Boolean = this !is ConfigField.InfoText
