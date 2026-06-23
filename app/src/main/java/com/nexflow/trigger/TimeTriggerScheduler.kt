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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.receiver.TimeAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules TIME triggers through [AlarmManager] instead of an in-process polling loop.
 *
 * Why not a coroutine `while(true) { delay() }`: that loop only runs while the foreground
 * service is alive and its `delay` is suspended (not fired) during Doze, so scheduled flows
 * were silently skipped when the screen was off or the OEM killed the service. An AlarmManager
 * alarm is held by the system, survives process death, and (with setExactAndAllowWhileIdle)
 * fires through Doze.
 *
 * Permission: prefers exact alarms but degrades gracefully. See [canScheduleExact].
 */
@Singleton
class TimeTriggerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Trigger ids we currently hold an alarm for, so [sync] can cancel removed ones. */
    private val scheduled = mutableSetOf<String>()

    /**
     * Reconcile scheduled alarms with the current set of enabled flows: schedule the next
     * occurrence for every enabled TIME trigger and cancel alarms whose trigger disappeared.
     * Called whenever the enabled-flow set changes and on boot.
     */
    @Synchronized
    fun sync(flows: List<Flow>) {
        val active = mutableSetOf<String>()
        flows.filter { it.enabled }.forEach { flow ->
            flow.triggers.filter { it.type == TriggerType.TIME }.forEach { trigger ->
                active += trigger.id
                scheduleNext(flow.id, trigger.id, trigger.config)
            }
        }
        (scheduled - active).forEach { cancel(it) }
        scheduled.clear()
        scheduled += active
    }

    /**
     * Schedule (or replace) the alarm for a single TIME trigger at its next occurrence.
     * Re-invoked by [TimeAlarmReceiver] after each fire to chain repeating alarms.
     * No-op for ONCE triggers that have no future occurrence.
     */
    @Synchronized
    fun scheduleNext(flowId: String, triggerId: String, config: Map<String, String>) {
        val nextAt = computeNextFireTime(config, System.currentTimeMillis()) ?: return
        // FLAG_UPDATE_CURRENT (without FLAG_NO_CREATE) always returns a non-null PendingIntent.
        val pending = pendingIntent(flowId, triggerId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        scheduled += triggerId
        if (canScheduleExact()) {
            // Fires at a near-exact time even in Doze. Also grants the app a temporary
            // allowlist to start the foreground service from TimeAlarmReceiver.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending)
        } else {
            // Permission-free fallback: still wakes through Doze, but the OS may batch it
            // into a maintenance window (delivery within a few minutes, not to the second).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending)
        }
    }

    @Synchronized
    fun cancel(triggerId: String) {
        alarmManager.cancel(pendingIntent("", triggerId, PendingIntent.FLAG_NO_CREATE) ?: return)
        scheduled -= triggerId
    }

    private fun pendingIntent(flowId: String, triggerId: String, extraFlags: Int): PendingIntent? {
        val intent = Intent(context, TimeAlarmReceiver::class.java).apply {
            // A stable action+data keeps the PendingIntent identity tied to the trigger so
            // FLAG_UPDATE_CURRENT replaces (rather than stacks) the alarm.
            action = TimeAlarmReceiver.ACTION_FIRE
            putExtra(TimeAlarmReceiver.EXTRA_FLOW_ID, flowId)
            putExtra(TimeAlarmReceiver.EXTRA_TRIGGER_ID, triggerId)
        }
        return PendingIntent.getBroadcast(
            context,
            triggerId.hashCode(),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * True if the app may schedule exact alarms. On API < 31 the legacy SCHEDULE_EXACT_ALARM
     * is granted at install; on API 31+ the user can revoke it via system Settings, and on
     * API 33+ (targetSdk 33+) it is denied by default.
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    companion object {
        /** Intent that sends the user to the system "Alarms & reminders" grant screen. */
        fun requestExactAlarmPermissionIntent(packageName: String): Intent =
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:$packageName"))

        /**
         * Next firing instant (epoch millis) strictly after [fromMillis] that matches the
         * trigger's "time" (HH:mm), "repeat" (DAILY/WEEKDAYS/WEEKENDS/CUSTOM/ONCE) and, for
         * CUSTOM, the "days" list (MON,TUE,...). Returns null if nothing ever matches.
         *
         * Field semantics are identical to the former TimeTriggerHandler polling loop.
         */
        fun computeNextFireTime(config: Map<String, String>, fromMillis: Long): Long? {
            val parts = (config["time"] ?: "08:00").split(":").map { it.toIntOrNull() ?: 0 }
            val targetHour = parts.getOrElse(0) { 8 }
            val targetMinute = parts.getOrElse(1) { 0 }
            val repeat = config["repeat"] ?: "DAILY"
            val customDays = config["days"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()

            val cal = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If today's target time has already passed, start scanning from tomorrow.
            if (cal.timeInMillis <= fromMillis) cal.add(Calendar.DAY_OF_YEAR, 1)

            // Scan up to a week for the first day satisfying the repeat rule.
            repeat(8) {
                if (dayMatches(repeat, customDays, cal.get(Calendar.DAY_OF_WEEK))) {
                    return cal.timeInMillis
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return null
        }

        private fun dayMatches(repeat: String, customDays: List<String>, dayOfWeek: Int): Boolean =
            when (repeat) {
                "WEEKDAYS" -> dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
                "WEEKENDS" -> dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                "CUSTOM" -> calDayId(dayOfWeek) in customDays
                else -> true // DAILY or ONCE: the next occurrence of the time, any day
            }

        private fun calDayId(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.MONDAY -> "MON"; Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"; Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"; Calendar.SATURDAY -> "SAT"
            else -> "SUN"
        }
    }
}
