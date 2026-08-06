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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Translates one MacroDroid item's settings into the NexFlow config keys for its type.
 *
 * Mapping the class type alone is not enough to make an imported flow usable: the two apps name
 * their settings differently (`m_hour`/`m_minute` vs a single `"HH:mm"` string), so without this
 * step every imported item arrives blank. Anything that cannot be carried over is passed to
 * [warn] rather than dropped in silence.
 */
internal fun interface MdrOptionMapper {
    fun map(options: MdrOptions, warn: (String) -> Unit): Map<String, String>
}

/**
 * The per-class mapping table, keyed by MacroDroid `m_classType`.
 *
 * Keyed by the *MacroDroid* class rather than the NexFlow type because several MacroDroid classes
 * map onto one NexFlow type with completely different fields (a tap is `TouchScreenAction` in old
 * files and `UIInteractionAction` in new ones). Adding support for another class is one entry
 * here plus one in [MdrToFlowConverter]'s type table — no change to the conversion itself.
 *
 * Field names and the meaning of every numeric option were read out of MacroDroid's own classes;
 * see docs/MACRODROID_IMPORT.md for the sources.
 */
internal object MdrOptionMappers {

    fun forClass(classType: String): MdrOptionMapper? = BY_CLASS[classType]

    /** Nested MacroDroid objects (a contact, a variable) are read one field at a time. */
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** MacroDroid stores days as a 7-slot array starting on Monday. */
    private val DAY_IDS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val WEEKDAYS = DAY_IDS.take(5)
    private val WEEKENDS = DAY_IDS.drop(5)

    /** `requestType` on an HTTP request, in MacroDroid's order. NexFlow stops at PATCH. */
    private val HTTP_METHODS = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

    /** `m_state` on the toggle actions: 0 = on, 1 = off, 2 = toggle. */
    private fun toggleState(value: Int?): String? = when (value) {
        0 -> "ON"
        1 -> "OFF"
        2 -> "TOGGLE"
        else -> null
    }

    /**
     * The connection option shared by the Wi-Fi/Bluetooth triggers and constraints:
     * 0 = adapter enabled, 1 = adapter disabled, 2 = connected, 3 = disconnected. NexFlow's
     * trigger only covers the connection half, so 0/1 are reported instead of guessed at.
     */
    private fun connectionEvent(value: Int?, warn: (String) -> Unit, what: String): String? = when (value) {
        2 -> "CONNECTED"
        3 -> "DISCONNECTED"
        0, 1 -> {
            warn("$what fires on the adapter being turned on/off, which NexFlow has no equivalent for — set it up as a connect/disconnect, or remove it")
            null
        }
        else -> null
    }

    /** How MacroDroid's exact-match/regex pair maps onto NexFlow's single match mode. */
    private fun matchMode(exact: Boolean?, regex: Boolean?): String = when {
        regex == true -> "REGEX"
        exact == true -> "EXACT"
        else -> "CONTAINS"
    }

