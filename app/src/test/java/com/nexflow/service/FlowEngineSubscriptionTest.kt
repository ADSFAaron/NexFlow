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
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.nfc.NfcBackgroundDispatch
import com.nexflow.trigger.TimeTriggerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow as KFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * How the engine keeps its trigger subscriptions in step with the enabled flows.
 *
 * It used to tear every subscription down and rebuild them all whenever the set changed, and the
 * runs those triggers had started went down with them. Switching on an unrelated flow stopped a
 * "wait ten minutes, then turn Wi-Fi off" halfway, and re-registering made every sticky trigger
 * (Wi-Fi, headset, battery, geofence) report its current state as a fresh event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowEngineSubscriptionTest {

    @BeforeEach
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun unmockLog() = unmockkStatic(Log::class)

    /** Parks mid-run until cancelled, and records that it was. */
    private class BlockingExecutor : ActionExecutor {
        override val supportedType = ActionType.TOAST
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: Throwable) {
                cancelled = true
                throw e
            }
        }
    }

    /** Counts subscriptions per trigger id; fires once, on the first subscription, if asked to. */
    private class CountingHandler(
        override val supportedType: TriggerType,
        private val fireOnce: Boolean = false,
    ) : TriggerHandler {
        val subscriptions = HashMap<String, Int>()
        private var fired = false
        override fun observe(trigger: Trigger): KFlow<TriggerEvent> = flow {
            subscriptions.merge(trigger.id, 1, Int::plus)
            if (fireOnce && !fired) {
                fired = true
                emit(TriggerEvent(triggerId = trigger.id, flowId = ""))
            }
            awaitCancellation()
        }
    }

    private fun engine(
        repository: FlowRepository,
        handlers: Set<TriggerHandler>,
        executors: Set<ActionExecutor> = emptySet(),
    ): FlowEngine {
        val prefs = mockk<android.content.SharedPreferences> { every { getBoolean(any(), any()) } returns false }
        val context = mockk<android.content.Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
            every { getString(any()) } returns "stopped"
        }
        return FlowEngine(
            repository = repository,
            globalVariableRepository = mockk<GlobalVariableRepository>(relaxed = true),
            triggerHandlerSet = handlers,
            actionExecutorSet = executors,
            conditionEvaluatorSet = emptySet(),
            timeTriggerScheduler = mockk<TimeTriggerScheduler>(relaxed = true),
            nfcBackgroundDispatch = mockk<NfcBackgroundDispatch>(relaxed = true),
            context = context,
        )
    }

    private fun flowWith(id: String, type: TriggerType, config: Map<String, String> = emptyMap()) = Flow(
        id = id, schemaVersion = 1, name = id, description = "", author = null, tags = emptyList(),
        enabled = true, createdAt = 0L, updatedAt = 0L,
        triggers = listOf(Trigger(id = "t-$id", type = type, config = config)),
        triggerLogic = TriggerLogic.ANY, conditions = emptyList(),
        actions = listOf(Action(id = "a-$id", type = ActionType.TOAST, config = emptyMap(), order = 0, enabled = true)),
        variables = emptyList(),
    )

    @Test
    fun `a run in progress survives another flow being switched on`() = runTest {
        val running = flowWith("long", TriggerType.WIFI)
        val other = flowWith("other", TriggerType.HEADSET_PLUG)
        val enabled = MutableStateFlow(listOf(running))
        val repository = mockk<FlowRepository>(relaxed = true)
        every { repository.observeEnabled() } returns enabled
        coEvery { repository.getById("long") } returns running
        val executor = BlockingExecutor()
        val engine = engine(
            repository,
            setOf(CountingHandler(TriggerType.WIFI, fireOnce = true), CountingHandler(TriggerType.HEADSET_PLUG)),
            setOf(executor),
        )
        engine.start(this)
        advanceUntilIdle()
        assertTrue(executor.started.isCompleted)

        enabled.value = listOf(running, other)
        advanceUntilIdle()

        val cancelled = executor.cancelled
        engine.stop()
        assertFalse(cancelled, "switching on an unrelated flow stopped a run in progress")
    }

    /**
     * Editing a flow's actions bumps updated_at, the order of the enabled list. Order is not
     * structure: nothing may be re-registered.
     */
    @Test
    fun `reordering the enabled flows re-subscribes nothing`() = runTest {
        val a = flowWith("a", TriggerType.WIFI)
        val b = flowWith("b", TriggerType.WIFI)
        val enabled = MutableStateFlow(listOf(a, b))
        val repository = mockk<FlowRepository>(relaxed = true)
        every { repository.observeEnabled() } returns enabled
        val wifi = CountingHandler(TriggerType.WIFI)
        val engine = engine(repository, setOf(wifi))
        engine.start(this)
        advanceUntilIdle()

        enabled.value = listOf(b.copy(updatedAt = 99, name = "renamed"), a)
        advanceUntilIdle()

        val counts = wifi.subscriptions.toMap()
        engine.stop()
        assertEquals(mapOf("t-a" to 1, "t-b" to 1), counts)
    }

    @Test
    fun `changing one trigger re-subscribes that trigger alone`() = runTest {
        val a = flowWith("a", TriggerType.WIFI, mapOf("ssid" to "Home"))
        val b = flowWith("b", TriggerType.WIFI)
        val enabled = MutableStateFlow(listOf(a, b))
        val repository = mockk<FlowRepository>(relaxed = true)
        every { repository.observeEnabled() } returns enabled
        val wifi = CountingHandler(TriggerType.WIFI)
        val engine = engine(repository, setOf(wifi))
        engine.start(this)
        advanceUntilIdle()

        enabled.value = listOf(flowWith("a", TriggerType.WIFI, mapOf("ssid" to "Office")), b)
        advanceUntilIdle()

        val counts = wifi.subscriptions.toMap()
        engine.stop()
        assertEquals(mapOf("t-a" to 2, "t-b" to 1), counts)
    }

    /** A run cut short must still be in the log, or it looks like the trigger never fired. */
    @Test
    fun `a run stopped part-way is logged`() = runTest {
        val flow = flowWith("f", TriggerType.MANUAL)
        val repository = mockk<FlowRepository>(relaxed = true)
        coEvery { repository.getById("f") } returns flow
        val saved = slot<ExecutionLog>()
        coEvery { repository.saveExecutionLog(capture(saved), any()) } returns Unit
        val executor = BlockingExecutor()
        val engine = engine(repository, emptySet(), setOf(executor))

        val run = launch { engine.runNow("f") }
        executor.started.await()
        run.cancel()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveExecutionLog(any(), any()) }
        assertEquals(ExecutionStatus.SKIPPED, saved.captured.status)
        assertTrue(engine.runningFlows.value.isEmpty())
    }

    /**
     * A tag that wakes a closed app finds no trigger subscribed. It used to be held for "the first
     * subscriber", which with two NFC flows was often the other flow's trigger — the tag was
     * filtered out there and never reached its own flow.
     */
    @Test
    fun `a tag nobody was listening for runs every flow bound to it, and only those`() = runTest {
        val a = flowWith("A", TriggerType.NFC_TAG, mapOf("tag_id" to "AAAA"))
        val b = flowWith("B", TriggerType.NFC_TAG, mapOf("tag_id" to "bbbb"))
        val any = flowWith("Any", TriggerType.NFC_TAG)
        val wifi = flowWith("W", TriggerType.WIFI)
        val repository = mockk<FlowRepository>(relaxed = true)
        every { repository.observeEnabled() } returns MutableStateFlow(listOf(a, b, any, wifi))
        listOf(a, b, any, wifi).forEach { f -> coEvery { repository.getById(f.id) } returns f }
        val engine = engine(repository, emptySet())

        engine.runForNfcTag("BBBB")

        coVerify(exactly = 1) { repository.getById("B") }
        coVerify(exactly = 1) { repository.getById("Any") }
        coVerify(exactly = 0) { repository.getById("A") }
        coVerify(exactly = 0) { repository.getById("W") }
    }

    /**
     * A "once" trigger without its moment (an older version, an import, the AI) is given one when
     * its flow is first seen enabled, and only its triggers are written — never the whole flow
     * from a snapshot, which could undo the user switching it off at that same moment.
     */
    @Test
    fun `an enabled once trigger without a moment is stamped, triggers only`() = runTest {
        val bare = flowWith("once", TriggerType.TIME, mapOf("time" to "08:00", "repeat" to "ONCE"))
        val daily = flowWith("daily", TriggerType.TIME, mapOf("time" to "08:00", "repeat" to "DAILY"))
        val repository = mockk<FlowRepository>(relaxed = true)
        every { repository.observeEnabled() } returns MutableStateFlow(listOf(bare, daily))
        val written = slot<List<Trigger>>()
        coEvery { repository.updateTriggers("once", capture(written)) } returns Unit
        val engine = engine(repository, emptySet())

        engine.start(this)
        advanceUntilIdle()
        engine.stop()

        assertTrue(com.nexflow.trigger.OnceTrigger.at(written.captured.single().config) != null)
        coVerify(exactly = 0) { repository.updateTriggers("daily", any()) }
        coVerify(exactly = 0) { repository.save(any()) }
    }
}
