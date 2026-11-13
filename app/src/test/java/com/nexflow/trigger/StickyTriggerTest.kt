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

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Wi-Fi, headset and battery state arrive as sticky broadcasts, and registering a receiver for
 * one redelivers the current value "as if it had just been broadcast" (Context.registerReceiver).
 * The engine registers on every start, so without filtering that out "connected to Wi-Fi",
 * "headset plugged in" and "battery below 20%" fired whenever the service (re)started while the
 * phone simply was in that state.
 */
@RunWith(AndroidJUnit4::class)
class StickyTriggerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val events = mutableListOf<TriggerEvent>()

    @After
    fun tearDown() = scope.cancel()

    private fun observe(handler: TriggerHandler, type: TriggerType, config: Map<String, String>) {
        handler.observe(Trigger("t", type, config)).onEach { events += it }.launchIn(scope)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun broadcast(intent: Intent) {
        context.sendStickyBroadcast(intent)
        idle()
    }

    private fun headset(state: Int) = Intent(AudioManager.ACTION_HEADSET_PLUG).putExtra("state", state)

    private fun battery(level: Int) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level)
        .putExtra(BatteryManager.EXTRA_SCALE, 100)

    @Suppress("DEPRECATION")
    private fun wifi(connected: Boolean) = Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION).putExtra(
        WifiManager.EXTRA_NETWORK_INFO,
        ShadowNetworkInfo.newInstance(
            if (connected) NetworkInfo.DetailedState.CONNECTED else NetworkInfo.DetailedState.DISCONNECTED,
            1, 0, true,
            if (connected) NetworkInfo.State.CONNECTED else NetworkInfo.State.DISCONNECTED,
        ),
    )

    @Test
    fun headsetAlreadyPluggedIn_doesNotFire_butPluggingItInDoes() {
        broadcast(headset(1))
        observe(HeadsetTriggerHandler(context), TriggerType.HEADSET_PLUG, mapOf("event" to "CONNECTED"))
        assertEquals(0, events.size)

        broadcast(headset(0))
        broadcast(headset(1))
        assertEquals(1, events.size)
    }

    @Test
    fun batteryAlreadyLow_doesNotFire_butDroppingBelowDoes() {
        broadcast(battery(10))
        observe(BatteryTriggerHandler(context), TriggerType.BATTERY, mapOf("level" to "20", "direction" to "BELOW"))
        assertEquals(0, events.size)

        broadcast(battery(50))
        broadcast(battery(15))
        assertEquals(1, events.size)
    }

    @Test
    fun wifiAlreadyConnected_doesNotFire_butConnectingDoes() {
        broadcast(wifi(true))
        observe(WifiTriggerHandler(context), TriggerType.WIFI, mapOf("event" to "CONNECTED"))
        assertEquals(0, events.size)

        broadcast(wifi(false))
        broadcast(wifi(true))
        assertEquals(1, events.size)
    }

    /** One disconnect passes through several detailed states, each its own broadcast. */
    @Test
    fun wifiDisconnect_firesOnceAcrossItsIntermediateStates() {
        broadcast(wifi(true))
        observe(WifiTriggerHandler(context), TriggerType.WIFI, mapOf("event" to "DISCONNECTED"))

        broadcast(wifi(false))
        broadcast(wifi(false))
        broadcast(wifi(false))
        assertEquals(1, events.size)
    }
}