    private val BY_CLASS: Map<String, MdrOptionMapper> = mapOf(

        // ---------------------------------------------------------------- triggers

        "TimerTrigger" to MdrOptionMapper { o, warn ->
            val hour = o.int("m_hour") ?: 0
            val minute = o.int("m_minute") ?: 0
            val selected = o.booleans("m_daysOfWeek").orEmpty()
                .let { days -> DAY_IDS.filterIndexed { i, _ -> days.getOrElse(i) { false } } }
            if ((o.int("m_second") ?: 0) > 0) {
                warn("Time trigger: the seconds part was dropped — NexFlow schedules to the minute")
            }
            // MacroDroid's own alarm bookkeeping, meaningless on this side.
            o.ignore("m_alarmId", "m_useAlarm")
            buildMap {
                put("time", "%02d:%02d".format(hour, minute))
                when {
                    selected.isEmpty() || selected.size == 7 -> put("repeat", "DAILY")
                    selected == WEEKDAYS -> put("repeat", "WEEKDAYS")
                    selected == WEEKENDS -> put("repeat", "WEEKENDS")
                    else -> {
                        put("repeat", "CUSTOM")
                        put("days", selected.joinToString(","))
                    }
                }
            }
        },

        "BatteryLevelTrigger" to MdrOptionMapper { o, warn ->
            // m_option: 0 = crosses the level, 1 = any change. NexFlow always compares to a level.
            if (o.int("m_option") == 1) {
                warn("Battery trigger: MacroDroid fired on any level change; NexFlow needs a level to cross — check the level and direction")
            }
            mapOf(
                "level" to (o.int("m_batteryLevel") ?: 0).toString(),
                "direction" to if (o.bool("m_decreasesTo") != false) "BELOW" else "ABOVE",
                "charging" to "ANY",
            )
        },

        "ScreenOnOffTrigger" to MdrOptionMapper { o, _ ->
            mapOf("event" to if (o.bool("m_screenOn") != false) "ON" else "OFF")
        },

        "DeviceUnlockedTrigger" to MdrOptionMapper { _, _ -> mapOf("event" to "UNLOCKED") },

        "WifiConnectionTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_SSIDList")
            buildMap {
                o.string("m_SSID")?.let { put("ssid", it) }
                connectionEvent(o.int("m_wifiState"), warn, "Wi-Fi trigger")?.let { put("event", it) }
            }
        },

        "BluetoothTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_deviceAddress", "m_anyDevice")
            buildMap {
                if (o.bool("m_anyDevice") != true) o.string("m_deviceName")?.let { put("device_name", it) }
                connectionEvent(o.int("m_btState"), warn, "Bluetooth trigger")?.let { put("event", it) }
            }
        },

        "ApplicationLaunchedTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_applicationNameList", "usePackageNameOption", "isAllApps")
            val packages = o.strings("m_packageNameList").orEmpty()
            if (packages.size > 1) {
                warn("App trigger: MacroDroid watched ${packages.size} apps; NexFlow watches one — kept '${packages.first()}'")
            }
            if (o.bool("m_launched") == false) {
                warn("App trigger: MacroDroid fired when the app was *closed*, which NexFlow has no trigger for")
            }
            packages.firstOrNull()?.let { mapOf("package_name" to it) } ?: emptyMap()
        },

        "IncomingCallTrigger" to MdrOptionMapper { o, _ ->
            o.ignore("m_incomingCallFromList", "m_groupIdList", "m_groupNameList", "m_option", "isExclude", "m_phoneNumberExclude")
            buildMap {
                (o.string("m_phoneNumber") ?: o.obj("m_incomingCallFrom")?.text("m_name"))
                    ?.let { put("contact", it) }
            }
        },

        "IncomingSMSTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_smsFrom", "m_smsFromList", "m_groupIdList", "m_groupNameList", "subscriptionId", "ignoreCase")
            if (o.bool("m_excludes") == true || o.bool("m_smsNumberExclude") == true) {
                warn("SMS trigger: MacroDroid's 'exclude' option is not carried over — NexFlow matches, it cannot exclude")
            }
            buildMap {
                o.string("m_smsNumber")?.let { put("sender", it) }
                o.string("m_smsContent")?.let { put("body_keyword", it) }
                put("match_mode", matchMode(o.bool("m_exactMatch"), o.bool("enableRegex")))
            }
        },

        "NotificationTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_applicationName", "m_applicationNameList", "m_soundOption", "ignoreCase", "m_supressMultiples", "m_ignoreOngoing")
            if (o.int("m_option") == 1) {
                warn("Notification trigger: MacroDroid fired when a notification was *cleared*, which NexFlow has no trigger for")
            }
            if (o.bool("m_excludeApps") == true || o.bool("m_excludes") == true) {
                warn("Notification trigger: MacroDroid's 'exclude' option is not carried over — NexFlow matches, it cannot exclude")
            }
            buildMap {
                (o.string("m_packageName") ?: o.strings("m_packageNameList")?.firstOrNull())
                    ?.let { put("package_name", it) }
                o.string("m_textContent")?.let { put("keyword", it) }
                put("match_field", "ANY")
                put("match_mode", matchMode(o.bool("m_exactMatch"), o.bool("enableRegex")))
            }
        },

        "HeadphonesTrigger" to MdrOptionMapper { o, _ ->
            o.ignore("m_micOption")
            mapOf("event" to if (o.bool("m_headphonesConnected") != false) "CONNECTED" else "DISCONNECTED")
        },

        "NFCTrigger" to MdrOptionMapper { o, _ ->
            buildMap { o.string("m_tagName")?.let { put("tag_id", it) } }
        },

        "GeofenceTrigger" to MdrOptionMapper { o, warn ->
            o.ignore("m_geofenceId", "m_geofenceUpdateRateMinutes", "m_updateRateText", "m_triggerFromUnknown")
            // The macro only references a zone by id; the coordinates live in the file's separate
            // geofenceData block, which is not part of the macro NexFlow imports.
            warn("Geofence trigger: MacroDroid keeps the coordinates outside the macro, so the area did not come across — set the location and radius in the editor")
            mapOf("event" to if (o.bool("m_enterArea") != false) "ENTER" else "EXIT")
        },

        "LightSensorTrigger" to MdrOptionMapper { o, _ ->
            // m_option: 0 = decreases to the level, 1 = increases to it.
            val lux = o.double("m_lightLevelFloat")?.takeIf { it > 0.0 }
                ?: o.int("m_lightLevel")?.toDouble()
                ?: 0.0
            mapOf(
                "mode" to if (o.int("m_option") == 1) "ABOVE" else "BELOW",
                "threshold_lux" to (if (lux % 1.0 == 0.0) lux.toInt().toString() else lux.toString()),
            )
        },

        // Shake sensitivity is a MacroDroid-wide setting, not part of the trigger, so there is
        // nothing in the file to carry over — NexFlow's own default applies.
        "ShakeDeviceTrigger" to MdrOptionMapper { _, _ -> mapOf("sensitivity" to "MEDIUM") },

        // ---------------------------------------------------------------- actions

        "ToastAction" to MdrOptionMapper { o, _ ->
            o.ignore(
                "m_backgroundColor", "m_textColor", "m_position", "m_horizontalPosition", "m_duration",
                "m_displayIcon", "m_imageName", "m_imagePackageName", "m_imageResourceName", "m_imageUri",
                "m_tintIcon", "cancelPrevious", "maintainSpaces", "useTextOnly",
            )
            buildMap { o.string("m_messageText")?.let { put("message", it) } }
        },

        "NotificationAction" to MdrOptionMapper { o, _ ->
            o.ignore(
                "m_imageResourceId", "m_imageResourceName", "m_iconBgColor", "iconText", "iconType",
                "m_notificationChannelType", "notificationChannelName", "m_priority", "m_ringtoneIndex",
                "m_ringtoneName", "m_overwriteExisting", "m_runMacroWhenPressed", "m_macroGUIDToRun",
                "notificationActionButtons", "actionBlockData", "blockNextAction", "disableHtml",
                "maintainSpaces", "preventBackButtonClosing",
            )
            buildMap {
                o.string("m_notificationSubject")?.let { put("title", it) }
                o.string("m_notificationText")?.let { put("message", it) }
                put("tap_action", "NONE")
            }
        },

        "PauseAction" to MdrOptionMapper { o, _ ->
            o.ignore("m_useAlarm", "m_variable", "unitForVariables", "varDictionaryKeys")
            val seconds = o.int("m_delayInSeconds") ?: 0
            val millis = o.int("m_delayInMilliSeconds") ?: 0
            // NexFlow's delay is one value plus a unit, so a sub-second wait stays in milliseconds.
            if (seconds > 0) {
                mapOf("duration_value" to (seconds + millis / 1000).toString(), "duration_unit" to "SEC")
            } else {
                mapOf("duration_value" to millis.toString(), "duration_unit" to "MS")
            }
        },

        "SpeakTextAction" to MdrOptionMapper { o, _ ->
            o.ignore(
                "m_locale", "m_pitch", "m_speed", "m_queue", "m_waitToFinish", "m_audioStream",
                "m_specifyAudioStream", "m_readNumbersIndividually",
            )
            buildMap { o.string("m_textToSay")?.let { put("text", it) } }
        },

        "OpenWebPageAction" to MdrOptionMapper { o, _ ->
            o.ignore(
                "m_httpGet", "m_disableUrlEncode", "allowAnyCertificate", "blockNextAction",
                "m_variableToSaveResponse", "m_variableSuccessResponse",
            )
            buildMap { o.string("m_urlToOpen")?.let { put("url", it) } }
        },

        "SendSMSAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_addToMessageLog", "m_simId", "m_contact")
            if (o.bool("m_prePopulate") == true) {
                warn("Send SMS: MacroDroid only pre-filled the message for you to send; NexFlow sends it directly")
            }
            buildMap {
                (o.string("m_number") ?: o.obj("m_contact")?.text("m_number"))?.let { put("number", it) }
                o.string("m_messageContent")?.let { put("message", it) }
            }
        },

        "MakeCallAction" to MdrOptionMapper { o, _ ->
            o.ignore("m_contact", "slotId")
            buildMap { o.string("m_number")?.let { put("number", it) } }
        },

        "LaunchActivityAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_activityName", "m_activityToLaunch", "m_applicationName", "m_startNew", "m_excludeFromRecents", "option", "launchByPackageName")
            val pkg = o.string("m_packageToLaunch")
            if (pkg == null) warn("Open app: no package name in the file — pick the app in the editor")
            buildMap { pkg?.let { put("package_name", it) } }
        },

        "LaunchShortcutAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_intent", "m_intentEncoded", "m_serializedExtras", "m_appName")
            warn("Launch shortcut: the shortcut itself is a device-specific intent and cannot be imported — pick the shortcut again in the editor")
            buildMap { o.string("m_name")?.let { put("label", it) } }
        },

        "SetWifiAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_SSID", "m_networkId")
            // 0/1/2 line up with NexFlow; 3 = connect to a network, 4 = forget one, neither exists here.
            val state = toggleState(o.int("m_state"))
            if (state == null) warn("Wi-Fi action: 'connect to'/'forget network' has no NexFlow equivalent — the action was left unset")
            buildMap { state?.let { put("state", it) } }
        },

        "SetBluetoothAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_deviceName", "m_deviceAddress")
            val state = toggleState(o.int("m_state"))
            if (state == null) warn("Bluetooth action: connecting to a specific device has no NexFlow equivalent — the action was left unset")
            buildMap { state?.let { put("state", it) } }
        },

        "SetAirplaneModeAction" to MdrOptionMapper { o, _ ->
            o.ignore("m_keepWifiOn", "m_keepBluetoothOn", "mechanismOption", "configComplete")
            buildMap { toggleState(o.int("m_state"))?.let { put("state", it) } }
        },

        "SpeakerPhoneAction" to MdrOptionMapper { o, warn ->
            when (val state = toggleState(o.int("m_state"))) {
                "TOGGLE" -> {
                    warn("Speakerphone: MacroDroid's toggle has no NexFlow equivalent — set on or off in the editor")
                    emptyMap()
                }
                null -> emptyMap()
                else -> mapOf("state" to state)
            }
        },

        // m_option: 0 = allow all (DND off), 1 = priority only, 2 = total silence.
        "SetPriorityMode" to MdrOptionMapper { o, _ ->
            mapOf("state" to if (o.int("m_option") == 0) "OFF" else "ON")
        },

        "SetBrightnessAction" to MdrOptionMapper { o, _ ->
            o.ignore("m_brightness", "m_forcePieMode", "m_variable", "varDictionaryKeys", "forceValue", "forceValueEnabled")
            mapOf(
                "level" to (o.int("m_brightnessPercent") ?: 50).toString(),
                "brightness_unit" to "PCT",
            )
        },

        "ClipboardAction" to MdrOptionMapper { o, _ ->
            buildMap { o.string("m_clipboardText")?.let { put("text", it) } }
        },

        "WriteToFileAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_pathUri", "m_pathName", "m_temporaryPathName", "overwrite")
            // NexFlow's write action always replaces the file; MacroDroid could add to it.
            if (o.bool("m_append") == true || o.bool("m_prepend") == true) {
                warn("Write to file: MacroDroid appended to the file; NexFlow overwrites it")
            }
            buildMap {
                val dir = o.string("m_path")?.trimEnd('/')
                val name = o.string("m_filename")
                listOfNotNull(dir, name).takeIf { it.isNotEmpty() }
                    ?.let { put("path", it.joinToString("/")) }
                o.string("m_logText")?.let { put("content", it) }
            }
        },

        "ControlMediaAction" to MdrOptionMapper { o, warn ->
            o.ignore("m_applicationName", "m_packageName", "m_sendMediaPlayerCommands", "m_simulateMediaButton", "optionInt")
            // m_option is the English label MacroDroid shows ("Play", "Pause", "Play/Pause").
            when (val option = o.string("m_option")?.lowercase()) {
                "play" -> mapOf("action" to "PLAY")
                "pause" -> mapOf("action" to "PAUSE")
                "play/pause", "playpause", null -> mapOf("action" to "TOGGLE")
                else -> {
                    warn("Media action: '$option' has no NexFlow equivalent — imported as play/pause")
                    mapOf("action" to "TOGGLE")
                }
            }
        },

        // Everything lives in a nested config object here, unlike every other action.
        "HttpRequestAction" to MdrOptionMapper { o, warn ->
            val request = o.obj("requestConfig")
            if (request?.get("headerParams")?.let { it.toString() != "[]" } == true ||
                request?.get("queryParams")?.let { it.toString() != "[]" } == true
            ) {
                warn("HTTP request: headers and query parameters are not carried over — add them to the URL or the body")
            }
            if (request?.text("basicAuthEnabled") == "true") {
                warn("HTTP request: basic authentication is not carried over")
            }
            buildMap {
                request?.text("urlToOpen")?.let { put("url", it) }
                put("method", HTTP_METHODS.getOrElse(request?.text("requestType")?.toIntOrNull() ?: 0) { "GET" })
                request?.text("contentBodyText")?.let { put("body", it) }
            }
        },

        "SetVariableAction" to MdrOptionMapper { o, warn ->
            o.ignore(
                "m_booleanInvert", "m_darkMode", "m_doubleRandomMax", "m_doubleRandomMin", "m_falseLabel",
                "m_trueLabel", "m_intRandomMax", "m_intRandomMin", "m_userPromptShowCancel",
                "m_userPromptStopAfterCancel", "varDictionaryKeys",
            )
            if (o.bool("m_intValueIncrement") == true || o.bool("m_intValueDecrement") == true ||
                o.bool("m_intRandom") == true || o.bool("m_intExpression") == true ||
                o.bool("m_userPrompt") == true
            ) {
                warn("Set variable: MacroDroid computed the value (increment/random/expression/prompt) — only a plain value was imported")
            }
            val name = o.obj("m_variable")?.text("m_name")
            val value = o.string("m_newStringValue")
                ?: o.int("m_newIntValue")?.takeIf { it != 0 }?.toString()
                ?: o.double("m_newDoubleValue")?.takeIf { it != 0.0 }?.toString()
                ?: o.bool("m_newBooleanValue")?.toString()
            buildMap {
                name?.let { put("variable_name", it) }
                value?.let { put("value", it) }
            }
        },

        // m_option 0 = a fixed number of passes; the other modes loop on a condition.
        "LoopAction" to MdrOptionMapper { o, warn ->
            if (o.int("m_option") != 0) {
                warn("Repeat: MacroDroid looped on a condition; NexFlow repeats a fixed number of times — check the count")
            }
            mapOf("count" to (o.int("m_fixedOptionCount") ?: 1).coerceAtLeast(1).toString())
        },

        "TouchScreenAction" to MdrOptionMapper { o, _ ->
            buildMap {
                o.int("m_xLocation")?.let { put("x", it.toString()) }
                o.int("m_yLocation")?.let { put("y", it.toString()) }
                put("duration", "50")
            }
        },

        "UIInteractionAction" to MdrOptionMapper { o, warn ->
            o.ignore("action")
            val config = o.obj("uiInteractionConfiguration")
            val get = { key: String -> config?.text(key) }
            when (config?.text("type")) {
                "Click" -> {
                    val point = config["xyPoint"] as? JsonObject
                    val x = point?.text("x")
                    val y = point?.text("y")
                    if (x == null || y == null) {
                        warn("UI interaction: MacroDroid clicked a view by text/id rather than a screen position, which NexFlow cannot do — set coordinates in the editor")
                        emptyMap()
                    } else {
                        mapOf("x" to x, "y" to y, "duration" to "50")
                    }
                }
                "Gesture" -> buildMap {
                    get("startX")?.let { put("x1", it) }
                    get("startY")?.let { put("y1", it) }
                    get("endX")?.let { put("x2", it) }
                    get("endY")?.let { put("y2", it) }
                    put("duration", get("durationMs")?.takeIf { it != "0" } ?: "300")
                    if (get("xyPercentages") == "true") {
                        warn("Swipe: MacroDroid stored the points as percentages of the screen; NexFlow uses pixels — check the coordinates")
                    }
                }
                else -> emptyMap()
            }
        },

        "SetWallpaperAction" to MdrOptionMapper { o, warn ->
            o.ignore(
                "m_imageName", "m_wallpaperUriString", "m_option",
                "m_liveWallpaperName", "m_liveWallpaperPackage", "m_liveWallpaperClassName",
            )
            warn("Set wallpaper: the image is a file on the old device and cannot be imported — pick an image in the editor")
            // m_screenOption: 0 = both, 1 = home, 2 = lock.
            mapOf(
                "target" to when (o.int("m_screenOption")) {
                    1 -> "HOME"
                    2 -> "LOCK"
                    else -> "BOTH"
                },
            )
        },

        // ---------------------------------------------------------------- constraints

        "BatteryLevelConstraint" to MdrOptionMapper { o, _ ->
            o.ignore("m_equals")
            mapOf(
                "level" to (o.int("m_batteryLevel") ?: 0).toString(),
                "direction" to if (o.bool("m_greaterThan") == true) "ABOVE" else "BELOW",
            )
        },

        "TimeOfDayConstraint" to MdrOptionMapper { o, _ ->
            mapOf(
                "start" to "%02d:%02d".format(o.int("m_startHour") ?: 0, o.int("m_startMinute") ?: 0),
                "end" to "%02d:%02d".format(o.int("m_endHour") ?: 0, o.int("m_endMinute") ?: 0),
            )
        },

        "DayOfWeekConstraint" to MdrOptionMapper { o, _ ->
            val days = o.booleans("m_daysOfWeek").orEmpty()
            mapOf("days" to DAY_IDS.filterIndexed { i, _ -> days.getOrElse(i) { false } }.joinToString(","))
        },

        "WifiConstraint" to MdrOptionMapper { o, warn ->
            o.ignore("m_SSIDList")
            buildMap {
                o.string("m_SSID")?.let { put("ssid", it) }
                connectionEvent(o.int("m_wifiState"), warn, "Wi-Fi condition")?.let { put("state", it) }
            }
        },

        "BluetoothConstraint" to MdrOptionMapper { o, warn ->
            o.ignore("m_deviceAddress", "m_anyDevice")
            buildMap {
                if (o.bool("m_anyDevice") != true) o.string("m_deviceName")?.let { put("device_name", it) }
                connectionEvent(o.int("m_btState"), warn, "Bluetooth condition")?.let { put("state", it) }
            }
        },

        "ScreenOnOffConstraint" to MdrOptionMapper { o, _ ->
            mapOf("state" to if (o.bool("m_screenOn") != false) "ON" else "OFF")
        },

        "ExternalPowerConstraint" to MdrOptionMapper { o, _ ->
            o.ignore("m_powerConnectedOptions")
            mapOf("state" to if (o.bool("m_externalPower") != false) "CHARGING" else "NOT_CHARGING")
        },
    )
}
