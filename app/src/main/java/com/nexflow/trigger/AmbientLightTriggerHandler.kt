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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fires when ambient light crosses a lux threshold (config: `mode` BELOW/ABOVE,
 * `threshold_lux`). Edge-triggered: the first sample only records which side we're on;
 * an event fires when a later sample crosses to the matching side — so a flow armed in a
 * dark room doesn't fire immediately.
 */
@Singleton
class AmbientLightTriggerHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.AMBIENT_LIGHT

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val below = trigger.config["mode"]?.uppercase() != "ABOVE"
        val threshold = trigger.config["threshold_lux"]?.trim()?.toFloatOrNull() ?: 50f

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            close()
            return@callbackFlow
        }

        var wasSatisfied: Boolean? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0]
                val satisfied = if (below) lux < threshold else lux > threshold
                if (satisfied && wasSatisfied == false) {
                    trySend(TriggerEvent(trigger.id, ""))
                }
                wasSatisfied = satisfied
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
