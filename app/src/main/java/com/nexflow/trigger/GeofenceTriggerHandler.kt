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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.event.GeofenceEventSource
import com.nexflow.receiver.GeofenceTransitionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when the device enters or leaves a defined geofence area.
 * Requires ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION (Android 10+).
 * Events persist even when the app is killed — delivered via GeofenceTransitionReceiver.
 */
@Singleton
class GeofenceTriggerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TriggerHandler {

    override val supportedType = TriggerType.GEOFENCE

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val lat = trigger.config["lat"]?.toDoubleOrNull() ?: run { close(); return@callbackFlow }
        val lng = trigger.config["lng"]?.toDoubleOrNull() ?: run { close(); return@callbackFlow }
        val radiusM = trigger.config["radius_m"]?.toFloatOrNull() ?: 200f
        val targetEvent = trigger.config["event"]?.uppercase() ?: "ENTER"

        val client = LocationServices.getGeofencingClient(context)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            trigger.id.hashCode(),
            Intent(context, GeofenceTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val geofence = Geofence.Builder()
            .setRequestId(trigger.id)
            .setCircularRegion(lat, lng, radiusM)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        client.addGeofences(request, pendingIntent)
            .addOnFailureListener { close(it) }

        val collectionJob = launch {
            GeofenceEventSource.events.collect { event ->
                if (event.requestId != trigger.id) return@collect
                val matches = when (targetEvent) {
                    "ENTER" -> event.transitionType == Geofence.GEOFENCE_TRANSITION_ENTER
                    "EXIT" -> event.transitionType == Geofence.GEOFENCE_TRANSITION_EXIT
                    else -> true
                }
                if (matches) trySend(TriggerEvent(trigger.id, ""))
            }
        }

        awaitClose {
            collectionJob.cancel()
            client.removeGeofences(pendingIntent)
        }
    }
}
