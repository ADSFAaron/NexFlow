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
package com.nexflow.ai

import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowContextFormatterTest {

    private fun flow(
        triggers: List<Trigger> = emptyList(),
        actions: List<Action> = emptyList(),
    ) = Flow(
        id = "flow-1",
        schemaVersion = 1,
        name = "早安模式",
        description = "起床時播放音樂",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = triggers,
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = actions,
        variables = emptyList(),
    )

    @Test
    fun `emits name description and trigger logic`() {
        val text = FlowContextFormatter.format(flow())
        assertTrue(text.contains("name: 早安模式"), text)
        assertTrue(text.contains("description: 起床時播放音樂"), text)
        assertTrue(text.contains("trigger_logic: ANY"), text)
    }

    @Test
    fun `config travels as a JSON object string, matching create_flow`() {
        val text = FlowContextFormatter.format(
            flow(
                triggers = listOf(Trigger("t1", TriggerType.TIME, mapOf("time" to "07:00"))),
                actions = listOf(
                    Action("a1", ActionType.TOAST, mapOf("message" to "早安"), order = 0, enabled = true),
                ),
            ),
        )
        assertTrue(text.contains("""- TIME config: {"time":"07:00"}"""), text)
        assertTrue(text.contains("""1. TOAST config: {"message":"早安"}"""), text)
    }

    @Test
    fun `disabled actions are flagged and execution order is honoured`() {
        val text = FlowContextFormatter.format(
            flow(
                actions = listOf(
                    Action("a2", ActionType.TOAST, emptyMap(), order = 1, enabled = false),
                    Action("a1", ActionType.DELAY, emptyMap(), order = 0, enabled = true),
                ),
            ),
        )
        val delayIndex = text.indexOf("1. DELAY")
        val toastIndex = text.indexOf("2. TOAST (disabled)")
        assertTrue(delayIndex in 0 until toastIndex, text)
    }

    @Test
    fun `an empty flow says so instead of leaving the section blank`() {
        val text = FlowContextFormatter.format(flow())
        assertTrue(text.contains("(none — this flow only runs manually)"), text)
        assertTrue(text.contains("actions:\n  (none)"), text)
    }
}
