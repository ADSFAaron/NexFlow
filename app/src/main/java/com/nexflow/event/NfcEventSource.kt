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
package com.nexflow.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges a scanned NFC tag → NfcTagTriggerHandler.
 *
 * Two things scan: MainActivity's reader mode while the app is in front, and
 * [com.nexflow.nfc.NfcTagRunActivity] when a tag wakes the app from closed.
 */
object NfcEventSource {
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * A tag scanned before anything was listening. A tag arriving on a dead process starts the
     * service and emits in the same breath, and the engine cannot have subscribed yet — with
     * `replay = 0` that emit lands nowhere, so the tag would wake the app and then do nothing.
     * Held here instead and delivered to the first subscriber.
     */
    private val pending = AtomicReference<String?>(null)

    val events: Flow<String> = _events.asSharedFlow()
        .onSubscription { pending.getAndSet(null)?.let { emit(it) } }

    /** @param tagId hex string of the NFC tag UID, e.g. "04A3B2C1" */
    fun emit(tagId: String) {
        // Checked rather than always buffered: with a live collector the tag must go straight
        // through, and stashing it as well would fire the flow twice.
        if (_events.subscriptionCount.value == 0) pending.set(tagId) else _events.tryEmit(tagId)
    }

    /** Drops a tag held for a subscriber that never came. */
    fun clearPending() {
        pending.set(null)
    }
}
