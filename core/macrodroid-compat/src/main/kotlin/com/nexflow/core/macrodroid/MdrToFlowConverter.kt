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
package com.nexflow.core.macrodroid

import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.ImportItemKind
import com.nexflow.core.flowschema.ImportWarning
import com.nexflow.core.flowschema.ImportWarnings
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.macrodroid.model.MdrItem
import com.nexflow.core.macrodroid.model.MdrMacro
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts MacroDroid macros into NexFlow .flow format.
 *
 * Conversion is best-effort in two independent steps, and both report what they could not do:
 *
 * 1. the class type is looked up in the tables below — no entry means an `UNSUPPORTED` item;
 * 2. the settings are translated by [MdrOptionMappers] — no mapper means the item imports with
 *    its settings blank, which the caller must surface, because an item that looks fine but does
 *    nothing is worse than one that is visibly missing.
 *
 * Warnings carry the id of the item they are about, so the UI can walk the user to it.
 *
 * Every class name and option below was read from MacroDroid's own classes and from real export
 * files; docs/MACRODROID_IMPORT.md lists the sources. Do not add entries from memory — MacroDroid
 * names them inconsistently (`MakeCallAction` but `SetPriorityMode`, `WifiConnectionTrigger` but
 * `WifiConstraint`), so a plausible-looking guess simply never matches.
 */
object MdrToFlowConverter {

    fun convert(macro: MdrMacro): ConversionResult {
        val now = Instant.now().toString()
        val flowId = macro.guid.takeIf { it != 0L }?.toString() ?: UUID.randomUUID().toString()
        val flowName = macro.name.ifBlank { "Imported Macro" }
        val warnings = WarningSink(flowId, flowName)

        val triggers = macro.triggerList.map { convertTrigger(it, warnings) }
        val conditions = macro.constraintList.map { convertCondition(it, warnings) }

        // Actions can expand (one MacroDroid menu becomes a whole NexFlow menu block), so the
        // order is a running count rather than the index in the source list.
        val actions = mutableListOf<ActionJson>()
        macro.actionList.forEach { actions += convertAction(it, actions.size, warnings) }

        val flowJson = FlowJson(
            schemaVersion = 1,
            id = flowId,
            name = flowName,
            description = macro.description,
            author = null,
            tags = listOf("macrodroid-import"),
            enabled = macro.enabled,
            createdAt = now,
            updatedAt = now,
            triggers = triggers,
            triggerLogic = "ANY",
            conditions = conditions,
            actions = actions,
            variables = emptyList(),
        )
        return ConversionResult(flowJson, warnings.all)
    }

    /** Collects warnings already tagged with the flow and item they belong to. */
    private class WarningSink(private val flowId: String, private val flowName: String) {
        val all = mutableListOf<ImportWarning>()

        fun at(kind: ImportItemKind, itemId: String) = Warn { code, args ->
            all += ImportWarning(
                code = code,
                args = args.toList(),
                flowId = flowId,
                flowName = flowName,
                itemKind = kind,
                itemId = itemId,
            )
        }
    }

    private fun convertTrigger(trigger: MdrItem, warnings: WarningSink): TriggerJson {
        val id = trigger.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val warn = warnings.at(ImportItemKind.TRIGGER, id)
        val mappedType = TRIGGER_TYPE_MAP[trigger.classType]
        if (mappedType == null) warn(ImportWarnings.UNSUPPORTED_TRIGGER, trigger.classType)
        return TriggerJson(
            id = id,
            type = mappedType ?: "MANUAL",
            config = mapOptions(trigger, mappedType, warn),
        )
    }

    private fun convertCondition(constraint: MdrItem, warnings: WarningSink): ConditionJson {
        val id = constraint.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val warn = warnings.at(ImportItemKind.CONDITION, id)
        val mappedType = CONDITION_TYPE_MAP[constraint.classType]
        // NexFlow enforces conditions before a run, so an unrecognised one holds the whole flow
        // back — never let this one pass unmentioned.
        if (mappedType == null) warn(ImportWarnings.UNSUPPORTED_CONDITION, constraint.classType)
        return ConditionJson(
            id = id,
            type = mappedType ?: "UNKNOWN",
            config = mapOptions(constraint, mappedType, warn),
            negate = false,
        )
    }

