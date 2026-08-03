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
package com.nexflow.core.automation.trigger

import com.nexflow.core.automation.model.TriggerType

/**
 * The contract for [TriggerEvent.metadata]: what a trigger reports about the event that fired it.
 *
 * The engine merges the metadata into the run's variable map under the [PREFIX] namespace, so a
 * flow reads the incoming SMS body as `{{trigger.body}}`, the notifying app as `{{trigger.package}}`
 * and so on. Handlers must use the constants here rather than raw strings — [keysFor] is what the
 * editor offers in its insert-variable menu, and a key only a handler knows about would never be
 * offered (or would be offered but never filled).
 */
object TriggerVariables {

    /** Namespace prefix for trigger-supplied values, referenced as `{{trigger.name}}`. */
    const val PREFIX = "trigger."

    // ---- Supplied by the engine for every trigger ----

    /** [TriggerType] name of the trigger that fired, e.g. `SMS_RECEIVED`. */
    const val TYPE = "type"

    /** Epoch milliseconds at which the trigger fired. */
    const val TIMESTAMP = "timestamp"

    // ---- Type-specific ----

    /** BATTERY: charge percentage, 0–100. */
    const val LEVEL = "level"

    /** BATTERY: `true` when the device was charging at the moment the trigger fired. */
    const val CHARGING = "charging"

    /** WIFI: SSID of the network involved (empty when it cannot be read — see WifiTriggerHandler). */
    const val SSID = "ssid"

    /** BLUETOOTH / WIFI / SCREEN / HEADSET_PLUG / GEOFENCE: which side of the event fired. */
    const val EVENT = "event"

    /** BLUETOOTH: device name, empty when the name is not readable without a pairing. */
    const val DEVICE = "device"

    /** APP_LAUNCH / NOTIFICATION_RECEIVED: package name of the app. */
    const val PACKAGE = "package"

    /** INCOMING_CALL: caller number as reported by the platform. */
    const val NUMBER = "number"

    /** SMS_RECEIVED: sender address. */
    const val SENDER = "sender"

    /** SMS_RECEIVED: message body. */
    const val BODY = "body"

    /** NOTIFICATION_RECEIVED: notification title (may be empty). */
    const val TITLE = "title"

    /** NOTIFICATION_RECEIVED: notification body text (may be empty). */
    const val TEXT = "text"

    /** NFC_TAG: scanned tag UID as an uppercase hex string. */
    const val TAG_ID = "tag_id"

    /** GEOFENCE: centre of the area that was entered/left. */
    const val LATITUDE = "lat"

    /** GEOFENCE: centre of the area that was entered/left. */
    const val LONGITUDE = "lng"

    /** AMBIENT_LIGHT: measured illuminance in lux. */
    const val LUX = "lux"

    /** TIME: the scheduled `HH:mm` that fired. */
    const val TIME = "time"

    /** Keys the engine fills in for every trigger, whatever its type. */
    val COMMON: List<String> = listOf(TYPE, TIMESTAMP)

    /**
     * Every `{{trigger.x}}` name a flow using [type] can read, common keys included.
     * Order is stable so the editor's insert menu doesn't shuffle between recompositions.
     */
    fun keysFor(type: TriggerType): List<String> = COMMON + when (type) {
        TriggerType.TIME -> listOf(TIME)
        TriggerType.BATTERY -> listOf(LEVEL, CHARGING)
        TriggerType.BLUETOOTH -> listOf(DEVICE, EVENT)
        TriggerType.WIFI -> listOf(SSID, EVENT)
        TriggerType.SCREEN -> listOf(EVENT)
        TriggerType.APP_LAUNCH -> listOf(PACKAGE)
        TriggerType.INCOMING_CALL -> listOf(NUMBER)
        TriggerType.SMS_RECEIVED -> listOf(SENDER, BODY)
        TriggerType.NOTIFICATION_RECEIVED -> listOf(PACKAGE, TITLE, TEXT)
        TriggerType.DEVICE_BOOT -> emptyList()
        TriggerType.HEADSET_PLUG -> listOf(EVENT)
        TriggerType.NFC_TAG -> listOf(TAG_ID)
        TriggerType.GEOFENCE -> listOf(EVENT, LATITUDE, LONGITUDE)
        TriggerType.SHAKE -> emptyList()
        TriggerType.AMBIENT_LIGHT -> listOf(LUX)
        TriggerType.MANUAL -> emptyList()
    }
}
