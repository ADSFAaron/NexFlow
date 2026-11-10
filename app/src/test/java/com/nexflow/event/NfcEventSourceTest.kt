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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * NfcEventSource is live delivery to every NFC trigger listening. A tag that finds nobody
 * listening is not this class's business any more — NfcTagDispatcher sends it to the service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcEventSourceTest {

    /** Each NFC trigger is its own subscriber; a tag must reach all of them, not the first. */
    @Test
    fun `a tag reaches every listening trigger`() = runTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val a = launch { NfcEventSource.events.take(1).toList(first) }
        val b = launch { NfcEventSource.events.take(1).toList(second) }
        yield()

        NfcEventSource.emit("DEADBEEF")
        a.join()
        b.join()

        assertEquals(listOf("DEADBEEF"), first)
        assertEquals(listOf("DEADBEEF"), second)
    }

    /**
     * Nothing is held for later: a subscriber that arrives afterwards (the engine re-subscribing
     * after a flow edit) must not re-run a flow with a tag the user tapped minutes ago.
     */
    @Test
    fun `a tag with nobody listening is not replayed to a later subscriber`() = runTest {
        NfcEventSource.emit("04A3B2C1")

        val later = launch { NfcEventSource.events.first() }
        yield()
        assertTrue(later.isActive)
        later.cancel()
    }

    @Test
    fun `hasListeners tells the dispatcher whether an event would be heard`() = runTest {
        assertFalse(NfcEventSource.hasListeners)
        val job = launch { NfcEventSource.events.first() }
        yield()
        assertTrue(NfcEventSource.hasListeners)
        job.cancel()
        yield()
        assertFalse(NfcEventSource.hasListeners)
    }
}
