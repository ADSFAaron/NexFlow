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
import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.nfc.NfcBackgroundDispatch
import com.nexflow.core.automation.trigger.TriggerVariables
import com.nexflow.trigger.TimeTriggerScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What the engine decides before a single action runs: the flow's conditions must hold, an ALL
 * flow must have all of its triggers, and whatever fired the flow must reach the actions as
 * `{{trigger.x}}`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowEngineConditionTest {

    private class RecordingExecutor : ActionExecutor {
        override val supportedType = ActionType.TOAST
        val messages = mutableListOf<String>()
        override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
            messages += action.config["message"] ?: ""
            return ActionResult.Success
        }
    }

    private class FixedEvaluator(
        override val supportedType: ConditionType,
        private val result: Boolean,
    ) : ConditionEvaluator {
        override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>) = result
    }

    @BeforeEach
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun unmockLog() = unmockkStatic(Log::class)

    private fun engineFor(
        flow: Flow,
        repository: FlowRepository,
        executor: ActionExecutor,
        evaluators: Set<ConditionEvaluator>,
    ): FlowEngine {
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
            conditionEvaluatorSet = evaluators,
            timeTriggerScheduler = mockk<TimeTriggerScheduler>(relaxed = true),
            nfcBackgroundDispatch = mockk<NfcBackgroundDispatch>(relaxed = true),
            context = context,
        )
    }

    @Test
    fun `a flow whose condition does not hold is skipped and logged with the reason`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(conditions = listOf(condition(ConditionType.CHARGING)))
        val engine = engineFor(
            flow, repository, executor,
            setOf(FixedEvaluator(ConditionType.CHARGING, result = false)),
        )
        val logged = slot<ExecutionLog>()
        coEvery { repository.saveExecutionLog(capture(logged), any()) } returns Unit

        engine.runNow(flow.id)

        assertTrue(executor.messages.isEmpty(), "actions must not run when a condition fails")
        assertEquals(ExecutionStatus.SKIPPED, logged.captured.status)
        assertTrue(
            logged.captured.errorMessage?.contains("CHARGING") == true,
            "the log must name the condition, got ${logged.captured.errorMessage}",
        )
    }

    @Test
    fun `a flow whose conditions hold runs normally`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(conditions = listOf(condition(ConditionType.CHARGING)))
        val engine = engineFor(
            flow, repository, executor,
            setOf(FixedEvaluator(ConditionType.CHARGING, result = true)),
        )
        val logged = slot<ExecutionLog>()
        coEvery { repository.saveExecutionLog(capture(logged), any()) } returns Unit

        engine.runNow(flow.id)

        assertEquals(listOf("hi"), executor.messages)
        assertEquals(ExecutionStatus.SUCCESS, logged.captured.status)
    }

    @Test
    fun `a successful run is saved with the steps it was made of`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow()
        val engine = engineFor(flow, repository, executor, emptySet())
        val logged = slot<ExecutionLog>()
        val steps = slot<List<ExecutionStep>>()
        coEvery { repository.saveExecutionLog(capture(logged), capture(steps)) } returns Unit

        engine.runNow(flow.id)

        // The steps are the whole point of the run detail screen, and they are only ever written
        // here — a summary saved without them renders as a run that executed nothing.
        assertEquals(1, steps.captured.size)
        val step = steps.captured.single()
        assertEquals("a0", step.actionId)
        assertEquals(ExecutionStatus.SUCCESS, step.status)
        assertEquals(logged.captured.id, step.logId, "steps must be filed under the run that produced them")
        // Detailed logging is off unless the user turns it on, and the mocked prefs say off.
        assertNull(step.resolvedConfig)
    }

    @Test
    fun `a skipped run records no steps`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(conditions = listOf(condition(ConditionType.CHARGING)))
        val engine = engineFor(
            flow, repository, executor,
            setOf(FixedEvaluator(ConditionType.CHARGING, result = false)),
        )
        val steps = slot<List<ExecutionStep>>()
        coEvery { repository.saveExecutionLog(any(), capture(steps)) } returns Unit

        engine.runNow(flow.id)

        // Nothing ran, so there is nothing to show step by step — the gate's reason on the
        // summary is the whole story.
        assertTrue(steps.captured.isEmpty())
    }

    @Test
    fun `an unknown condition type stops the run rather than being ignored`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(
            conditions = listOf(
                Condition(id = "c1", type = "SOMETHING_ELSE", config = emptyMap(), negate = false),
            ),
        )
        val engine = engineFor(flow, repository, executor, emptySet())
        val logged = slot<ExecutionLog>()
        coEvery { repository.saveExecutionLog(capture(logged), any()) } returns Unit

        engine.runNow(flow.id)

        assertTrue(executor.messages.isEmpty())
        assertEquals(ExecutionStatus.SKIPPED, logged.captured.status)
    }

    @Test
    fun `trigger values supplied by the caller reach the actions`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(message = "from {{trigger.package}} at {{trigger.type}}")
        val engine = engineFor(flow, repository, executor, emptySet())

        engine.runNow(
            flow.id,
            triggerVariables = mapOf(
                TriggerVariables.PACKAGE to "com.chat",
                TriggerVariables.TYPE to "NOTIFICATION_RECEIVED",
            ),
        )

        assertEquals(listOf("from com.chat at NOTIFICATION_RECEIVED"), executor.messages)
    }

    @Test
    fun `a manual run still reports a type and a timestamp`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(message = "{{trigger.type}}|{{trigger.timestamp}}")
        val engine = engineFor(flow, repository, executor, emptySet())

        engine.runNow(flow.id)

        val (type, timestamp) = executor.messages.single().split("|")
        assertEquals("MANUAL", type)
        assertTrue(timestamp.toLongOrNull() != null, "timestamp must be epoch millis, got '$timestamp'")
    }

    @Test
    fun `an ALL flow runs only once all of its triggers have fired`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(
            triggers = listOf(trigger("t1", TriggerType.WIFI), trigger("t2", TriggerType.HEADSET_PLUG)),
            logic = TriggerLogic.ALL,
        )
        val engine = engineFor(flow, repository, executor, emptySet())

        assertEquals(null, engine.runNow(flow.id, triggerId = "t1"))
        assertTrue(executor.messages.isEmpty(), "one trigger is not all of them")

        engine.runNow(flow.id, triggerId = "t2")
        assertEquals(listOf("hi"), executor.messages)
    }

    @Test
    fun `a manual run is never held back by ALL`() = runTest {
        // Pressing Run means run — it must not be swallowed as one third of a combination.
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(
            triggers = listOf(trigger("t1", TriggerType.WIFI), trigger("t2", TriggerType.HEADSET_PLUG)),
            logic = TriggerLogic.ALL,
        )
        val engine = engineFor(flow, repository, executor, emptySet())

        engine.runNow(flow.id)

        assertEquals(listOf("hi"), executor.messages)
    }

    @Test
    fun `an ANY flow runs on the first trigger`() = runTest {
        val executor = RecordingExecutor()
        val repository = mockk<FlowRepository>(relaxed = true)
        val flow = testFlow(
            triggers = listOf(trigger("t1", TriggerType.WIFI), trigger("t2", TriggerType.HEADSET_PLUG)),
            logic = TriggerLogic.ANY,
        )
        val engine = engineFor(flow, repository, executor, emptySet())

        engine.runNow(flow.id, triggerId = "t1")

        assertEquals(listOf("hi"), executor.messages)
    }

    private fun condition(type: ConditionType) =
        Condition(id = "c-${type.name}", type = type.name, config = emptyMap(), negate = false)

    private fun trigger(id: String, type: TriggerType) =
        Trigger(id = id, type = type, config = emptyMap())

    private fun testFlow(
        conditions: List<Condition> = emptyList(),
        triggers: List<Trigger> = emptyList(),
        logic: TriggerLogic = TriggerLogic.ANY,
        message: String = "hi",
    ) = Flow(
        id = "f1",
        schemaVersion = 1,
        name = "test",
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = triggers,
        triggerLogic = logic,
        conditions = conditions,
        actions = listOf(
            Action(id = "a0", type = ActionType.TOAST, config = mapOf("message" to message), order = 0, enabled = true),
        ),
        variables = emptyList(),
    )
}
