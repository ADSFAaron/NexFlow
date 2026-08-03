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
import android.net.NetworkInfo
import android.net.wifi.WifiManager
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

/**
 * Fires when Wi-Fi connects or disconnects, optionally only for one SSID.
 * Reports `{{trigger.ssid}}` and `{{trigger.event}}`. The SSID is empty on disconnect and on
 * devices that withhold it without location permission.
 */
@Singleton
class WifiTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.WIFI

    @Suppress("DEPRECATION")
    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val targetSsid = trigger.config["ssid"]?.trim()
        val targetEvent = trigger.config["event"]?.uppercase() ?: "CONNECTED"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    ?: return

                val isConnected = networkInfo.isConnected
                val eventMatches = when (targetEvent) {
                    "CONNECTED" -> isConnected
                    "DISCONNECTED" -> !isConnected
                    else -> false
                }
                if (!eventMatches) return

                // Read the SSID whenever we are connected — both to filter on it and to report
                // it as {{trigger.ssid}}. Needs location permission; null when withheld.
                val currentSsid = if (isConnected) {
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wm.connectionInfo?.ssid
                        ?.removePrefix("\"")?.removeSuffix("\"")
                        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                } else {
                    null
                }

                if (!targetSsid.isNullOrBlank() && isConnected) {
                    if (currentSsid == null || !currentSsid.equals(targetSsid, ignoreCase = true)) return
                }

                trySend(
                    TriggerEvent(
                        triggerId = trigger.id,
                        flowId = "",
                        metadata = mapOf(
                            TriggerVariables.SSID to currentSsid.orEmpty(),
                            TriggerVariables.EVENT to if (isConnected) "CONNECTED" else "DISCONNECTED",
                        ),
                    ),
                )
            }
        }

        context.registerReceiver(receiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
