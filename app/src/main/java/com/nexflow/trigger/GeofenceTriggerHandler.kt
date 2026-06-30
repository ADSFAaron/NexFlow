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

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
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

    private companion object {
        const val TAG = "GeofenceTrigger"
    }

    override val supportedType = TriggerType.GEOFENCE

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = callbackFlow {
        val lat = trigger.config["lat"]?.toDoubleOrNull() ?: run { close(); return@callbackFlow }
        val lng = trigger.config["lng"]?.toDoubleOrNull() ?: run { close(); return@callbackFlow }
        val radiusM = trigger.config["radius_m"]?.toFloatOrNull() ?: 200f
        val targetEvent = trigger.config["event"]?.uppercase() ?: "ENTER"

        // ACCESS_FINE_LOCATION must be granted BEFORE calling addGeofences. Play Services does not
        // surface the missing-permission error through addOnFailureListener or a synchronous throw —
        // it rethrows a SecurityException asynchronously on its own connection-callback thread
        // (onConnected), which no try/catch here can intercept and which crashes the app. So the
        // only safe guard is to never call addGeofences without the permission. This also covers a
        // flow that was persisted as enabled before the permission existed (engine subscribes on
        // startup) and the case where the user revokes location while the flow is enabled.
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            close()
            return@callbackFlow
        }

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

        // Belt-and-suspenders for a permission revoked in the tiny window after the check above.
        try {
            client.addGeofences(request, pendingIntent)
                .addOnFailureListener { e ->
                    // Registration can fail for reasons outside our control — most commonly
                    // GEOFENCE_NOT_AVAILABLE (status 1000) when the device has no usable location
                    // fix / NLP, or the service is briefly unavailable. Close the flow gracefully
                    // instead of rethrowing: propagating the exception would crash the collector
                    // coroutine in the flow engine. The geofence simply isn't armed this time.
                    Log.w(TAG, "addGeofences failed for ${trigger.id}; geofence not armed", e)
                    close()
                }
        } catch (e: SecurityException) {
            // Permission revoked in the tiny window after the check above. Close gracefully —
            // rethrowing (close(e)) would crash the flow-engine collector, same as a failed
            // registration. The geofence just isn't armed.
            Log.w(TAG, "addGeofences SecurityException for ${trigger.id}; geofence not armed", e)
            close()
            return@callbackFlow
        }

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
