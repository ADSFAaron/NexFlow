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

import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Only between 22:00 and 07:00". Config: `start`, `end` as `HH:mm` on the device's local clock.
 *
 * `start` is inclusive and `end` exclusive, so 09:00–17:00 stops holding exactly at 17:00.
 * A range whose end is before its start wraps past midnight; equal values mean the whole day.
 */
@Singleton
class TimeRangeConditionEvaluator @Inject constructor() : ConditionEvaluator {

    override val supportedType = ConditionType.TIME_RANGE

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean =
        isWithinRange(
            start = condition.config["start"],
            end = condition.config["end"],
            now = Calendar.getInstance(),
        )

    companion object {
        /** Pure part, so the wrap-past-midnight rule is testable without a device. */
        fun isWithinRange(start: String?, end: String?, now: Calendar): Boolean {
            val startMinutes = parseMinutes(start) ?: 0
            val endMinutes = parseMinutes(end) ?: MINUTES_PER_DAY
            if (startMinutes == endMinutes) return true
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            return if (startMinutes < endMinutes) {
                nowMinutes in startMinutes until endMinutes
            } else {
                // Wraps midnight: 22:00–07:00 holds late at night and early in the morning.
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }
        }

        /** `HH:mm` → minutes since midnight; null for a blank or malformed value. */
        private fun parseMinutes(value: String?): Int? {
            val parts = value?.trim()?.split(":") ?: return null
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return hour * 60 + minute
        }

        private const val MINUTES_PER_DAY = 24 * 60
    }
}

/**
 * "Only on weekdays". Config: `days` as a comma-separated list of `MON,TUE,WED,THU,FRI,SAT,SUN`
 * — the same encoding the TIME trigger's day picker writes.
 *
 * An empty list is no restriction rather than "never": a half-filled condition must not silently
 * stop every run of the flow.
 */
@Singleton
class DayOfWeekConditionEvaluator @Inject constructor() : ConditionEvaluator {

    override val supportedType = ConditionType.DAY_OF_WEEK

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean =
        matchesToday(condition.config["days"], Calendar.getInstance())

    companion object {
        fun matchesToday(days: String?, now: Calendar): Boolean {
            val selected = days?.split(",")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (selected.isEmpty()) return true
            return dayId(now.get(Calendar.DAY_OF_WEEK)) in selected
        }

        private fun dayId(calendarDay: Int): String = when (calendarDay) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            else -> "SUN"
        }
    }
}
