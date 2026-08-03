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
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import com.nexflow.core.macrodroid.model.MdrAction
import com.nexflow.core.macrodroid.model.MdrConstraint
import com.nexflow.core.macrodroid.model.MdrMacro
import com.nexflow.core.macrodroid.model.MdrTrigger
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts MacroDroid .mdr macros into NexFlow .flow format.
 *
 * Conversion is best-effort: unsupported MacroDroid class types produce an action/trigger
 * with type "UNSUPPORTED" and a note in the config. The caller should surface these to the user.
 */
object MdrToFlowConverter {

    fun convert(macro: MdrMacro): ConversionResult {
        val now = Instant.now().toString()
        val warnings = mutableListOf<String>()

        val triggers = macro.triggerList.mapIndexed { i, t -> convertTrigger(t, i, warnings) }
        val conditions = macro.constraintList.mapIndexed { i, c -> convertCondition(c, i, warnings) }
        if (conditions.isNotEmpty()) {
            // Only the constraint *type* is translated — MacroDroid names its settings differently,
            // so the imported condition arrives without them. NexFlow enforces conditions at run
            // time, which makes an unreviewed constraint able to hold a flow back; say so plainly
            // rather than let the user discover it as a flow that never fires.
            warnings += "Conditions: ${conditions.size} constraint(s) imported — their settings do " +
                "NOT carry over from MacroDroid. Open each one in the editor and set it up, or " +
                "remove it; conditions are enforced before a flow runs"
        }
        val actions = macro.actionList.mapIndexed { i, a -> convertAction(a, i, warnings) }

        val flowJson = FlowJson(
            schemaVersion = 1,
            id = macro.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = macro.name.ifBlank { "Imported Macro" },
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
        return ConversionResult(flowJson, warnings)
    }

    private fun convertTrigger(trigger: MdrTrigger, index: Int, warnings: MutableList<String>): TriggerJson {
        val mappedType = TRIGGER_TYPE_MAP[trigger.classType]
        if (mappedType == null) {
            warnings += "Trigger[$index]: unsupported class '${trigger.classType}'"
        }
        return TriggerJson(
            id = trigger.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            type = mappedType ?: "MANUAL",
            config = trigger.options,
        )
    }

    private fun convertCondition(constraint: MdrConstraint, index: Int, warnings: MutableList<String>): ConditionJson {
        val mappedType = CONDITION_TYPE_MAP[constraint.classType]
        if (mappedType == null) {
            warnings += "Condition[$index]: unsupported class '${constraint.classType}'"
        }
        return ConditionJson(
            id = constraint.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            type = mappedType ?: "UNKNOWN",
            config = constraint.options,
            negate = constraint.negate,
        )
    }

    private fun convertAction(action: MdrAction, index: Int, warnings: MutableList<String>): ActionJson {
        val mappedType = ACTION_TYPE_MAP[action.classType]
        if (mappedType == null) {
            warnings += "Action[$index]: unsupported class '${action.classType}'"
        }
        val config = if (mappedType == null) {
            JsonObject(
                action.options + mapOf(
                    "_mdr_class" to JsonPrimitive(action.classType),
                ),
            )
        } else {
            action.options
        }
        return ActionJson(
            id = action.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            type = mappedType ?: "UNSUPPORTED",
            config = config,
            order = index,
            enabled = true,
        )
    }

    // Community-maintained mapping of MacroDroid class_type strings to NexFlow ActionType names.
    private val TRIGGER_TYPE_MAP = mapOf(
        "TimerTrigger" to "TIME",
        "BatteryLevelTrigger" to "BATTERY",
        "BluetoothConnectedTrigger" to "BLUETOOTH",
        "WifiConnectedTrigger" to "WIFI",
        "ScreenOnOffTrigger" to "SCREEN",
        "ApplicationLaunchedTrigger" to "APP_LAUNCH",
        "IncomingCallTrigger" to "INCOMING_CALL",
        "SMSReceivedTrigger" to "SMS_RECEIVED",
        "NotificationTrigger" to "NOTIFICATION_RECEIVED",
        "BootTrigger" to "DEVICE_BOOT",
        "HeadphonesTrigger" to "HEADSET_PLUG",
        "NFCTrigger" to "NFC_TAG",
        "GeoFenceTrigger" to "GEOFENCE",
        "FloatingButtonTrigger" to "MANUAL",
    )

    private val ACTION_TYPE_MAP = mapOf(
        "LaunchApplication" to "OPEN_APP",
        "SendSMS" to "SEND_SMS",
        "PhoneCall" to "CALL_PHONE",
        "WiFiAction" to "WIFI_TOGGLE",
        "BluetoothAction" to "BLUETOOTH_TOGGLE",
        "DoNotDisturbAction" to "DND_TOGGLE",
        "VolumeAction" to "VOLUME_ADJUST",
        "NotificationAction" to "NOTIFICATION",
        "ToastAction" to "TOAST",
        "HttpRequestAction" to "HTTP_REQUEST",
        "ClipboardAction" to "CLIPBOARD_COPY",
        "OpenBrowser" to "OPEN_URL",
        "MediaAction" to "MEDIA_PLAY_PAUSE",
        "TextToSpeechAction" to "TTS",
        "BrightnessAction" to "BRIGHTNESS_ADJUST",
        "WaitAction" to "DELAY",
        "IfAction" to "IF_BLOCK",
        "ElseAction" to "ELSE_BLOCK",
        "EndIfAction" to "END_IF",
        "LoopAction" to "REPEAT_BLOCK",
        "EndLoopAction" to "END_REPEAT",
        "SetVariableAction" to "SET_VARIABLE",
        "WriteToFile" to "WRITE_FILE",
        "ShareAction" to "SHARE",
        "ScreenshotAction" to "SCREENSHOT",
    )

    private val CONDITION_TYPE_MAP = mapOf(
        "BatteryConstraint" to "BATTERY_LEVEL",
        "WiFiConstraint" to "WIFI_CONNECTED",
        "TimeConstraint" to "TIME_RANGE",
        "BluetoothConstraint" to "BLUETOOTH_CONNECTED",
        "ScreenConstraint" to "SCREEN_STATE",
    )
}

data class ConversionResult(
    val flow: FlowJson,
    val warnings: List<String>,
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}
