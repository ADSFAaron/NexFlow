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
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowInterpreterTest {

    /** Records every TOAST execution's interpolated "message" config. */
    private class RecordingExecutor : ActionExecutor {
        override val supportedType = ActionType.TOAST
        val messages = mutableListOf<String>()
        override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
            messages += action.config["message"] ?: ""
            return ActionResult.Success
        }
    }

    private fun interpreter(executor: RecordingExecutor = RecordingExecutor()) =
        FlowInterpreter(mapOf(ActionType.TOAST to executor))

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

    private fun action(type: ActionType, order: Int, config: Map<String, String> = emptyMap()) =
        Action(id = "a$order", type = type, config = config, order = order, enabled = true)

    // ----- expression evaluator -----

    @Test
    fun `evaluates literals and comparisons`() {
        val i = interpreter()
        val vars = mapOf("battery" to "15", "status" to "OK")

        assertTrue(i.evaluateExpression("true", vars))
        assertTrue(i.evaluateExpression("TRUE", vars))
        assertFalse(i.evaluateExpression("false", vars))
        assertFalse(i.evaluateExpression("", vars))

        assertTrue(i.evaluateExpression("{{battery}} < 20", vars))
        assertFalse(i.evaluateExpression("{{battery}} > 20", vars))
        assertTrue(i.evaluateExpression("{{battery}} <= 15", vars))
        assertTrue(i.evaluateExpression("{{battery}} >= 15", vars))
        assertTrue(i.evaluateExpression("{{battery}} == 15", vars))
        assertTrue(i.evaluateExpression("{{battery}} != 20", vars))

        // string comparison is case-insensitive
        assertTrue(i.evaluateExpression("{{status}} == ok", vars))
        assertTrue(i.evaluateExpression("{{status}} != fail", vars))
        // numeric, not lexicographic: "9" < "10"
        assertTrue(i.evaluateExpression("9 < 10", vars))
    }

    @Test
    fun `if block runs only matching branch`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.IF_BLOCK, 0, mapOf("expression" to "{{battery}} < 20")),
                    action(ActionType.TOAST, 1, mapOf("message" to "low")),
                    action(ActionType.ELSE_BLOCK, 2),
                    action(ActionType.TOAST, 3, mapOf("message" to "high")),
                    action(ActionType.END_IF, 4),
                ),
                variables = listOf(Variable("battery", VariableType.INTEGER, "15")),
            ),
        )
        assertTrue(result is InterpreterResult.Success)
        assertEquals(listOf("low"), executor.messages)
    }

    // ----- SET_VARIABLE config keys -----

    @Test
    fun `set variable accepts variable_name key used by the UI`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.SET_VARIABLE, 0, mapOf("variable_name" to "greeting", "value" to "hello")),
                    action(ActionType.TOAST, 1, mapOf("message" to "{{greeting}} world")),
                ),
            ),
        )
        assertTrue(result is InterpreterResult.Success)
        assertEquals(listOf("hello world"), executor.messages)
    }

    @Test
    fun `set variable still accepts legacy name key`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.SET_VARIABLE, 0, mapOf("name" to "greeting", "value" to "hi")),
                    action(ActionType.TOAST, 1, mapOf("message" to "{{greeting}}")),
                ),
            ),
        )
        assertTrue(result is InterpreterResult.Success)
        assertEquals(listOf("hi"), executor.messages)
    }

    @Test
    fun `repeat block executes body count times`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.REPEAT_BLOCK, 0, mapOf("count" to "3")),
                    action(ActionType.TOAST, 1, mapOf("message" to "tick")),
                    action(ActionType.END_REPEAT, 2),
                ),
            ),
        )
        assertTrue(result is InterpreterResult.Success)
        assertEquals(listOf("tick", "tick", "tick"), executor.messages)
    }

    // ----- global variables -----

    @Test
    fun `global variable is readable via g-prefixed reference`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(action(ActionType.TOAST, 0, mapOf("message" to "count is {{g:counter}}"))),
            ),
            globalVariables = mapOf("counter" to "7"),
        )
        assertTrue(result is InterpreterResult.Success)
        assertEquals(listOf("count is 7"), executor.messages)
    }

    @Test
    fun `setting a g-prefixed variable reports the persisted global`() = runTest {
        val persisted = mutableMapOf<String, String>()
        val result = interpreter().execute(
            flow(
                listOf(
                    action(ActionType.SET_VARIABLE, 0, mapOf("variable_name" to "g:counter", "value" to "{{g:counter}}!")),
                ),
            ),
            globalVariables = mapOf("counter" to "hi"),
            onGlobalVariableSet = { name, value -> persisted[name] = value },
        )
        assertTrue(result is InterpreterResult.Success)
        // Callback receives the un-prefixed name and the interpolated new value.
        assertEquals(mapOf("counter" to "hi!"), persisted)
    }

    @Test
    fun `setting a local variable does not report a global`() = runTest {
        val persisted = mutableMapOf<String, String>()
        interpreter().execute(
            flow(
                listOf(action(ActionType.SET_VARIABLE, 0, mapOf("variable_name" to "local", "value" to "x"))),
            ),
            onGlobalVariableSet = { name, value -> persisted[name] = value },
        )
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun `writing an undeclared global fails the run instead of creating it`() = runTest {
        val executor = RecordingExecutor()
        val persisted = mutableMapOf<String, String>()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.SET_VARIABLE, 0, mapOf("variable_name" to "g:countr", "value" to "1")),
                    action(ActionType.TOAST, 1, mapOf("message" to "after")),
                ),
            ),
            globalVariables = mapOf("counter" to "0"),
            onGlobalVariableSet = { name, value -> persisted[name] = value },
        )
        assertTrue(result is InterpreterResult.Failure)
        assertTrue("g:countr" in (result as InterpreterResult.Failure).message)
        assertTrue(persisted.isEmpty())
        // The run stops at the bad write; nothing after it executes.
        assertTrue(executor.messages.isEmpty())
    }

    @Test
    fun `an undeclared global is never readable in the same run`() = runTest {
        val executor = RecordingExecutor()
        val result = interpreter(executor).execute(
            flow(
                listOf(
                    action(ActionType.SET_VARIABLE, 0, mapOf("variable_name" to "g:typo", "value" to "written")),
                    action(ActionType.TOAST, 1, mapOf("message" to "{{g:typo}}")),
                ),
            ),
        )
        assertTrue(result is InterpreterResult.Failure)
        assertTrue(executor.messages.isEmpty())
    }
}
