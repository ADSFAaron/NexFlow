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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.interpreter.ExpressionEvaluator
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Only above 30% battery". Config: `direction` (`ABOVE`/`BELOW`) and `level` (0–100).
 *
 * `ABOVE` is inclusive of the threshold, `BELOW` likewise — the same convention the BATTERY
 * trigger uses, so a condition and a trigger written with the same numbers agree.
 */
@Singleton
class BatteryLevelConditionEvaluator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConditionEvaluator {

    override val supportedType = ConditionType.BATTERY_LEVEL

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        // No threshold = nothing to restrict. Substituting a default here would silently block
        // every run of a flow whose condition arrived from an import without a level.
        val threshold = condition.config["level"]?.trim()?.toIntOrNull() ?: return true
        val level = context.batteryLevel() ?: return false
        return if (condition.config["direction"]?.trim()?.uppercase() == "ABOVE") {
            level >= threshold
        } else {
            level <= threshold
        }
    }
}

/** "Only while charging". Config: `state` = `CHARGING` (default) or `NOT_CHARGING`. */
@Singleton
class ChargingConditionEvaluator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConditionEvaluator {

    override val supportedType = ConditionType.CHARGING

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        val charging = context.isCharging() ?: return false
        val wantCharging = condition.config["state"]?.trim()?.uppercase() != "NOT_CHARGING"
        return charging == wantCharging
    }
}

/** "Only while the screen is off". Config: `state` = `ON` (default) or `OFF`. */
@Singleton
class ScreenStateConditionEvaluator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConditionEvaluator {

    override val supportedType = ConditionType.SCREEN_STATE

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        val wantOn = condition.config["state"]?.trim()?.uppercase() != "OFF"
        return powerManager.isInteractive == wantOn
    }
}

/**
 * A free-form comparison over the run's variables — the same syntax as the If action, but decided
 * before any action runs. Config: `expression`, e.g. `{{trigger.sender}} == 0912345678`.
 *
 * A blank expression is no restriction; a malformed one evaluates to false, exactly as it would
 * inside an If.
 */
@Singleton
class ExpressionConditionEvaluator @Inject constructor() : ConditionEvaluator {

    override val supportedType = ConditionType.EXPRESSION

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        val expression = condition.config["expression"]?.trim().orEmpty()
        if (expression.isEmpty()) return true
        return ExpressionEvaluator.evaluate(expression, variables)
    }
}

/**
 * Current charge percentage from the sticky ACTION_BATTERY_CHANGED broadcast, or null when the
 * platform has no value to give (emulators without a battery, a null sticky intent).
 */
private fun Context.batteryStatus(): Intent? =
    registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

private fun Context.batteryLevel(): Int? {
    val intent = batteryStatus() ?: return null
    val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    if (raw < 0 || scale <= 0) return null
    return (raw * 100) / scale
}

private fun Context.isCharging(): Boolean? {
    val intent = batteryStatus() ?: return null
    return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
        -1 -> null
        else -> false
    }
}
