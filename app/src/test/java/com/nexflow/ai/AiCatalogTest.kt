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

import android.content.Context
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.info
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * The system instruction is generated from the same catalogs the editor uses, so every
 * type the user can pick manually must also be described to Gemini.
 */
class AiCatalogTest {

    private val context = mockk<Context> {
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }

    @TestFactory
    fun `system instruction lists every trigger and action type`(): List<DynamicTest> {
        val instruction = AiCatalog.systemInstruction(context)
        val allTypes = TriggerType.entries.map { it.name } + ActionType.entries.map { it.name }
        return allTypes.map { name ->
            DynamicTest.dynamicTest(name) {
                assertTrue("- $name:" in instruction, "$name missing from system instruction")
            }
        }
    }

    @Test
    fun `dropdown options and slider ranges appear in the instruction`() {
        val instruction = AiCatalog.systemInstruction(context)
        // WIFI_TOGGLE state dropdown
        assertTrue("state:enum[ON|OFF|TOGGLE]" in instruction)
        // DELAY unit slider with per-unit ranges
        assertTrue("duration_unit:enum[MS(0..60000)|SEC(0..3600)]" in instruction)
    }

    @TestFactory
    fun `every capturable field has a spec so Gemini knows its key`(): List<DynamicTest> {
        val allInfos =
            TriggerType.entries.map { it.name to it.info(context).fields } +
                ActionType.entries.map { it.name to it.info(context).fields }
        return allInfos.map { (name, fields) ->
            DynamicTest.dynamicTest(name) {
                val specKeys = fields.mapNotNull { AiCatalog.fieldSpec(it) }
                AiCatalog.capturableKeys(fields).forEach { key ->
                    assertTrue(
                        specKeys.any { key in it },
                        "$name: capturable key \"$key\" has no field spec",
                    )
                }
            }
        }
    }

    @Test
    fun `capturableKeys includes both keys of a UnitSlider and skips display-only fields`() {
        val fields = ActionType.DELAY.info(context).fields
        assertEquals(setOf("duration_value", "duration_unit"), AiCatalog.capturableKeys(fields))

        val displayOnly = listOf(
            ConfigField.CurrentLocationButton(latKey = "lat", lngKey = "lng"),
            ConfigField.InfoText("note", "x", body = "x"),
        )
        assertTrue(AiCatalog.capturableKeys(displayOnly).isEmpty())
    }

    @Test
    fun `allowedValues resolves dropdowns, toggles and unit sliders`() {
        val wifi = ActionType.WIFI_TOGGLE.info(context).fields
        assertEquals(setOf("ON", "OFF", "TOGGLE"), AiCatalog.allowedValues(wifi, "state"))

        val delay = ActionType.DELAY.info(context).fields
        assertEquals(setOf("MS", "SEC"), AiCatalog.allowedValues(delay, "duration_unit"))
        assertNull(AiCatalog.allowedValues(delay, "duration_value"))

        assertNull(AiCatalog.allowedValues(ActionType.TOAST.info(context).fields, "message"))
    }

    @Test
    fun `numericRange resolves sliders and ignores free-form fields`() {
        val slider = ConfigField.Slider("level", "x", min = 0, max = 100)
        assertEquals(0..100, AiCatalog.numericRange(listOf(slider), "level"))
        assertNull(AiCatalog.numericRange(ActionType.TOAST.info(context).fields, "message"))
    }
}
