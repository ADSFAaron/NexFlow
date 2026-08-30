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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A tag that wakes a dead process arrives before the engine exists to hear it. The activity
 * starts the service and emits in the same breath, and with `replay = 0` that emit lands
 * nowhere — the tag would wake the app and then do nothing, which is the exact failure the
 * background-NFC feature exists to avoid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcEventSourceTest {

    @BeforeEach
    @AfterEach
    fun clean() = NfcEventSource.clearPending()

    @Test
    fun `a tag scanned before anything is listening reaches the first subscriber`() = runTest {
        NfcEventSource.emit("04A3B2C1")

        assertEquals("04A3B2C1", NfcEventSource.events.first())
    }

    @Test
    fun `a held tag is delivered once, not to every later subscriber`() = runTest {
        NfcEventSource.emit("04A3B2C1")
        assertEquals("04A3B2C1", NfcEventSource.events.first())

        // A second subscriber (the engine re-subscribing after a flow edit) must not re-run the
        // flow with a tag the user tapped once, minutes ago.
        val second = launch { NfcEventSource.events.first() }
        yield()
        assertEquals(true, second.isActive, "the tag must not be replayed to a new subscriber")
        second.cancel()
    }

    @Test
    fun `a tag scanned while the engine is listening goes straight through`() = runTest {
        val received = mutableListOf<String>()
        val job = launch { NfcEventSource.events.take(1).toList(received) }
        yield()

        NfcEventSource.emit("DEADBEEF")
        job.join()

        assertEquals(listOf("DEADBEEF"), received)
        // It must not ALSO have been stashed, or the next subscriber would fire the flow again.
        val later = launch { NfcEventSource.events.first() }
        yield()
        assertEquals(true, later.isActive)
        later.cancel()
    }

    @Test
    fun `a tag held for a subscriber that never came can be dropped`() = runTest {
        NfcEventSource.emit("04A3B2C1")
        // Automation was switched off before the engine started: delivering this whenever the
        // engine next comes up would fire a flow the user triggered days ago.
        NfcEventSource.clearPending()

        val waiting = launch { NfcEventSource.events.first() }
        yield()
        assertEquals(true, waiting.isActive)
        waiting.cancel()
    }
}
