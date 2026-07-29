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
package com.nexflow.ai

import android.content.Context
import com.nexflow.FlavorFeatures
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.info
import java.util.Locale

/**
 * Generates the Gemini system instruction from the authoritative trigger/action catalogs
 * ([TriggerType.info]/[ActionType.info]), so the prompt can never drift from what the
 * executors actually accept. Also exposes the per-type config-key/value contract used by
 * [FlowDraftMapper] to validate what Gemini sends back.
 */
object AiCatalog {

    fun systemInstruction(context: Context): String {
        val locale = Locale.getDefault()
        return """
            |You are the flow-building assistant inside NexFlow, an Android automation app.
            |A flow = one or more triggers (combined with trigger_logic ANY or ALL) plus an ordered list of actions.
            |
            |Rules:
            |- You ONLY help with building and modifying NexFlow automation flows. If the user asks about anything else (general knowledge, coding, news, chit-chat, other apps), politely decline in ONE short sentence and remind them what you can do. Never answer the off-topic question itself, even partially.
            |- Converse in the user's language (current app locale: ${locale.toLanguageTag()}).
            |- If the request is ambiguous or missing a required detail, ask ONE short clarifying question. Otherwise call create_flow directly. Never describe JSON to the user.
            |- Control flow is a FLAT ordered action list with balanced markers: IF_BLOCK … optional ELSE_BLOCK … END_IF; REPEAT_BLOCK … END_REPEAT; SHOW_MENU, then for each option a MENU_CASE (config.option = the option text) followed by its actions, then END_MENU.
            |- Text values may reference variables as {{name}}. IF_BLOCK expression example: {{battery}} < 20.
            |- Whenever a config key needs an app package name (OPEN_APP, APP_LAUNCH, NOTIFICATION_RECEIVED), you MUST call search_installed_apps first and use a returned package_name. Never guess package names.
            |- enum config values must match the listed values exactly (uppercase). Numbers must stay inside the listed range.
            |- Optional keys may be omitted. Keys marked "leave empty" cannot be filled by you — omit them and tell the user to complete that field in the editor.
            |- After create_flow succeeds, summarize in one or two sentences what the flow does and mention that it was saved in a disabled state so the user can review it, grant any permissions, and enable it.
            |- If the user later asks for changes to the flow you created, call create_flow again with the COMPLETE revised flow (every trigger and action, not just the changed parts) — the app updates the existing flow in place.
            |
            |TRIGGER TYPES:
            |${catalogText(context, triggers = true)}
            |
            |ACTION TYPES:
            |${catalogText(context, triggers = false)}
        """.trimMargin()
    }

    // Flavor-hidden types (e.g. SMS on play) are omitted so Gemini can't build flows the
    // picker itself refuses to show.
    private fun catalogText(context: Context, triggers: Boolean): String =
        if (triggers) {
            TriggerType.entries.filter { it !in FlavorFeatures.hiddenTriggerTypes }.joinToString("\n") { type ->
                val info = type.info(context)
                "- ${type.name}: ${info.description}${fieldsSpec(info.fields)}"
            }
        } else {
            ActionType.entries.filter { it !in FlavorFeatures.hiddenActionTypes }.joinToString("\n") { type ->
                val info = type.info(context)
                "- ${type.name}: ${info.description}${fieldsSpec(info.fields)}"
            }
        }

    private fun fieldsSpec(fields: List<ConfigField>): String {
        val specs = fields.mapNotNull { fieldSpec(it) }
        return if (specs.isEmpty()) "" else " | config: ${specs.joinToString(", ")}"
    }

    /** Compact one-fragment spec for a field, or null for display-only fields. */
    fun fieldSpec(field: ConfigField): String? = when (field) {
        is ConfigField.TextInput -> "${field.key}:text"
        is ConfigField.VariableNameInput -> "${field.key}:variable-name (bare name; prefix g: for a global)"
        is ConfigField.ConditionInput ->
            "${field.key}:condition\"{{var}} < 20\" (operators ==,!=,<,<=,>,>=; empty right side = truthy check)"
        is ConfigField.Dropdown -> "${field.key}:enum[${field.options.joinToString("|") { it.first }}]"
        is ConfigField.Slider -> "${field.key}:int[${field.min}..${field.max}]"
        is ConfigField.TimePicker -> "${field.key}:time\"HH:mm\""
        is ConfigField.AppPicker -> "${field.key}:package (resolve via search_installed_apps)"
        is ConfigField.ShortcutPicker -> "${field.key}: leave empty (user picks the shortcut in the editor)"
        is ConfigField.DayPicker -> buildString {
            append("${field.key}:days\"MON,TUE,WED,THU,FRI,SAT,SUN\" comma-separated")
            if (field.showWhenKey != null) append(" (only when ${field.showWhenKey}=${field.showWhenValue})")
        }
        is ConfigField.WifiSsidInput -> "${field.key}:text"
        is ConfigField.NfcTagScan -> "${field.key}: leave empty (user scans the tag in the editor)"
        is ConfigField.MenuOptionList -> "${field.key}:json array of option strings e.g. [\"A\",\"B\"]"
        is ConfigField.ImagePicker -> "${field.key}: leave empty (user picks the image in the editor)"
        is ConfigField.Toggle -> "${field.key}:bool[true|false]"
        is ConfigField.UnitSlider ->
            "${field.key}:int + ${field.unitKey}:enum[" +
                field.units.joinToString("|") { "${it.id}(${it.min}..${it.max})" } + "]"
        is ConfigField.CurrentLocationButton, is ConfigField.InfoText,
        is ConfigField.ScreenCoordinatePicker,
        -> null
    }

    /** Config keys Gemini is allowed to set for this field list. */
    fun capturableKeys(fields: List<ConfigField>): Set<String> = buildSet {
        fields.forEach { field ->
            when (field) {
                is ConfigField.CurrentLocationButton, is ConfigField.InfoText,
                is ConfigField.ScreenCoordinatePicker,
                -> Unit
                is ConfigField.UnitSlider -> {
                    add(field.key)
                    add(field.unitKey)
                }
                else -> add(field.key)
            }
        }
    }

    /** Exact allowed values for an enum-like key, or null when free-form. */
    fun allowedValues(fields: List<ConfigField>, key: String): Set<String>? {
        fields.forEach { field ->
            when (field) {
                is ConfigField.Dropdown ->
                    if (field.key == key) return field.options.map { it.first }.toSet()
                is ConfigField.Toggle ->
                    if (field.key == key) return setOf("true", "false")
                is ConfigField.UnitSlider ->
                    if (field.unitKey == key) return field.units.map { it.id }.toSet()
                else -> Unit
            }
        }
        return null
    }

    /** Inclusive int range for a numeric key, or null when not range-bound. */
    fun numericRange(fields: List<ConfigField>, key: String): IntRange? {
        fields.forEach { field ->
            if (field is ConfigField.Slider && field.key == key) return field.min..field.max
        }
        return null
    }
}
