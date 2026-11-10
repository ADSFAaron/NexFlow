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
import org.junit.jupiter.api.Test

/**
 * With the master switch off the service serves one-off runs and then leaves. Leaving too early
 * cancels whatever is still running — and the everyday way to get there was a double tap.
 */
class StopWhenIdleTest {

    private val stops = mutableListOf<Int>()

    private fun keeper(masterSwitchOn: Boolean = false) =
        StopWhenIdle(stop = { stops += it }, keepRunning = { masterSwitchOn })

    /**
     * Second tap on the same widget: the engine drops it at once (the flow is already running), so
     * it ends first. That must not stop the service under the first tap's run.
     */
    @Test
    fun `a run that ends while another is still going does not stop the service`() {
        val k = keeper()
        k.onStartCommand(1); k.runStarted() // first tap: long run
        k.onStartCommand(2); k.runStarted() // second tap: dropped immediately

        k.runEnded()
        assertEquals(emptyList<Int>(), stops)

        k.runEnded()
        assertEquals(listOf(2), stops)
    }

    /**
     * The stop names the newest start. A request already delivered but not yet handled has a
     * newer id, so `stopSelfResult` refuses — the service stays for it.
     */
    @Test
    fun `the stop carries the newest start id`() {
        val k = keeper()
        k.onStartCommand(5); k.runStarted()
        k.onStartCommand(6); k.nothingToDo()
        assertEquals(emptyList<Int>(), stops)

        k.runEnded()
        assertEquals(listOf(6), stops)
    }

    @Test
    fun `with the master switch on the service never stops itself`() {
        val k = keeper(masterSwitchOn = true)
        k.onStartCommand(1); k.runStarted()
        k.runEnded()
        k.nothingToDo()
        assertEquals(emptyList<Int>(), stops)
    }
}
