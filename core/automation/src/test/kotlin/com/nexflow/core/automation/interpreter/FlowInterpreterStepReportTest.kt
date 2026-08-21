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
package com.nexflow.core.automation.interpreter

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the per-run build log is built from. These assertions are the contract the log screen
 * reads against: an action that ran must produce exactly one report, in execution order, carrying
 * enough to say *which* row failed — the thing a run-level result cannot express.
 */
class FlowInterpreterStepReportTest {

    private class OkExecutor(override val supportedType: ActionType = ActionType.TOAST) : ActionExecutor {
        override suspend fun execute(action: Action, variables: MutableMap<String, String>) =
            ActionResult.Success
    }

    private class FailingExecutor(private val message: String) : ActionExecutor {
        override val supportedType = ActionType.HTTP_REQUEST
        override suspend fun execute(action: Action, variables: MutableMap<String, String>) =
            ActionResult.Failure(message)
    }

    private fun interpreter(vararg extra: ActionExecutor) = FlowInterpreter(
        (listOf(OkExecutor()) + extra).associateBy { it.supportedType },
    )

    private fun flow(actions: List<Action>, variables: List<Variable> = emptyList()) = Flow(
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
        actions = actions,
        variables = variables,
    )

    private fun action(
        type: ActionType,
        order: Int,
        config: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
    ) = Action(id = "a$order", type = type, config = config, order = order, enabled = enabled)

    private suspend fun run(
        actions: List<Action>,
        variables: List<Variable> = emptyList(),
        detailed: Boolean = false,
        vararg extra: ActionExecutor,
    ): Pair<InterpreterResult, List<StepReport>> {
        val steps = mutableListOf<StepReport>()
        val result = interpreter(*extra).execute(
            flow = flow(actions, variables),
            onStep = { steps += it },
            recordResolvedConfig = detailed,
        )
        return result to steps
    }

    @Test
    fun `every executed action is reported once, in order`() = runTest {
        val (_, steps) = run(
            listOf(
                action(ActionType.TOAST, 0),
                action(ActionType.TOAST, 1),
                action(ActionType.TOAST, 2),
            ),
        )

        assertEquals(listOf("a0", "a1", "a2"), steps.map { it.actionId })
        assertTrue(steps.all { it.status == ExecutionStatus.SUCCESS })
    }

    @Test
    fun `end markers are not reported`() = runTest {
        val (_, steps) = run(
            listOf(
                action(ActionType.IF_BLOCK, 0, mapOf("expression" to "true")),
                action(ActionType.TOAST, 1),
                action(ActionType.ELSE_BLOCK, 2),
                action(ActionType.TOAST, 3),
                action(ActionType.END_IF, 4),
            ),
        )

        // The IF is reported (it decided something); ELSE and END_IF did no work.
        assertEquals(listOf(ActionType.IF_BLOCK, ActionType.TOAST), steps.map { it.actionType })
    }

    @Test
    fun `an IF records which way it went and nests what it let through`() = runTest {
        val (_, taken) = run(
            listOf(
                action(ActionType.IF_BLOCK, 0, mapOf("expression" to "1 < 2")),
                action(ActionType.TOAST, 1),
                action(ActionType.END_IF, 2),
            ),
        )
        assertEquals(FlowInterpreter.NOTE_IF_TRUE, taken[0].note)
        assertEquals(0, taken[0].depth)
        assertEquals(1, taken[1].depth, "the branch body must read as nested under its condition")

        val (_, notTaken) = run(
            listOf(
                action(ActionType.IF_BLOCK, 0, mapOf("expression" to "1 > 2")),
                action(ActionType.TOAST, 1),
                action(ActionType.END_IF, 2),
            ),
        )
        assertEquals(FlowInterpreter.NOTE_IF_FALSE, notTaken.single().note)
    }

    @Test
    fun `a disabled action is reported as skipped rather than omitted`() = runTest {
        val (_, steps) = run(
            listOf(
                action(ActionType.TOAST, 0),
                action(ActionType.TOAST, 1, enabled = false),
                action(ActionType.TOAST, 2),
            ),
        )

        assertEquals(3, steps.size)
        assertEquals(ExecutionStatus.SKIPPED, steps[1].status)
        assertEquals(FlowInterpreter.NOTE_DISABLED, steps[1].note)
    }

