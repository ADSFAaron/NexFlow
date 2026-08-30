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

import android.util.Log
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.trigger.TimeTriggerScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [FlowEngine.runningFlows] is what every "running" indicator in the app reads — the list card's
 * badge, the foreground-service notification and the status popup. Its value is only ever right
 * if it is the same state the engine uses to drop overlapping runs, so these tests pin both
 * halves: the list reflects a run in progress, and claiming a slot is what decides whether a
 * second run of the same flow is allowed to start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowEngineRunningStateTest {

    /** An executor that parks mid-run, so a run can be observed while it is still going. */
    private class BlockingExecutor : ActionExecutor {
        override val supportedType = ActionType.TOAST
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var runCount = 0

        override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
            runCount++
            started.complete(Unit)
            release.await()
            return ActionResult.Success
        }
    }

    @BeforeEach
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unmockLog() = unmockkStatic(Log::class)

    private fun engineFor(flow: Flow, repository: FlowRepository, executor: ActionExecutor): FlowEngine {
        coEvery { repository.getById(flow.id) } returns flow
        // Execution toast disabled so a run never touches android.widget.Toast on the JVM.
        val prefs = mockk<android.content.SharedPreferences> {
            every { getBoolean(any(), any()) } returns false
        }
        val context = mockk<android.content.Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return FlowEngine(
            repository = repository,
            globalVariableRepository = mockk<GlobalVariableRepository>(relaxed = true),
            triggerHandlerSet = emptySet(),
            actionExecutorSet = setOf(executor),
            conditionEvaluatorSet = emptySet(),
            timeTriggerScheduler = mockk<TimeTriggerScheduler>(relaxed = true),
            context = context,
        )
    }

    @Test
    fun `a flow is listed as running for as long as its actions are running`() = runTest {
        val executor = BlockingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow()
        val engine = engineFor(flow, repository, executor)

        assertTrue(engine.runningFlows.value.isEmpty(), "nothing runs before runNow is called")

        val run = launch { engine.runNow(flow.id) }
        executor.started.await()

        val running = engine.runningFlows.value.single()
        assertEquals(flow.id, running.id)
        // The name travels with the entry: the service notification names the flow without ever
        // touching the repository.
        assertEquals("test", running.name)

        executor.release.complete(Unit)
        run.join()
        assertTrue(engine.runningFlows.value.isEmpty(), "the entry must be cleared when the run ends")
    }

    @Test
    fun `a second run of a flow already running is dropped and does not duplicate the entry`() = runTest {
        val executor = BlockingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow()
        val engine = engineFor(flow, repository, executor)

        val first = launch { engine.runNow(flow.id) }
        executor.started.await()

        // Same flow again while the first is still going: the engine reports "never started".
        assertNull(engine.runNow(flow.id))
        assertEquals(1, engine.runningFlows.value.size, "an overlapping run must not add an entry")
        assertEquals(1, executor.runCount, "the dropped run must not have executed any action")

        executor.release.complete(Unit)
        first.join()
    }

    @Test
    fun `a run that fails still clears its running entry`() = runTest {
        val throwing = object : ActionExecutor {
            override val supportedType = ActionType.TOAST
            override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult =
                throw IllegalStateException("boom")
        }
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow()
        val engine = engineFor(flow, repository, throwing)

        engine.runNow(flow.id)

        // A stuck entry would leave the card spinning and the notification lying forever, with
        // no way back short of restarting the service.
        assertTrue(engine.runningFlows.value.isEmpty())
    }

    private fun testFlow() = Flow(
        id = "f1",
        schemaVersion = 1,
        name = "test",
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = emptyList(),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = listOf(
            Action(id = "a0", type = ActionType.TOAST, config = mapOf("message" to "hi"), order = 0, enabled = true),
        ),
        variables = emptyList(),
    )
}
