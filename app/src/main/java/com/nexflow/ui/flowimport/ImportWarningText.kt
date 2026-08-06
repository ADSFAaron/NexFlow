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
package com.nexflow.ui.flowimport

import android.content.Context
import com.nexflow.R
import com.nexflow.core.flowschema.ImportWarning
import com.nexflow.core.flowschema.ImportWarnings

/**
 * Turns an [ImportWarning] into what the user reads.
 *
 * The converters run in modules with no resources, so they emit codes; the mapping to translated
 * text lives here. Each entry is a headline (what happened) and a body (what to do about it) —
 * a wall of one-liners is what made the old import summary unreadable.
 */
private data class WarningText(val titleRes: Int, val bodyRes: Int)

private val TEXTS: Map<String, WarningText> = mapOf(
    ImportWarnings.UNSUPPORTED_TRIGGER to WarningText(R.string.iw_unsupported_trigger, R.string.iw_unsupported_trigger_body),
    ImportWarnings.UNSUPPORTED_CONDITION to WarningText(R.string.iw_unsupported_condition, R.string.iw_unsupported_condition_body),
    ImportWarnings.UNSUPPORTED_ACTION to WarningText(R.string.iw_unsupported_action, R.string.iw_unsupported_action_body),
    ImportWarnings.SETTINGS_NOT_MAPPED to WarningText(R.string.iw_settings_not_mapped, R.string.iw_settings_not_mapped_body),
    ImportWarnings.SETTINGS_DROPPED to WarningText(R.string.iw_settings_dropped, R.string.iw_settings_dropped_body),
    ImportWarnings.TAP_SWIPE_GITHUB_ONLY to WarningText(R.string.iw_tap_swipe, R.string.iw_tap_swipe_body),
    ImportWarnings.MENU_CASES_EMPTY to WarningText(R.string.iw_menu_cases, R.string.iw_menu_cases_body),
    ImportWarnings.MENU_NO_BUTTONS to WarningText(R.string.iw_menu_empty, R.string.iw_menu_empty_body),
    ImportWarnings.TIME_SECONDS_DROPPED to WarningText(R.string.iw_time_seconds, R.string.iw_time_seconds_body),
    ImportWarnings.BATTERY_ANY_CHANGE to WarningText(R.string.iw_battery_any, R.string.iw_battery_any_body),
    ImportWarnings.CONNECTION_ADAPTER_STATE to WarningText(R.string.iw_conn_adapter, R.string.iw_conn_adapter_body),
    ImportWarnings.APP_TRIGGER_MULTIPLE to WarningText(R.string.iw_app_multiple, R.string.iw_app_multiple_body),
    ImportWarnings.APP_TRIGGER_CLOSED to WarningText(R.string.iw_app_closed, R.string.iw_app_closed_body),
    ImportWarnings.EXCLUDE_UNSUPPORTED to WarningText(R.string.iw_exclude, R.string.iw_exclude_body),
    ImportWarnings.NOTIFICATION_CLEARED to WarningText(R.string.iw_notif_cleared, R.string.iw_notif_cleared_body),
    ImportWarnings.GEOFENCE_COORDINATES to WarningText(R.string.iw_geofence, R.string.iw_geofence_body),
    ImportWarnings.SMS_PREPOPULATE to WarningText(R.string.iw_sms_prefill, R.string.iw_sms_prefill_body),
    ImportWarnings.OPEN_APP_NO_PACKAGE to WarningText(R.string.iw_open_app_blank, R.string.iw_open_app_blank_body),
    ImportWarnings.SHORTCUT_NOT_PORTABLE to WarningText(R.string.iw_shortcut, R.string.iw_shortcut_body),
    ImportWarnings.WALLPAPER_NOT_PORTABLE to WarningText(R.string.iw_wallpaper, R.string.iw_wallpaper_body),
    ImportWarnings.TOGGLE_OPTION_UNSUPPORTED to WarningText(R.string.iw_toggle_option, R.string.iw_toggle_option_body),
    ImportWarnings.WRITE_FILE_APPEND to WarningText(R.string.iw_write_append, R.string.iw_write_append_body),
    ImportWarnings.MEDIA_OPTION_UNKNOWN to WarningText(R.string.iw_media_option, R.string.iw_media_option_body),
    ImportWarnings.SET_VARIABLE_COMPUTED to WarningText(R.string.iw_var_computed, R.string.iw_var_computed_body),
    ImportWarnings.LOOP_CONDITIONAL to WarningText(R.string.iw_loop_conditional, R.string.iw_loop_conditional_body),
    ImportWarnings.UI_CLICK_BY_VIEW to WarningText(R.string.iw_click_by_view, R.string.iw_click_by_view_body),
    ImportWarnings.SWIPE_PERCENTAGES to WarningText(R.string.iw_swipe_percent, R.string.iw_swipe_percent_body),
    ImportWarnings.HTTP_EXTRAS_DROPPED to WarningText(R.string.iw_http_extras, R.string.iw_http_extras_body),
    ImportWarnings.GLOBAL_CREATED to WarningText(R.string.iw_global_created, R.string.iw_global_created_body),
    ImportWarnings.GLOBAL_UNDECLARED to WarningText(R.string.iw_global_undeclared, R.string.iw_global_undeclared_body),
    ImportWarnings.UNKNOWN_CONDITION_TYPES to WarningText(R.string.iw_unknown_condition, R.string.iw_unknown_condition_body),
    ImportWarnings.SCHEMA_ERROR to WarningText(R.string.iw_schema_error, R.string.iw_schema_error_body),
)

/** Short line naming what happened, e.g. "This action has no NexFlow equivalent". */
fun ImportWarning.title(context: Context): String =
    TEXTS[code]?.let { context.getString(it.titleRes) } ?: code

/**
 * What it means and what to do, with the warning's own arguments filled in.
 *
 * A mismatch between the arguments a warning carries and the placeholders its translation uses
 * would otherwise crash the review dialog — in a translation NexFlow's own tests never render.
 */
fun ImportWarning.body(context: Context): String =
    TEXTS[code]?.let { text ->
        runCatching { context.getString(text.bodyRes, *args.toTypedArray()) }
            .getOrElse { context.getString(text.bodyRes) }
    } ?: args.joinToString()
