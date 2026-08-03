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
import com.nexflow.event.NotificationEvent
import com.nexflow.event.NotificationEventSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationTriggerHandlerTest {

    private val handler = NotificationTriggerHandler()

    private fun trigger(config: Map<String, String>) =
        Trigger(id = "t1", type = TriggerType.NOTIFICATION_RECEIVED, config = config)

    /** Collects what the handler emits while [emissions] posts notifications. */
    private fun TestScope.eventsFor(
        config: Map<String, String>,
        emissions: List<NotificationEvent>,
    ): List<TriggerEvent> {
        val received = mutableListOf<TriggerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            handler.observe(trigger(config)).collect { received += it }
        }
        emissions.forEach { NotificationEventSource.emit(it) }
        job.cancel()
        return received
    }

    @Test
    fun `the app filter still applies`() = runTest {
        val events = eventsFor(
            mapOf("package_name" to "com.chat"),
            listOf(
                NotificationEvent("com.chat", "Alice", "hi"),
                NotificationEvent("com.other", "Bob", "hi"),
            ),
        )
        assertEquals(1, events.size)
        assertEquals("com.chat", events.single().metadata[TriggerVariables.PACKAGE])
    }

    @Test
    fun `a keyword matches across title and body by default`() = runTest {
        val events = eventsFor(
            mapOf("keyword" to "urgent"),
            listOf(
                NotificationEvent("com.mail", "Urgent: reply", "please"),
                NotificationEvent("com.mail", "Newsletter", "this is urgent"),
                NotificationEvent("com.mail", "Newsletter", "nothing to see"),
            ),
        )
        assertEquals(2, events.size)
    }

    @Test
    fun `match_field narrows the search to one part`() = runTest {
        val titleOnly = eventsFor(
            mapOf("keyword" to "urgent", "match_field" to NotificationTriggerHandler.FIELD_TITLE),
            listOf(
                NotificationEvent("com.mail", "Urgent", "later"),
                NotificationEvent("com.mail", "Newsletter", "urgent"),
            ),
        )
        assertEquals(1, titleOnly.size)
        assertEquals("Urgent", titleOnly.single().metadata[TriggerVariables.TITLE])

        val textOnly = eventsFor(
            mapOf("keyword" to "urgent", "match_field" to NotificationTriggerHandler.FIELD_TEXT),
            listOf(
                NotificationEvent("com.mail", "Urgent", "later"),
                NotificationEvent("com.mail", "Newsletter", "urgent"),
            ),
        )
        assertEquals(1, textOnly.size)
        assertEquals("urgent", textOnly.single().metadata[TriggerVariables.TEXT])
    }

    @Test
    fun `regex mode is honoured`() = runTest {
        val events = eventsFor(
            mapOf("keyword" to """\d{6}""", "match_mode" to TextMatcher.MODE_REGEX),
            listOf(
                NotificationEvent("com.bank", "Code", "123456"),
                NotificationEvent("com.bank", "Code", "12"),
            ),
        )
        assertEquals(1, events.size)
    }

    @Test
    fun `title and text reach the flow as trigger metadata`() = runTest {
        val events = eventsFor(
            emptyMap(),
            listOf(NotificationEvent("com.chat", "Alice", "see you at 7")),
        )
        val metadata = events.single().metadata
        assertEquals("com.chat", metadata[TriggerVariables.PACKAGE])
        assertEquals("Alice", metadata[TriggerVariables.TITLE])
        assertEquals("see you at 7", metadata[TriggerVariables.TEXT])
    }

    @Test
    fun `an unconfigured trigger still fires for everything`() = runTest {
        val events = eventsFor(
            emptyMap(),
            listOf(
                NotificationEvent("com.a", "", ""),
                NotificationEvent("com.b", "x", "y"),
            ),
        )
        assertTrue(events.size == 2, "blank filters must not silence the trigger")
    }
}
