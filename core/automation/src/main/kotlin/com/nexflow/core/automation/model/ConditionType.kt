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
package com.nexflow.core.automation.model

/**
 * The constraints a flow can put on top of its triggers: the trigger says *when* to run,
 * a condition says *only if* the device is in this state.
 *
 * [Condition.type] stays a plain string in the domain model and the `.flow` schema so an imported
 * file naming a constraint this build doesn't know about survives a round trip instead of being
 * silently rewritten. Use [fromId] to resolve it; an unresolvable type is a constraint that cannot
 * be checked, and the engine refuses to run the flow rather than pretend it holds.
 */
enum class ConditionType {
    /** Local clock is inside `start`..`end` (`HH:mm`, wrapping past midnight). */
    TIME_RANGE,

    /** Today is one of `days` (`MON,TUE,...`). */
    DAY_OF_WEEK,

    /** Battery percentage is `ABOVE`/`BELOW` `level`. */
    BATTERY_LEVEL,

    /** Device is / is not charging. */
    CHARGING,

    /** Wi-Fi is connected (optionally to `ssid`). */
    WIFI_CONNECTED,

    /** A Bluetooth audio device is connected (optionally matching `device_name`). */
    BLUETOOTH_CONNECTED,

    /** Screen is on or off. */
    SCREEN_STATE,

    /** Free-form expression over flow/global/trigger variables, same syntax as the If action. */
    EXPRESSION,
    ;

    companion object {
        fun fromId(id: String): ConditionType? =
            entries.firstOrNull { it.name.equals(id.trim(), ignoreCase = true) }
    }
}
