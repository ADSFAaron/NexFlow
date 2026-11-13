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

import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.trigger.TriggerVariables
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A TIME trigger's alarm going off. The cases that matter are the ones that used to end in
 * nothing at all: automation switched off, and Android refusing to let the app start its
 * service from the background (it allows that only for exact alarms). Users saw a flow not run
 * at its time with no log line and no notice; each of those now leaves both, while a run that
 * did start leaves its own log through the engine and nothing extra here.
 */
class TimeAlarmHandlerTest {

    private val logs = mutableListOf<ExecutionLog>()
    private val chained = mutableListOf<String>()
    private val notified = mutableListOf<Pair<String, String>>()
    private val runs = mutableListOf<Triple<String, Map<String, String>, String>>()

    private var serviceOn = true
    private var startSucceeds = true

    private fun flow(enabled: Boolean = true, repeat: String = "DAILY") = Flow(
        id = "f", schemaVersion = 1, name = "Morning", description = "", author = null,
        icon = null, iconColor = null, tags = emptyList(), enabled = enabled,
        createdAt = 0L, updatedAt = 0L,
        triggers = listOf(Trigger("t", TriggerType.TIME, mapOf("time" to "09:00", "repeat" to repeat))),
        triggerLogic = TriggerLogic.ANY, conditions = emptyList(), actions = emptyList(), variables = emptyList(),
    )

    private fun handler(stored: Flow?): TimeAlarmHandler {
        val repository = mockk<FlowRepository> {
            coEvery { getById("f") } returns stored
            coEvery { saveExecutionLog(any(), any()) } answers { logs += firstArg<ExecutionLog>() }
        }
        return TimeAlarmHandler(
            repository = repository,
            scheduleNext = { flowId, triggerId, _ -> chained += "$flowId/$triggerId" },
            serviceEnabled = { serviceOn },
            startRun = { id, vars, trigger -> runs += Triple(id, vars, trigger); startSucceeds },
            notifyBlocked = { f, time -> notified += f.name to time },
            messages = TimeAlarmHandler.Messages(skippedServiceOff = "switch off", blockedInBackground = "blocked"),
            now = { 1_000L },
        )
    }

    @Test
    fun `a run that starts leaves nothing extra and chains the next one`() = runBlocking {
        handler(flow()).onAlarm("f", "t")

        assertEquals(1, runs.size)
        val (id, vars, trigger) = runs.single()
        assertEquals("f", id)
        assertEquals("t", trigger)
        assertEquals(TriggerType.TIME.name, vars[TriggerVariables.TYPE])
        assertEquals("09:00", vars[TriggerVariables.TIME])
        assertTrue(logs.isEmpty(), "the engine logs a run it starts; nothing should be added here")
        assertTrue(notified.isEmpty())
        assertEquals(listOf("f/t"), chained)
    }

    @Test
    fun `android refusing the background start is logged, notified, and the schedule continues`() = runBlocking {
        startSucceeds = false

        handler(flow()).onAlarm("f", "t")

        val log = logs.single()
        assertEquals(ExecutionStatus.FAIL, log.status)
        assertEquals("blocked", log.errorMessage)
        assertEquals("f", log.flowId)
        assertEquals(1_000L, log.triggeredAt)
        assertEquals(listOf("Morning" to "09:00"), notified)
        assertEquals(listOf("f/t"), chained, "a refusal today must not end tomorrow's run")
    }

    @Test
    fun `with automation off the run is skipped, said in the log, and not started`() = runBlocking {
        serviceOn = false

        handler(flow()).onAlarm("f", "t")

        assertTrue(runs.isEmpty())
        val log = logs.single()
        assertEquals(ExecutionStatus.SKIPPED, log.status)
        assertEquals("switch off", log.errorMessage)
        assertTrue(notified.isEmpty(), "the user turned automation off; no alarm about it")
        assertEquals(listOf("f/t"), chained, "turning automation back on must resume the schedule")
    }

    @Test
    fun `a one-off time trigger is not chained`() = runBlocking {
        handler(flow(repeat = "ONCE")).onAlarm("f", "t")

        assertEquals(1, runs.size)
        assertTrue(chained.isEmpty())
    }

    @Test
    fun `a flow disabled or deleted since the alarm was set is dropped silently`() = runBlocking {
        handler(flow(enabled = false)).onAlarm("f", "t")
        handler(null).onAlarm("f", "t")
        handler(flow()).onAlarm("f", "other-trigger")

        assertTrue(runs.isEmpty())
        assertTrue(logs.isEmpty())
        assertTrue(chained.isEmpty())
    }
}
