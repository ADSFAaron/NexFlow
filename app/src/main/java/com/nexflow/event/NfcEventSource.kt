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

/**
 * Bridges a scanned NFC tag → every subscribed NfcTagTriggerHandler.
 *
 * Live delivery only. A tag that arrives with nobody subscribed — the app was closed and the tag
 * is what woke it — is not held here for later: [com.nexflow.nfc.NfcTagDispatcher] hands it to
 * the service instead, which finds the matching flows itself. Holding it used to deliver it to
 * the first trigger that subscribed and to no other, so with two NFC flows a tag woke the app
 * and then, half the time, did nothing.
 */
object NfcEventSource {
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<String> = _events.asSharedFlow()

    /** True while at least one NFC trigger is listening. */
    val hasListeners: Boolean get() = _events.subscriptionCount.value > 0

    /** @param tagId hex string of the NFC tag UID, e.g. "04A3B2C1" */
    fun emit(tagId: String) {
        _events.tryEmit(tagId)
    }
}
