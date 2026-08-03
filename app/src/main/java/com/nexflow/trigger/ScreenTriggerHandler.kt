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
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.core.automation.trigger.TriggerVariables
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Fires on screen on/off/unlock. Reports which one as `{{trigger.event}}`. */
@Singleton
class ScreenTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.SCREEN

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val targetEvent = trigger.config["event"]?.uppercase() ?: "ON"

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT) // screen unlocked
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val matched = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> targetEvent == "ON"
                    Intent.ACTION_SCREEN_OFF -> targetEvent == "OFF"
                    Intent.ACTION_USER_PRESENT -> targetEvent == "UNLOCKED"
                    else -> false
                }
                if (matched) {
                    trySend(
                        TriggerEvent(
                            triggerId = trigger.id,
                            flowId = "",
                            metadata = mapOf(TriggerVariables.EVENT to targetEvent),
                        ),
                    )
                }
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
