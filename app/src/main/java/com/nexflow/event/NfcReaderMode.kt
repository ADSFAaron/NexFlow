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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lets UI ask for the NFC controller to be put in reader mode without touching the adapter.
 *
 * Reader mode is exclusive and device-wide: while it is on, every tap goes to this app's
 * callback and nothing is dispatched to any other app — a URL tag opens nothing, a transit
 * card does nothing. So it must have exactly one owner ([com.nexflow.MainActivity]) that can
 * see every reason it is wanted, and it must be off whenever there is no reason at all.
 *
 * Two things want it: an enabled flow with an NFC trigger (the activity works that out from
 * the repository), and a config screen waiting for the user to tap the tag they are naming —
 * which has to work even before any NFC flow exists. Requests are counted rather than a plain
 * flag so one screen ending its scan cannot switch off another screen's.
 */
object NfcReaderMode {

    private val _scanRequests = MutableStateFlow(0)

    /** How many screens are currently waiting for the user to tap a tag. */
    val scanRequests: StateFlow<Int> = _scanRequests.asStateFlow()

    fun beginScan() = _scanRequests.update { it + 1 }

    fun endScan() = _scanRequests.update { (it - 1).coerceAtLeast(0) }

    private val _scannedTags = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Tags read while a scan is in progress. Separate from [NfcEventSource] on purpose: a tag
     * tapped to fill in a config field must not also fire the flows that trigger on it —
     * otherwise re-scanning the tag of the flow you are editing runs that very flow.
     */
    val scannedTags: SharedFlow<String> = _scannedTags.asSharedFlow()

    fun emitScannedTag(tagId: String) {
        _scannedTags.tryEmit(tagId)
    }
}
