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
package com.nexflow.condition

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

class TimeConditionEvaluatorsTest {

    private fun at(hour: Int, minute: Int = 0, dayOfWeek: Int = Calendar.WEDNESDAY): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            // DAY_OF_WEEK last: setting the time fields can roll the date on some calendars.
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }

    // ----- TIME_RANGE -----

    @Test
    fun `a normal range is inclusive of the start and exclusive of the end`() {
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("09:00", "17:00", at(9, 0)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("09:00", "17:00", at(16, 59)))
        assertFalse(TimeRangeConditionEvaluator.isWithinRange("09:00", "17:00", at(17, 0)))
        assertFalse(TimeRangeConditionEvaluator.isWithinRange("09:00", "17:00", at(8, 59)))
    }

    @Test
    fun `a range whose end precedes its start wraps past midnight`() {
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("22:00", "07:00", at(23, 30)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("22:00", "07:00", at(0, 5)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("22:00", "07:00", at(6, 59)))
        assertFalse(TimeRangeConditionEvaluator.isWithinRange("22:00", "07:00", at(7, 0)))
        assertFalse(TimeRangeConditionEvaluator.isWithinRange("22:00", "07:00", at(12, 0)))
    }

    @Test
    fun `equal bounds mean the whole day, not an empty window`() {
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("08:00", "08:00", at(3, 0)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("08:00", "08:00", at(8, 0)))
    }

    @Test
    fun `missing or malformed bounds fall back to the whole day`() {
        // A half-filled condition must not quietly stop the flow from ever running.
        assertTrue(TimeRangeConditionEvaluator.isWithinRange(null, null, at(4, 0)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("", "", at(4, 0)))
        assertTrue(TimeRangeConditionEvaluator.isWithinRange("25:00", "nonsense", at(4, 0)))
    }

    // ----- DAY_OF_WEEK -----

    @Test
    fun `only the selected days match`() {
        assertTrue(DayOfWeekConditionEvaluator.matchesToday("MON,WED,FRI", at(12, dayOfWeek = Calendar.WEDNESDAY)))
        assertFalse(DayOfWeekConditionEvaluator.matchesToday("MON,WED,FRI", at(12, dayOfWeek = Calendar.TUESDAY)))
        assertTrue(DayOfWeekConditionEvaluator.matchesToday(" sun ", at(12, dayOfWeek = Calendar.SUNDAY)))
    }

    @Test
    fun `an empty day list is no restriction`() {
        assertTrue(DayOfWeekConditionEvaluator.matchesToday(null, at(12, dayOfWeek = Calendar.TUESDAY)))
        assertTrue(DayOfWeekConditionEvaluator.matchesToday("", at(12, dayOfWeek = Calendar.TUESDAY)))
        assertTrue(DayOfWeekConditionEvaluator.matchesToday(" , ", at(12, dayOfWeek = Calendar.TUESDAY)))
    }
}
