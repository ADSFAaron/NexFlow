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

import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.TIME

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = flow {
        val targetTime = trigger.config["time"] ?: "08:00"
        val parts = targetTime.split(":").map { it.toIntOrNull() ?: 0 }
        val targetHour = parts.getOrElse(0) { 8 }
        val targetMinute = parts.getOrElse(1) { 0 }
        val repeat = trigger.config["repeat"] ?: "DAILY"

        var lastFiredDay = -1

        while (true) {
            val now = Calendar.getInstance()
            val day = now.get(Calendar.DAY_OF_YEAR)
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)

            if (hour == targetHour && minute == targetMinute && day != lastFiredDay) {
                val customDays = trigger.config["days"]?.split(",")?.map { it.trim() } ?: emptyList()
                val calDayId = when (now.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "MON"; Calendar.TUESDAY -> "TUE"
                    Calendar.WEDNESDAY -> "WED"; Calendar.THURSDAY -> "THU"
                    Calendar.FRIDAY -> "FRI"; Calendar.SATURDAY -> "SAT"
                    else -> "SUN"
                }
                val shouldFire = when (repeat) {
                    "WEEKDAYS" -> now.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY
                    "WEEKENDS" -> now.get(Calendar.DAY_OF_WEEK).let {
                        it == Calendar.SATURDAY || it == Calendar.SUNDAY
                    }
                    "CUSTOM" -> calDayId in customDays
                    else -> true // DAILY or ONCE
                }
                if (shouldFire) {
                    emit(TriggerEvent(trigger.id, ""))
                    lastFiredDay = day
                    if (repeat == "ONCE") return@flow
                }
            }

            // Sleep until the start of the next minute
            val secondsLeft = 60 - now.get(Calendar.SECOND)
            delay(secondsLeft * 1_000L)
        }
    }
}
