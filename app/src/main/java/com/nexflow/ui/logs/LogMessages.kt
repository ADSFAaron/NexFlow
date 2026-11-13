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
package com.nexflow.ui.logs

import android.content.Context
import androidx.annotation.StringRes
import com.nexflow.R
import com.nexflow.core.automation.LogText
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.ui.flows.detail.config.info

/**
 * Puts a stored run-log message into words in the reader's language — see [LogText].
 *
 * Text that is not a coded message (logs written before codes existed) is shown unchanged. A code
 * this build does not know — a log written by a newer version — falls back to its code and
 * arguments rather than disappearing, so the reason is still there to read.
 */
object LogMessages {

    /** What an argument holds, when it needs more than being printed as it is. */
    internal enum class Arg {
        /** An [ActionType] name, shown as the action's label. */
        ACTION,

        /** A [ConditionType] name, shown as the condition's label. */
        COND,

        /** Where a JSON path had got to: empty means the whole response. */
        WHERE,
    }

    internal class Spec(@param:StringRes val text: Int, val args: Map<Int, Arg> = emptyMap())

    /** Every code the app writes. A test keeps this in step with the code base. */
    internal val SPECS: Map<String, Spec> = mapOf(
        "literal_only" to Spec(R.string.logmsg_literal_only),
        "set_var_no_name" to Spec(R.string.logmsg_set_var_no_name),
        "set_var_eval" to Spec(R.string.logmsg_set_var_eval),
        "set_var_too_long" to Spec(R.string.logmsg_set_var_too_long),
        "no_executor" to Spec(R.string.logmsg_no_executor, mapOf(0 to Arg.ACTION)),
        "action_threw" to Spec(R.string.logmsg_action_threw, mapOf(0 to Arg.ACTION)),
        "unknown_global" to Spec(R.string.logmsg_unknown_global),
        "unexpected" to Spec(R.string.logmsg_unexpected),
        "cond_failed" to Spec(R.string.logmsg_cond_failed, mapOf(0 to Arg.COND)),
        "cond_not_met" to Spec(R.string.logmsg_cond_not_met, mapOf(0 to Arg.COND)),
        "cond_unknown_type" to Spec(R.string.logmsg_cond_unknown_type),
        "cond_no_evaluator" to Spec(R.string.logmsg_cond_no_evaluator, mapOf(0 to Arg.COND)),
        "speaker_none" to Spec(R.string.logmsg_speaker_none),
        "speaker_rejected" to Spec(R.string.logmsg_speaker_rejected),
        "screenshot_os" to Spec(R.string.logmsg_screenshot_os),
        "needs_accessibility" to Spec(R.string.logmsg_needs_accessibility, mapOf(0 to Arg.ACTION)),
        "screenshot_failed" to Spec(R.string.logmsg_screenshot_failed),
        "menu_no_options" to Spec(R.string.logmsg_menu_no_options),
        "tts_init" to Spec(R.string.logmsg_tts_init),
        "tts_error" to Spec(R.string.logmsg_tts_error),
        "volume_no_level" to Spec(R.string.logmsg_volume_no_level),
        "volume_dnd" to Spec(R.string.logmsg_volume_dnd),
        "write_no_path" to Spec(R.string.logmsg_write_no_path),
        "write_dotdot" to Spec(R.string.logmsg_write_dotdot),
        "write_private" to Spec(R.string.logmsg_write_private),
        "write_mediastore" to Spec(R.string.logmsg_write_mediastore),
        "write_failed" to Spec(R.string.logmsg_write_failed),
        "wallpaper_no_image" to Spec(R.string.logmsg_wallpaper_no_image),
        "wallpaper_gone" to Spec(R.string.logmsg_wallpaper_gone),
        "wallpaper_failed" to Spec(R.string.logmsg_wallpaper_failed),
        "notif_permission" to Spec(R.string.logmsg_notif_permission),
        "notif_tap_no_app" to Spec(R.string.logmsg_notif_tap_no_app),
        "notif_tap_app_missing" to Spec(R.string.logmsg_notif_tap_app_missing),
        "notif_tap_no_url" to Spec(R.string.logmsg_notif_tap_no_url),
        "notif_tap_no_shortcut" to Spec(R.string.logmsg_notif_tap_no_shortcut),
        "notif_tap_bad_shortcut" to Spec(R.string.logmsg_notif_tap_bad_shortcut),
        "notif_tap_blocked_shortcut" to Spec(R.string.logmsg_notif_tap_blocked_shortcut),
        "share_no_text" to Spec(R.string.logmsg_share_no_text),
        "share_failed" to Spec(R.string.logmsg_share_failed),
        "dnd_access" to Spec(R.string.logmsg_dnd_access),
        "url_none" to Spec(R.string.logmsg_url_none),
        "url_open_failed" to Spec(R.string.logmsg_url_open_failed),
        "write_settings" to Spec(R.string.logmsg_write_settings),
        "brightness_no_level" to Spec(R.string.logmsg_brightness_no_level),
        "tap_coords" to Spec(R.string.logmsg_tap_coords),
        "tap_not_dispatched" to Spec(R.string.logmsg_tap_not_dispatched),
        "swipe_coords" to Spec(R.string.logmsg_swipe_coords),
        "swipe_same_point" to Spec(R.string.logmsg_swipe_same_point),
        "swipe_not_dispatched" to Spec(R.string.logmsg_swipe_not_dispatched),
        "airplane_adb" to Spec(R.string.logmsg_airplane_adb),
        "airplane_failed" to Spec(R.string.logmsg_airplane_failed),
        "app_no_package" to Spec(R.string.logmsg_app_no_package),
        "app_not_found" to Spec(R.string.logmsg_app_not_found),
        "app_launch_failed" to Spec(R.string.logmsg_app_launch_failed),
        "http_bad_method" to Spec(R.string.logmsg_http_bad_method),
        "http_global_response" to Spec(R.string.logmsg_http_global_response),
        "http_failed" to Spec(R.string.logmsg_http_failed),
        "http_too_large" to Spec(R.string.logmsg_http_too_large),
        "http_bad_header" to Spec(R.string.logmsg_http_bad_header),
        "json_invalid" to Spec(R.string.logmsg_json_invalid),
        "json_empty_segment" to Spec(R.string.logmsg_json_empty_segment),
        "json_missing_key" to Spec(R.string.logmsg_json_missing_key, mapOf(1 to Arg.WHERE)),
        "json_not_number" to Spec(R.string.logmsg_json_not_number, mapOf(1 to Arg.WHERE)),
        "json_out_of_range" to Spec(R.string.logmsg_json_out_of_range, mapOf(1 to Arg.WHERE)),
        "json_scalar" to Spec(R.string.logmsg_json_scalar, mapOf(1 to Arg.WHERE)),
        "call_no_number" to Spec(R.string.logmsg_call_no_number),
        "call_failed" to Spec(R.string.logmsg_call_failed),
        "bt_no_adapter" to Spec(R.string.logmsg_bt_no_adapter),
        "bt_permission" to Spec(R.string.logmsg_bt_permission),
        "bt_failed" to Spec(R.string.logmsg_bt_failed),
        "shortcut_none" to Spec(R.string.logmsg_shortcut_none),
        "shortcut_invalid" to Spec(R.string.logmsg_shortcut_invalid),
        "shortcut_blocked" to Spec(R.string.logmsg_shortcut_blocked),
        "shortcut_missing" to Spec(R.string.logmsg_shortcut_missing),
        "shortcut_failed" to Spec(R.string.logmsg_shortcut_failed),
        "sms_no_number" to Spec(R.string.logmsg_sms_no_number),
        "sms_no_message" to Spec(R.string.logmsg_sms_no_message),
        "sms_failed" to Spec(R.string.logmsg_sms_failed),
    )

    fun render(context: Context, stored: String?): String? {
        stored ?: return null
        val parsed = LogText.parse(stored) ?: return stored
        val spec = SPECS[parsed.code] ?: return LogText.debug(stored)
        val args = parsed.args.mapIndexed { index, value -> describe(context, spec.args[index], value) }
        // A message with fewer arguments than its wording expects (written by another version)
        // would throw while formatting; its raw form still says what happened.
        return runCatching { context.getString(spec.text, *args.toTypedArray()) }
            .getOrElse { LogText.debug(stored) }
    }

    private fun describe(context: Context, kind: Arg?, value: String): String = when (kind) {
        null -> value
        Arg.ACTION -> runCatching { ActionType.valueOf(value).info(context).label }.getOrDefault(value)
        Arg.COND -> ConditionType.fromId(value)?.info(context)?.label ?: value
        Arg.WHERE -> if (value.isEmpty()) {
            context.getString(R.string.logmsg_json_root)
        } else {
            context.getString(R.string.logmsg_json_where, value)
        }
    }
}
