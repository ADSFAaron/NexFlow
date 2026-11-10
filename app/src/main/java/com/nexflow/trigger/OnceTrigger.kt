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
package com.nexflow.trigger

import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType

/**
 * A TIME trigger set to repeat "once" means one particular moment: the next time the clock shows
 * that time *after the user set it*. That moment is stored as [KEY_AT] (epoch millis), and once it
 * has passed the trigger is spent.
 *
 * Without a stored moment, "once" had nothing to remember it by. Every reschedule — any change to
 * any flow, a service restart, a reboot — asked "when is 08:00 next?" afresh and got tomorrow, so
 * a "once" trigger ran every day.
 *
 * The moment is set when the user confirms the trigger in the editor (each confirm re-arms it),
 * and for triggers that arrive without one — older versions, an import, the AI — when their flow
 * is first seen enabled. Moments from elsewhere are dropped on the way in: a file or an AI reply
 * carrying another device's or another day's moment would arrive already spent.
 */
object OnceTrigger {

    const val KEY_AT = "once_at"

    fun isOnce(config: Map<String, String>): Boolean = config["repeat"] == "ONCE"

    /** The stored moment, or null when this trigger has none (yet). */
    fun at(config: Map<String, String>): Long? = config[KEY_AT]?.toLongOrNull()

    /**
     * The config of a TIME trigger the user has just confirmed: "once" gets a fresh moment, the
     * next occurrence of its time after [now]; any other repeat loses a moment it no longer uses.
     */
    fun stamp(config: Map<String, String>, now: Long): Map<String, String> {
        if (!isOnce(config)) return config - KEY_AT
        val next = TimeTriggerScheduler.computeNextFireTime(config - KEY_AT, now) ?: return config - KEY_AT
        return config + (KEY_AT to next.toString())
    }

    /** [flow] with a moment given to each "once" trigger that has none; the same instance if none lacked one. */
    fun stampMissing(flow: Flow, now: Long): Flow {
        if (flow.triggers.none(::needsMoment)) return flow
        return flow.copy(
            triggers = flow.triggers.map { if (needsMoment(it)) it.copy(config = stamp(it.config, now)) else it },
        )
    }

    private fun needsMoment(trigger: Trigger) =
        trigger.type == TriggerType.TIME && isOnce(trigger.config) && at(trigger.config) == null
}
