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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fires when the device is shaken. Listens to the accelerometer only while at least one
 * enabled flow uses this trigger (the engine collects the flow → we register; it stops →
 * awaitClose unregisters), so there is no idle battery cost.
 */
@Singleton
class ShakeTriggerHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.SHAKE

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        // Total acceleration in g's that counts as a shake.
        val threshold = when (trigger.config["sensitivity"]?.uppercase()) {
            "LOW" -> 3.4f
            "HIGH" -> 2.0f
            else -> 2.7f
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            close()
            return@callbackFlow
        }

        var lastFired = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gForce = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2],
                ) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (gForce > threshold && now - lastFired > DEBOUNCE_MS) {
                    lastFired = now
                    trySend(TriggerEvent(trigger.id, ""))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        /** One shake gesture produces several spikes; collapse them into one event. */
        const val DEBOUNCE_MS = 1500L
    }
}
