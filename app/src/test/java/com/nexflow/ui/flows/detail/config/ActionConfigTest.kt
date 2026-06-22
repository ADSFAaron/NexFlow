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
package com.nexflow.ui.flows.detail.config

import android.content.Context
import com.nexflow.core.automation.model.ActionType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Covers every ActionType selectable in the Flows UI: each must expose valid
 * picker metadata, a usable summary, and config keys that match what the
 * FlowInterpreter / ActionExecutors read at runtime.
 */
class ActionConfigTest {

    // Catalog strings now come from resources; a mocked Context returns a
    // non-blank placeholder so structural assertions still hold without Robolectric.
    private val context = mockk<Context> {
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }

    @TestFactory
    fun `every action type has valid picker metadata`(): List<DynamicTest> =
        ActionType.entries.map { type ->
            DynamicTest.dynamicTest(type.name) {
                val info = type.info(context)
                assertTrue(info.label.isNotBlank(), "$type label must not be blank")
                assertTrue(info.description.isNotBlank(), "$type description must not be blank")

                val inputKeys = info.fields.filter { it.capturesInput() }.map { it.key }
                assertEquals(
                    inputKeys.size, inputKeys.distinct().size,
                    "$type has duplicate field keys: $inputKeys",
                )
                info.fields.forEach { field ->
                    assertTrue(field.key.isNotBlank(), "$type has a field with blank key")
                    assertTrue(field.label.isNotBlank(), "$type field ${field.key} has blank label")
                }
            }
        }

    @TestFactory
    fun `summary handles empty and filled configs`(): List<DynamicTest> =
        ActionType.entries.map { type ->
            DynamicTest.dynamicTest(type.name) {
                assertTrue(
                    type.configSummary(context, emptyMap()).isNotBlank(),
                    "$type summary for empty config must not be blank",
                )
                val filled = sampleConfig(type.info(context).fields)
                assertTrue(
                    type.configSummary(context, filled).isNotBlank(),
                    "$type summary for filled config must not be blank",
                )
            }
        }

    // ----- Contracts between the config UI and the FlowInterpreter -----

    @Test
    fun `if block exposes the expression key read by the interpreter`() {
        val keys = ActionType.IF_BLOCK.info(context).fields.map { it.key }
        assertTrue("expression" in keys, "IF_BLOCK must expose an 'expression' field")
    }

    @Test
    fun `repeat block exposes the count key read by the interpreter`() {
        val keys = ActionType.REPEAT_BLOCK.info(context).fields.map { it.key }
        assertTrue("count" in keys, "REPEAT_BLOCK must expose a 'count' field")
    }

    @Test
    fun `set variable exposes the keys read by the interpreter`() {
        val keys = ActionType.SET_VARIABLE.info(context).fields.map { it.key }
        assertTrue("variable_name" in keys, "SET_VARIABLE must expose 'variable_name'")
        assertTrue("value" in keys, "SET_VARIABLE must expose 'value'")
    }

    @Test
    fun `block markers need no configuration`() {
        listOf(ActionType.ELSE_BLOCK, ActionType.END_IF, ActionType.END_REPEAT).forEach { type ->
            assertTrue(
                type.info(context).fields.isEmpty(),
                "$type is a block marker and must not require configuration",
            )
        }
    }
}