    /** One MacroDroid action, as one or more NexFlow actions starting at [order]. */
    private fun convertAction(action: MdrItem, order: Int, warnings: WarningSink): List<ActionJson> {
        val id = action.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val warn = warnings.at(ImportItemKind.ACTION, id)
        if (action.classType == OPTION_DIALOG) return expandOptionDialog(action, id, order, warn)

        val mappedType = actionTypeFor(action)
        if (mappedType == null) warn(ImportWarnings.UNSUPPORTED_ACTION, action.classType)
        if (mappedType == "SIMULATE_TAP" || mappedType == "SIMULATE_SWIPE") {
            // Kept as a real action rather than dropped: the coordinates are worth carrying over,
            // and the run fails loudly with this reason if the build or the service can't do it.
            warn(ImportWarnings.TAP_SWIPE_GITHUB_ONLY)
        }

        val config = if (mappedType == null) {
            // The type falls back to a toast on import, so give that toast something to say —
            // an empty one would leave the user guessing what used to be here.
            JsonObject(
                mapOptions(action, null, warn) + mapOf(
                    "_mdr_class" to JsonPrimitive(action.classType),
                    "message" to JsonPrimitive("Unsupported MacroDroid action: ${action.classType}"),
                ),
            )
        } else {
            mapOptions(action, mappedType, warn)
        }

        return listOf(
            ActionJson(
                id = id,
                type = mappedType ?: "UNSUPPORTED",
                config = config,
                order = order,
                enabled = !action.disabled,
            ),
        )
    }

    /**
     * MacroDroid's option dialog is a single action holding the button names, whereas NexFlow
     * spells a menu out as SHOW_MENU + one MENU_CASE per option + END_MENU. So this is the one
     * conversion that produces more actions than it consumes.
     *
     * What each option *does* cannot come across: MacroDroid points every button at another macro
     * or action block (`m_actionMacroGuids`), while a NexFlow case holds the actions inline. The
     * cases therefore arrive empty, and the user is told so.
     */
    private fun expandOptionDialog(
        action: MdrItem,
        menuId: String,
        order: Int,
        warn: Warn,
    ): List<ActionJson> {
        val options = MdrOptions(action.options)
        val buttons = options.strings("m_buttonNames").orEmpty().filter { it.isNotBlank() }
        val title = options.string("m_title") ?: options.string("m_message") ?: ""
        options.ignore(
            "m_message", "m_actionMacroGuids", "actionBlockData", "m_defaultButton",
            "m_defaultTimeOutSecs", "blockNextAction", "preventBackButtonClosing",
        )

        if (buttons.isEmpty()) {
            warn(ImportWarnings.MENU_NO_BUTTONS)
        } else {
            warn(ImportWarnings.MENU_CASES_EMPTY, buttons.size.toString())
        }
        reportLeftovers(options, warn)

        val enabled = !action.disabled
        val showMenu = ActionJson(
            id = menuId,
            type = "SHOW_MENU",
            config = JsonObject(
                mapOf(
                    "title" to JsonPrimitive(title),
                    // The editor stores the option list as JSON text inside the config value.
                    "options" to JsonPrimitive(JsonArray(buttons.map(::JsonPrimitive)).toString()),
                ),
            ),
            order = order,
            enabled = enabled,
        )
        val cases = buttons.mapIndexed { i, option ->
            ActionJson(
                id = UUID.randomUUID().toString(),
                type = "MENU_CASE",
                config = JsonObject(mapOf("option" to JsonPrimitive(option))),
                order = order + 1 + i,
                enabled = enabled,
            )
        }
        val endMenu = ActionJson(
            id = UUID.randomUUID().toString(),
            type = "END_MENU",
            config = JsonObject(emptyMap()),
            order = order + 1 + buttons.size,
            enabled = enabled,
        )
        return listOf(showMenu) + cases + endMenu
    }

    /**
     * The NexFlow type for an action, where the class type alone is not always enough: MacroDroid
     * files a tap and a swipe under one class and tells them apart by the configuration inside.
     */
    private fun actionTypeFor(action: MdrItem): String? = when (action.classType) {
        "UIInteractionAction" -> when (uiInteractionType(action.options)) {
            "Click" -> "SIMULATE_TAP"
            "Gesture" -> "SIMULATE_SWIPE"
            else -> null
        }
        else -> ACTION_TYPE_MAP[action.classType]
    }

    private fun uiInteractionType(options: JsonObject): String? =
        ((options["uiInteractionConfiguration"] as? JsonObject)?.get("type") as? JsonPrimitive)?.content

    /**
     * Runs the item's option mapper and reports what it could not carry over. Without a mapper the
     * item is imported blank — said out loud, because the alternative is a flow that looks
     * complete and silently does nothing.
     */
    private fun mapOptions(item: MdrItem, mappedType: String?, warn: Warn): JsonObject {
        val mapper = MdrOptionMappers.forClass(item.classType)
        if (mapper == null) {
            if (mappedType != null && item.options.isNotEmpty()) warn(ImportWarnings.SETTINGS_NOT_MAPPED)
            return JsonObject(emptyMap())
        }
        val options = MdrOptions(item.options)
        val config = mapper.map(options, warn)
        reportLeftovers(options, warn)
        return JsonObject(config.mapValues { (_, v) -> JsonPrimitive(v) })
    }

