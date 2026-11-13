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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A flow can arrive as a shared file, so its author may not be the person running it. These pin
 * the interpreter-level guards that keep such a flow from choosing what to launch from outside
 * data, or from running up a bill.
 */
class FlowInterpreterPolicyTest {

    /** Records the interpolated config of every action of [supportedType] it is asked to run. */
    private class Recorder(override val supportedType: ActionType) : ActionExecutor {
        val configs = mutableListOf<Map<String, String>>()
        override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
            configs += action.config
            return ActionResult.Success
        }
    }

    private fun flow(vararg actions: Action) = Flow(
        id = "f1", schemaVersion = 1, name = "test", description = "", author = null,
        tags = emptyList(), enabled = true, createdAt = 0L, updatedAt = 0L,
        triggers = emptyList(), triggerLogic = TriggerLogic.ANY, conditions = emptyList(),
        actions = actions.toList(), variables = emptyList(),
    )

    private var order = 0
    private fun action(type: ActionType, vararg config: Pair<String, String>) =
        Action(id = "a${order}", type = type, config = config.toMap(), order = order++, enabled = true)

    private fun interpreter(vararg executors: ActionExecutor, limiter: ActionRateLimiter = ActionRateLimiter.UNLIMITED) =
        FlowInterpreter(executors.associateBy { it.supportedType }, limiter)

    // ----- literal-only keys -----

    @Test
    fun `a shortcut intent built from a variable is refused`() = runTest {
        val shortcut = Recorder(ActionType.LAUNCH_SHORTCUT)
        val result = interpreter(shortcut).execute(
            flow(action(ActionType.LAUNCH_SHORTCUT, "intent_uri" to "{{trigger.body}}")),
            triggerVariables = mapOf("body" to "intent:#Intent;action=android.intent.action.CALL;end"),
        )
        assertTrue(result is InterpreterResult.Failure, "expected failure, got $result")
        assertTrue(shortcut.configs.isEmpty(), "the executor must never see the steered intent")
    }

    @Test
    fun `even a global variable may not supply a notification's shortcut intent`() = runTest {
        // Any variable, not only trigger data: a global written by an earlier run hides its origin.
        val notification = Recorder(ActionType.NOTIFICATION)
        val result = interpreter(notification).execute(
            flow(action(ActionType.NOTIFICATION, "tap_action" to "OPEN_SHORTCUT", "tap_shortcut_uri" to "{{g:uri}}")),
            globalVariables = mapOf("uri" to "intent:#Intent;end"),
        )
        assertTrue(result is InterpreterResult.Failure)
        assertTrue(notification.configs.isEmpty())
    }

    @Test
    fun `a literal shortcut intent still runs`() = runTest {
        val shortcut = Recorder(ActionType.LAUNCH_SHORTCUT)
        val result = interpreter(shortcut).execute(
            flow(action(ActionType.LAUNCH_SHORTCUT, "intent_uri" to "intent:#Intent;component=a/.B;end")),
        )
        assertEquals(InterpreterResult.Success, result)
        assertEquals(1, shortcut.configs.size)
    }

    // ----- everything else may use trigger data -----

    @Test
    fun `the app that posted a notification may be opened from trigger data`() = runTest {
        // OPEN_APP only reaches an app's launcher screen, so this stays allowed.
        val openApp = Recorder(ActionType.OPEN_APP)
        val result = interpreter(openApp).execute(
            flow(action(ActionType.OPEN_APP, "package_name" to "{{trigger.package}}")),
            triggerVariables = mapOf("package" to "com.example.chat"),
        )
        assertEquals(InterpreterResult.Success, result)
        assertEquals("com.example.chat", openApp.configs.single()["package_name"])
    }

    @Test
    fun `trigger data may still flow into ordinary keys`() = runTest {
        // Forwarding a message, or replying to its sender, is what these flows are for.
        val sms = Recorder(ActionType.SEND_SMS)
        val result = interpreter(sms).execute(
            flow(action(ActionType.SEND_SMS, "number" to "{{trigger.sender}}", "message" to "Got: {{trigger.body}}")),
            triggerVariables = mapOf("sender" to "+15550100", "body" to "hi"),
        )
        assertEquals(InterpreterResult.Success, result)
        assertEquals("+15550100", sms.configs.single()["number"])
    }

    // ----- rate limiting -----

    @Test
    fun `actions over the limit are skipped while the rest of the flow runs`() = runTest {
        val sms = Recorder(ActionType.SEND_SMS)
        val toast = Recorder(ActionType.TOAST)
        val steps = mutableListOf<StepReport>()
        val result = interpreter(sms, toast, limiter = SlidingWindowRateLimiter(nowMillis = { 0L })).execute(
            flow(
                action(ActionType.REPEAT_BLOCK, "count" to "8"),
                action(ActionType.SEND_SMS, "number" to "+15550100", "message" to "x"),
                action(ActionType.END_REPEAT),
                action(ActionType.TOAST, "message" to "done"),
            ),
            onStep = { steps += it },
        )
        assertEquals(InterpreterResult.Success, result)
        assertEquals(RateLimitCategory.SMS.perMinute, sms.configs.size)
        assertEquals(1, toast.configs.size, "a limited step must not abort the run")
        val limited = steps.filter { it.status == ExecutionStatus.SKIPPED && it.actionType == ActionType.SEND_SMS }
        assertEquals(8 - RateLimitCategory.SMS.perMinute, limited.size)
        assertEquals("${FlowInterpreter.NOTE_RATE_LIMITED}5", limited.first().note)
    }

    @Test
    fun `the window reopens after a minute and is kept per flow`() {
        var now = 0L
        val limited = mutableListOf<RateLimitCategory>()
        val limiter = SlidingWindowRateLimiter(nowMillis = { now }, onLimited = { _, c -> limited += c })
        val call = action(ActionType.CALL_PHONE)
        val a = flow(call)
        val b = flow(call).copy(id = "f2")

        repeat(5) { assertTrue(limiter.tryAcquire(a, call, RateLimitCategory.CALL)) }
        assertEquals(false, limiter.tryAcquire(a, call, RateLimitCategory.CALL))
        assertTrue(limiter.tryAcquire(b, call, RateLimitCategory.CALL), "another flow has its own budget")
        assertEquals(listOf(RateLimitCategory.CALL), limited)

        now = 60_000L
        assertTrue(limiter.tryAcquire(a, call, RateLimitCategory.CALL))
    }

    @Test
    fun `a direct-dial shortcut counts as a call`() {
        val dial = action(ActionType.LAUNCH_SHORTCUT, "intent_uri" to "tel:123#Intent;action=android.intent.action.CALL;end")
        val view = action(ActionType.LAUNCH_SHORTCUT, "intent_uri" to "https://example.com#Intent;action=android.intent.action.VIEW;end")
        assertEquals(RateLimitCategory.CALL, RateLimitCategory.of(dial))
        assertEquals(null, RateLimitCategory.of(view))
    }
}
