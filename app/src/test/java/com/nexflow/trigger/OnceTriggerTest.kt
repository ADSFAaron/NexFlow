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
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * "Once" used to be "the next 08:00" recomputed at every reschedule — and the app reschedules on
 * any change to any flow, every service start and every boot — so after it fired, the next
 * reschedule armed it for tomorrow. It ran every day.
 */
class OnceTriggerTest {

    private val day = 24 * 60 * 60 * 1000L

    /** Today at [hour]:00, local time. */
    private fun today(hour: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val once = mapOf("time" to "08:00", "repeat" to "ONCE")

    @Test
    fun `confirming a once trigger fixes its next occurrence`() {
        val stamped = OnceTrigger.stamp(once, now = today(7))
        assertEquals(today(8), OnceTrigger.at(stamped))

        val later = OnceTrigger.stamp(once, now = today(9))
        assertEquals(today(8) + day, OnceTrigger.at(later), "past today's 08:00, it is tomorrow's")
    }

    /** The bug itself: after it fired, every later reschedule must find nothing left to do. */
    @Test
    fun `a once trigger that has fired is never scheduled again`() {
        val stamped = OnceTrigger.stamp(once, now = today(7))

        assertEquals(today(8), TimeTriggerScheduler.computeNextFireTime(stamped, today(7)))
        assertNull(TimeTriggerScheduler.computeNextFireTime(stamped, today(8)))
        assertNull(TimeTriggerScheduler.computeNextFireTime(stamped, today(8) + 3 * day))
    }

    @Test
    fun `confirming it again re-arms it`() {
        val fired = OnceTrigger.stamp(once, now = today(7))
        val rearmed = OnceTrigger.stamp(fired, now = today(9))
        assertEquals(today(8) + day, TimeTriggerScheduler.computeNextFireTime(rearmed, today(9)))
    }

    @Test
    fun `switching away from once drops the moment`() {
        val stamped = OnceTrigger.stamp(once, now = today(7))
        val daily = OnceTrigger.stamp(stamped + ("repeat" to "DAILY"), now = today(7))
        assertNull(OnceTrigger.at(daily))
        // And a daily trigger is unaffected by the change: still every day.
        assertEquals(today(8) + day, TimeTriggerScheduler.computeNextFireTime(daily, today(9)))
    }

    private fun flowWith(vararg triggers: Trigger) = Flow(
        id = "f", schemaVersion = 1, name = "f", description = "", author = null, tags = emptyList(),
        enabled = true, createdAt = 0L, updatedAt = 0L, triggers = triggers.toList(),
        triggerLogic = TriggerLogic.ANY, conditions = emptyList(), actions = emptyList(), variables = emptyList(),
    )

    /** Older versions, imports and the AI produce "once" without a moment; it is given one. */
    @Test
    fun `a once trigger without a moment gets one, and nothing else is touched`() {
        val bare = Trigger("t1", TriggerType.TIME, once)
        val daily = Trigger("t2", TriggerType.TIME, mapOf("time" to "08:00", "repeat" to "DAILY"))
        val wifi = Trigger("t3", TriggerType.WIFI, mapOf("repeat" to "ONCE"))

        val stamped = OnceTrigger.stampMissing(flowWith(bare, daily, wifi), now = today(7))

        assertEquals(today(8), OnceTrigger.at(stamped.triggers[0].config))
        assertEquals(daily, stamped.triggers[1])
        assertEquals(wifi, stamped.triggers[2])
    }

    @Test
    fun `a flow that needs no moment is returned as is`() {
        val already = Trigger("t1", TriggerType.TIME, OnceTrigger.stamp(once, today(7)))
        val flow = flowWith(already)
        assertSame(flow, OnceTrigger.stampMissing(flow, now = today(9)))
    }

    @Test
    fun `other repeats are untouched by the once rule`() {
        val weekdays = mapOf("time" to "08:00", "repeat" to "WEEKDAYS")
        assertFalse(OnceTrigger.isOnce(weekdays))
        assertNotNull(TimeTriggerScheduler.computeNextFireTime(weekdays, today(9)))
        assertTrue(OnceTrigger.isOnce(once))
    }
}
