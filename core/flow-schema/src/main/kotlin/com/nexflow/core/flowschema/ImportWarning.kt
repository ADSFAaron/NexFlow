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
package com.nexflow.core.flowschema

/**
 * One thing the user has to know about an import, as data rather than a sentence.
 *
 * The converters live in pure-Kotlin modules with no resources and no `Context`, so a warning
 * they build cannot be a display string — it would arrive in English in an otherwise translated
 * app. It carries a [code] the UI turns into localized text, plus enough identity ([flowId],
 * [itemKind], [itemId]) for the UI to take the user straight to the item that needs attention.
 */
data class ImportWarning(
    val code: String,
    val args: List<String> = emptyList(),
    /** Filled in by the importer once the converted flow has an id. */
    val flowId: String? = null,
    val flowName: String = "",
    val itemKind: ImportItemKind? = null,
    val itemId: String? = null,
) {
    /** Whether the UI can offer to open the exact item this is about. */
    val isFixable: Boolean get() = flowId != null && itemId != null
}

enum class ImportItemKind { TRIGGER, CONDITION, ACTION }

/**
 * Warning codes. Each maps to one localized string; the order of [ImportWarning.args] is the
 * order of that string's format arguments.
 */
object ImportWarnings {

    // --- the class type itself ------------------------------------------------------------
    /** args: MacroDroid class name */
    const val UNSUPPORTED_TRIGGER = "unsupported_trigger"
    /** args: MacroDroid class name */
    const val UNSUPPORTED_CONDITION = "unsupported_condition"
    /** args: MacroDroid class name */
    const val UNSUPPORTED_ACTION = "unsupported_action"

    // --- the settings ---------------------------------------------------------------------
    const val SETTINGS_NOT_MAPPED = "settings_not_mapped"
    /** args: comma-separated MacroDroid option keys */
    const val SETTINGS_DROPPED = "settings_dropped"

    // --- per-feature differences ----------------------------------------------------------
    const val TAP_SWIPE_GITHUB_ONLY = "tap_swipe_github_only"
    /** args: option count */
    const val MENU_CASES_EMPTY = "menu_cases_empty"
    const val MENU_NO_BUTTONS = "menu_no_buttons"
    const val TIME_SECONDS_DROPPED = "time_seconds_dropped"
    const val BATTERY_ANY_CHANGE = "battery_any_change"
    const val CONNECTION_ADAPTER_STATE = "connection_adapter_state"
    /** args: number of apps, package kept */
    const val APP_TRIGGER_MULTIPLE = "app_trigger_multiple"
    const val APP_TRIGGER_CLOSED = "app_trigger_closed"
    const val EXCLUDE_UNSUPPORTED = "exclude_unsupported"
    const val NOTIFICATION_CLEARED = "notification_cleared"
    const val GEOFENCE_COORDINATES = "geofence_coordinates"
    const val SMS_PREPOPULATE = "sms_prepopulate"
    const val OPEN_APP_NO_PACKAGE = "open_app_no_package"
    const val SHORTCUT_NOT_PORTABLE = "shortcut_not_portable"
    const val WALLPAPER_NOT_PORTABLE = "wallpaper_not_portable"
    const val TOGGLE_OPTION_UNSUPPORTED = "toggle_option_unsupported"
    const val WRITE_FILE_APPEND = "write_file_append"
    /** args: the MacroDroid option label */
    const val MEDIA_OPTION_UNKNOWN = "media_option_unknown"
    const val SET_VARIABLE_COMPUTED = "set_variable_computed"
    const val LOOP_CONDITIONAL = "loop_conditional"
    const val UI_CLICK_BY_VIEW = "ui_click_by_view"
    const val SWIPE_PERCENTAGES = "swipe_percentages"
    const val HTTP_EXTRAS_DROPPED = "http_extras_dropped"

    // --- .flow import ---------------------------------------------------------------------
    /** args: variable name */
    const val GLOBAL_CREATED = "global_created"
    /** args: variable name */
    const val GLOBAL_UNDECLARED = "global_undeclared"
    /** args: comma-separated type names */
    const val UNKNOWN_CONDITION_TYPES = "unknown_condition_types"
    /** args: field, message */
    const val SCHEMA_ERROR = "schema_error"
}
