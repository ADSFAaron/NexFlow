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
import com.nexflow.core.automation.model.ConditionType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Covers every ConditionType selectable in the Flows UI: each must expose valid picker
 * metadata, offer the negate toggle, and write the config keys its evaluator reads.
 */
class ConditionConfigTest {

    private val context = mockk<Context> {
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }

    @TestFactory
    fun `every condition type has valid picker metadata`(): List<DynamicTest> =
        ConditionType.entries.map { type ->
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
    fun `every condition type offers the negate toggle`(): List<DynamicTest> =
        ConditionType.entries.map { type ->
            DynamicTest.dynamicTest(type.name) {
                // Without it, `negate` would be unreachable from the editor even though the
                // schema, the gate and the importer all honour it.
                assertTrue(
                    type.info(context).fields.any { it.key == NEGATE_KEY },
                    "$type is missing the $NEGATE_KEY toggle",
                )
            }
        }

    @TestFactory
    fun `summary handles empty and filled configs`(): List<DynamicTest> =
        ConditionType.entries.map { type ->
            DynamicTest.dynamicTest(type.name) {
                assertTrue(
                    type.configSummary(context, emptyMap()).isNotBlank(),
                    "$type summary for empty config must not be blank",
                )
                val filled = sampleConfig(type.info(context).fields)
                assertTrue(
                    type.configSummary(context, filled, negate = true).isNotBlank(),
                    "$type summary for filled config must not be blank",
                )
            }
        }

    @Test
    fun `condition fields match what each evaluator reads`() {
        fun keys(type: ConditionType) = type.info(context).fields.map { it.key }

        assertTrue(keys(ConditionType.TIME_RANGE).containsAll(listOf("start", "end")))
        assertTrue("days" in keys(ConditionType.DAY_OF_WEEK))
        assertTrue(keys(ConditionType.BATTERY_LEVEL).containsAll(listOf("direction", "level")))
        assertTrue("state" in keys(ConditionType.CHARGING))
        assertTrue(keys(ConditionType.WIFI_CONNECTED).containsAll(listOf("state", "ssid")))
        assertTrue(keys(ConditionType.BLUETOOTH_CONNECTED).containsAll(listOf("state", "device_name")))
        assertTrue("state" in keys(ConditionType.SCREEN_STATE))
        assertTrue("expression" in keys(ConditionType.EXPRESSION))
    }

    @Test
    fun `type ids survive a round trip through the schema string`() {
        // Condition.type is stored as a plain string; a rename that broke this would turn every
        // saved condition into an unsupported one and stop those flows dead.
        ConditionType.entries.forEach { type ->
            assertEquals(type, ConditionType.fromId(type.name))
        }
        assertEquals(ConditionType.TIME_RANGE, ConditionType.fromId(" time_range "))
        assertEquals(null, ConditionType.fromId("NOT_A_CONDITION"))
    }
}
