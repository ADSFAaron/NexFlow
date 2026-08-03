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

import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerVariables
import com.nexflow.core.automation.util.TextMatcher
import com.nexflow.event.SmsEvent
import com.nexflow.event.SmsEventSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Lives in the `github` test source set: the SMS trigger is compiled out of the Play flavor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmsReceivedTriggerHandlerTest {

    private val handler = SmsReceivedTriggerHandler()

    private fun TestScope.eventsFor(
        config: Map<String, String>,
        emissions: List<SmsEvent>,
    ): List<TriggerEvent> {
        val received = mutableListOf<TriggerEvent>()
        val trigger = Trigger(id = "t1", type = TriggerType.SMS_RECEIVED, config = config)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            handler.observe(trigger).collect { received += it }
        }
        emissions.forEach { SmsEventSource.emit(it) }
        job.cancel()
        return received
    }

    @Test
    fun `body keyword filters on top of the sender`() = runTest {
        val events = eventsFor(
            mapOf("sender" to "0912", "body_keyword" to "code"),
            listOf(
                SmsEvent("0912345678", "Your code is 1234"),
                SmsEvent("0912345678", "Hello there"),
                SmsEvent("0988000000", "Your code is 9999"),
            ),
        )
        assertEquals(1, events.size)
        assertEquals("Your code is 1234", events.single().metadata[TriggerVariables.BODY])
    }

    @Test
    fun `regex mode is honoured on the body`() = runTest {
        val events = eventsFor(
            mapOf("body_keyword" to """\d{4}""", "match_mode" to TextMatcher.MODE_REGEX),
            listOf(
                SmsEvent("0912345678", "code 1234"),
                SmsEvent("0912345678", "no digits here"),
            ),
        )
        assertEquals(1, events.size)
    }

    @Test
    fun `sender and body reach the flow as trigger metadata`() = runTest {
        val events = eventsFor(emptyMap(), listOf(SmsEvent("0912345678", "hi")))
        val metadata = events.single().metadata
        assertEquals("0912345678", metadata[TriggerVariables.SENDER])
        assertEquals("hi", metadata[TriggerVariables.BODY])
    }
}