    @Test
    fun `a repeat reports its rounds and tags each pass`() = runTest {
        val (_, steps) = run(
            listOf(
                action(ActionType.REPEAT_BLOCK, 0, mapOf("count" to "3")),
                action(ActionType.TOAST, 1),
                action(ActionType.END_REPEAT, 2),
            ),
        )

        assertEquals("${FlowInterpreter.NOTE_REPEAT}3", steps[0].note)
        val body = steps.drop(1)
        assertEquals(3, body.size)
        assertEquals(listOf(0, 1, 2), body.map { it.iteration })
        assertTrue(body.all { it.depth == 1 }, "the body is one level deeper than the loop")
    }

    @Test
    fun `a failing action is the last step and names the reason`() = runTest {
        val (result, steps) = run(
            listOf(
                action(ActionType.TOAST, 0),
                action(ActionType.HTTP_REQUEST, 1, mapOf("url" to "https://x")),
                action(ActionType.TOAST, 2),
            ),
            extra = arrayOf(FailingExecutor("main.temp not found")),
        )

        assertTrue(result is InterpreterResult.Failure)
        // The run stops at the failure, so the action after it never reports.
        assertEquals(listOf("a0", "a1"), steps.map { it.actionId })
        assertEquals(ExecutionStatus.FAIL, steps.last().status)
        assertEquals("main.temp not found", steps.last().errorMessage)
    }

    @Test
    fun `a failure inside a branch keeps the depth that locates it`() = runTest {
        val (_, steps) = run(
            listOf(
                action(ActionType.IF_BLOCK, 0, mapOf("expression" to "true")),
                action(ActionType.HTTP_REQUEST, 1),
                action(ActionType.END_IF, 2),
            ),
            extra = arrayOf(FailingExecutor("no url")),
        )

        val failed = steps.last()
        assertEquals(ExecutionStatus.FAIL, failed.status)
        assertEquals(1, failed.depth)
    }

    @Test
    fun `a bad SET_VARIABLE fails its own step, not just the run`() = runTest {
        val (result, steps) = run(
            listOf(
                action(
                    ActionType.SET_VARIABLE, 0,
                    mapOf("variable_name" to "g:nope", "value" to "1"),
                ),
            ),
        )

        assertTrue(result is InterpreterResult.Failure)
        assertEquals(ExecutionStatus.FAIL, steps.single().status)
        assertTrue(steps.single().errorMessage!!.contains("g:nope"))
    }

    @Test
    fun `resolved config is withheld unless detail is asked for`() = runTest {
        val actions = listOf(
            action(ActionType.TOAST, 0, mapOf("message" to "hi {{who}}")),
            action(ActionType.SET_VARIABLE, 1, mapOf("variable_name" to "n", "value" to "2 + 3")),
        )
        val variables = listOf(Variable("who", VariableType.STRING, "world"))

        val (_, plain) = run(actions, variables)
        assertTrue(plain.all { it.resolvedConfig == null }, "nothing from the run may be stored by default")

        val (_, detailed) = run(actions, variables, detailed = true)
        assertEquals("message: hi world", detailed[0].resolvedConfig)
        // The interesting part of an assignment is where it landed, not the expression.
        assertEquals("n = 5", detailed[1].resolvedConfig)
    }

    @Test
    fun `a run without a listener still succeeds`() = runTest {
        // onStep is optional: the engine always passes one, but nothing else should have to.
        val result = interpreter().execute(flow(listOf(action(ActionType.TOAST, 0))))
        assertTrue(result is InterpreterResult.Success)
    }

    @Test
    fun `a menu records the branch the user picked`() = runTest {
        val chooser = object : ActionExecutor {
            override val supportedType = ActionType.SHOW_MENU
            override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
                variables["__menu_choice__"] = "Coffee"
                return ActionResult.Success
            }
        }
        val (_, steps) = run(
            listOf(
                action(ActionType.SHOW_MENU, 0),
                action(ActionType.MENU_CASE, 1, mapOf("option" to "Coffee")),
                action(ActionType.TOAST, 2),
                action(ActionType.END_MENU, 3),
            ),
            extra = arrayOf(chooser),
        )

        // Which option was chosen exists nowhere else: unlike an IF, it is not recomputable
        // from the flow plus the variables.
        assertEquals("${FlowInterpreter.NOTE_MENU}Coffee", steps[0].note)
        assertEquals(ActionType.TOAST, steps[1].actionType)
        assertEquals(1, steps[1].depth)
    }
}
