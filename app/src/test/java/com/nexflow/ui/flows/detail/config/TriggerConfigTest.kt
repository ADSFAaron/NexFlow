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
import com.nexflow.core.automation.model.TriggerType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Covers every TriggerType selectable in the Flows UI: each must expose valid
 * picker metadata and produce a usable summary for empty and filled configs.
 */
class TriggerConfigTest {

    // Catalog strings now come from resources; a mocked Context returns a
    // non-blank placeholder so structural assertions still hold without Robolectric.
    private val context = mockk<Context> {
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }

    @TestFactory
    fun `every trigger type has valid picker metadata`(): List<DynamicTest> =
        TriggerType.entries.map { type ->
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
        TriggerType.entries.map { type ->
            DynamicTest.dynamicTest(type.name) {
                // Unconfigured triggers must still render a placeholder summary
                assertTrue(
                    type.configSummary(context, emptyMap()).isNotBlank(),
                    "$type summary for empty config must not be blank",
                )
                // A fully filled config must produce a non-blank summary too
                val filled = sampleConfig(type.info(context).fields)
                assertTrue(
                    type.configSummary(context, filled).isNotBlank(),
                    "$type summary for filled config must not be blank",
                )
            }
        }

    @Test
    fun `time trigger fields match TimeTriggerHandler contract`() {
        val keys = TriggerType.TIME.info(context).fields.map { it.key }
        // TimeTriggerHandler reads "time", "repeat" and (for CUSTOM) "days"
        assertTrue("time" in keys)
        assertTrue("repeat" in keys)
        assertTrue("days" in keys)
    }

    @Test
    fun `geofence trigger exposes coordinates radius and event`() {
        val keys = TriggerType.GEOFENCE.info(context).fields.flatMap {
            if (it is ConfigField.CurrentLocationButton) listOf(it.latKey, it.lngKey) else listOf(it.key)
        }
        listOf("lat", "lng", "radius_m", "event").forEach { expected ->
            assertTrue(expected in keys, "GEOFENCE is missing field '$expected'")
        }
    }
}
