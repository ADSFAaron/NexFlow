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
package com.nexflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.nexflow.event.GeofenceEventSource
import com.nexflow.event.GeofenceTransitionEvent

/** Receives geofence transition intents from the Google Fused Location client. */
class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val triggeringGeofences = event.triggeringGeofences ?: return
        val transitionType = event.geofenceTransition

        triggeringGeofences.forEach { geofence ->
            GeofenceEventSource.emit(
                GeofenceTransitionEvent(
                    requestId = geofence.requestId,
                    transitionType = transitionType,
                ),
            )
        }
    }
}
