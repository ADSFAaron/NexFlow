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
import android.media.AudioManager
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
class HeadsetTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.HEADSET_PLUG

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val targetEvent = trigger.config["event"]?.uppercase() ?: "CONNECTED"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != AudioManager.ACTION_HEADSET_PLUG) return
                // state: 0 = unplugged, 1 = plugged
                val state = intent.getIntExtra("state", -1)
                val eventMatches = when (targetEvent) {
                    "CONNECTED" -> state == 1
                    "DISCONNECTED" -> state == 0
                    else -> false
                }
                if (eventMatches) trySend(TriggerEvent(trigger.id, ""))
            }
        }

        context.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
