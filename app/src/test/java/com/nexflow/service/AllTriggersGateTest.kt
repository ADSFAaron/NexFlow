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
package com.nexflow.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AllTriggersGateTest {

    private var clock = 0L
    private val gate = AllTriggersGate(windowMs = 60_000L, now = { clock })

    private fun fire(
        trigger: String,
        required: List<String> = listOf("a", "b"),
        variables: Map<String, String> = emptyMap(),
        flowId: String = "f1",
    ) = gate.onFire(flowId, required, trigger, variables)

    @Test
    fun `waits until every trigger has fired`() {
        assertNull(fire("a"))
        assertNotNull(fire("b"))
    }

    @Test
    fun `order does not matter and the set resets after completing`() {
        assertNull(fire("b"))
        assertNotNull(fire("a"))
        // Next round starts empty: one trigger alone must not fire the flow again.
        assertNull(fire("a"))
        assertNotNull(fire("b"))
    }

    @Test
    fun `a fire older than the window no longer counts`() {
        assertNull(fire("a"))
        clock += 60_001L
        assertNull(fire("b"), "a's fire has expired, so b alone must not complete the set")
        assertNotNull(fire("a"))
    }

    @Test
    fun `re-firing the same trigger refreshes it without completing`() {
        assertNull(fire("a"))
        clock += 30_000L
        assertNull(fire("a"))
        clock += 40_000L // 70s after the first fire, 40s after the second
        assertNotNull(fire("b"), "the refreshed fire is still inside the window")
    }

    @Test
    fun `merged variables favour the trigger that completed the set`() {
        fire("a", variables = mapOf("type" to "WIFI", "ssid" to "Home"))
        val merged = fire("b", variables = mapOf("type" to "HEADSET_PLUG", "event" to "CONNECTED"))

        assertEquals(
            mapOf("type" to "HEADSET_PLUG", "ssid" to "Home", "event" to "CONNECTED"),
            merged,
        )
    }

    @Test
    fun `a trigger removed from the flow cannot hold it hostage`() {
        // Fired while the flow still had three triggers, then the user deleted "c".
        assertNull(fire("a", required = listOf("a", "b", "c")))
        assertNotNull(fire("b", required = listOf("a", "b")))
    }

    @Test
    fun `flows are tracked independently`() {
        assertNull(fire("a", flowId = "f1"))
        assertNull(fire("a", flowId = "f2"))
        assertNotNull(fire("b", flowId = "f1"))
        assertNull(fire("a", flowId = "f2"), "f2 still needs b of its own")
    }

    @Test
    fun `clear drops half-finished combinations`() {
        assertNull(fire("a"))
        gate.clear()
        assertNull(fire("b"), "state from before the clear must not complete the set")
    }
}