    private fun reportLeftovers(options: MdrOptions, warn: Warn) {
        val leftovers = options.leftovers
        if (leftovers.isNotEmpty()) warn(ImportWarnings.SETTINGS_DROPPED, leftovers.joinToString())
    }

    private const val OPTION_DIALOG = "OptionDialogAction"

    /**
     * MacroDroid `m_classType` → NexFlow [com.nexflow.core.automation.model.TriggerType].
     *
     * MacroDroid has ~100 triggers to NexFlow's 16, so most of them have no entry by design.
     */
    private val TRIGGER_TYPE_MAP = mapOf(
        "TimerTrigger" to "TIME",
        "BatteryLevelTrigger" to "BATTERY",
        "BluetoothTrigger" to "BLUETOOTH",
        "WifiConnectionTrigger" to "WIFI",
        "WifiSSIDTrigger" to "WIFI",
        "ScreenOnOffTrigger" to "SCREEN",
        "DeviceUnlockedTrigger" to "SCREEN",
        "ApplicationLaunchedTrigger" to "APP_LAUNCH",
        "IncomingCallTrigger" to "INCOMING_CALL",
        "IncomingSMSTrigger" to "SMS_RECEIVED",
        "NotificationTrigger" to "NOTIFICATION_RECEIVED",
        "BootTrigger" to "DEVICE_BOOT",
        "HeadphonesTrigger" to "HEADSET_PLUG",
        "NFCTrigger" to "NFC_TAG",
        "GeofenceTrigger" to "GEOFENCE",
        "ShakeDeviceTrigger" to "SHAKE",
        "LightSensorTrigger" to "AMBIENT_LIGHT",
        // Everything the user starts by hand arrives as NexFlow's manual trigger.
        "FloatingButtonTrigger" to "MANUAL",
        "EmptyTrigger" to "MANUAL",
        "ShortcutTrigger" to "MANUAL",
        "WidgetPressedTrigger" to "MANUAL",
        "QuickSettingsTileTrigger" to "MANUAL",
    )

    /** MacroDroid `m_classType` → NexFlow [com.nexflow.core.automation.model.ActionType]. */
    private val ACTION_TYPE_MAP = mapOf(
        "LaunchActivityAction" to "OPEN_APP",
        "LaunchShortcutAction" to "LAUNCH_SHORTCUT",
        "SendSMSAction" to "SEND_SMS",
        "MakeCallAction" to "CALL_PHONE",
        "SetWifiAction" to "WIFI_TOGGLE",
        "SetBluetoothAction" to "BLUETOOTH_TOGGLE",
        "SetAirplaneModeAction" to "AIRPLANE_TOGGLE",
        "SetPriorityMode" to "DND_TOGGLE",
        "SetVolumeAction" to "VOLUME_ADJUST",
        "SetBrightnessAction" to "BRIGHTNESS_ADJUST",
        "SetWallpaperAction" to "SET_WALLPAPER",
        "SpeakerPhoneAction" to "SPEAKERPHONE",
        "NotificationAction" to "NOTIFICATION",
        "ToastAction" to "TOAST",
        "HttpRequestAction" to "HTTP_REQUEST",
        "ClipboardAction" to "CLIPBOARD_COPY",
        "UpdateClipboardAction" to "CLIPBOARD_COPY",
        "OpenWebPageAction" to "OPEN_URL",
        "ControlMediaAction" to "MEDIA_PLAY_PAUSE",
        "SpeakTextAction" to "TTS",
        "PauseAction" to "DELAY",
        "IfConditionAction" to "IF_BLOCK",
        "ElseAction" to "ELSE_BLOCK",
        "EndIfAction" to "END_IF",
        "LoopAction" to "REPEAT_BLOCK",
        "EndLoopAction" to "END_REPEAT",
        "SetVariableAction" to "SET_VARIABLE",
        "WriteToFileAction" to "WRITE_FILE",
        "TakeScreenshotAction" to "SCREENSHOT",
        // The old dedicated tap action; newer files use UIInteractionAction (see actionTypeFor).
        "TouchScreenAction" to "SIMULATE_TAP",
    )

    /** MacroDroid `m_classType` → NexFlow [com.nexflow.core.automation.model.ConditionType]. */
    private val CONDITION_TYPE_MAP = mapOf(
        "BatteryLevelConstraint" to "BATTERY_LEVEL",
        "ExternalPowerConstraint" to "CHARGING",
        "WifiConstraint" to "WIFI_CONNECTED",
        "TimeOfDayConstraint" to "TIME_RANGE",
        "DayOfWeekConstraint" to "DAY_OF_WEEK",
        "BluetoothConstraint" to "BLUETOOTH_CONNECTED",
        "ScreenOnOffConstraint" to "SCREEN_STATE",
    )
}

data class ConversionResult(
    val flow: FlowJson,
    val warnings: List<ImportWarning>,
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}
