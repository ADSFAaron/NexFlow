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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.BATTERY

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val threshold = trigger.config["level"]?.toIntOrNull() ?: 20
        val direction = trigger.config["direction"] ?: "BELOW"
        var lastTriggered = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (raw * 100) / scale else return

                val conditionMet = when (direction.uppercase()) {
                    "ABOVE" -> pct >= threshold
                    else -> pct <= threshold
                }
                // Only fire on the leading edge (not → condition met)
                if (conditionMet && !lastTriggered) {
                    trySend(TriggerEvent(trigger.id, "", metadata = mapOf("level" to pct.toString())))
                }
                lastTriggered = conditionMet
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
