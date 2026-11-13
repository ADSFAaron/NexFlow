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
package com.nexflow.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.TriggerJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Conditions were the one kind of step nothing checked; the AI path covers triggers and actions. */
@RunWith(AndroidJUnit4::class)
class FlowConfigValidatorTest {

    private val validator = FlowConfigValidator(ApplicationProvider.getApplicationContext<Context>())

    private fun config(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    private fun flow(vararg conditions: ConditionJson) = FlowJson(
        schemaVersion = 1, id = "f", name = "f", description = "", tags = emptyList(), enabled = false,
        createdAt = "", updatedAt = "",
        triggers = listOf(TriggerJson("t", "MANUAL", config())),
        triggerLogic = "ANY", conditions = conditions.toList(),
        actions = listOf(ActionJson("a", "TOAST", config("message" to "hi"), order = 0, enabled = true)),
        variables = emptyList(),
    )

    private fun battery(vararg pairs: Pair<String, String>) = ConditionJson("c", "BATTERY_LEVEL", config(*pairs), negate = false)

    @Test
    fun `a condition set the way its editor would is fine`() {
        assertEquals(emptyList<String>(), validator.validate(flow(battery("direction" to "BELOW", "level" to "20"))))
    }

    @Test
    fun `a condition value outside its dropdown or range is caught`() {
        val errors = validator.validate(flow(battery("direction" to "SIDEWAYS", "level" to "150")))
        assertEquals(2, errors.size)
        assertTrue(errors[0], errors[0].startsWith("conditions[0].config.direction: invalid value \"SIDEWAYS\""))
        assertTrue(errors[1], errors[1].startsWith("conditions[0].config.level: must be an integer in 0..100"))
    }

    @Test
    fun `a condition key its editor never writes is caught`() {
        val errors = validator.validate(flow(battery("direction" to "BELOW", "volts" to "4")))
        assertTrue("$errors", errors.single().startsWith("conditions[0].config: unknown key \"volts\" for BATTERY_LEVEL"))
    }

    @Test
    fun `an unknown condition type is an error, not a warning`() {
        val errors = validator.validate(flow(ConditionJson("c", "MOON_PHASE", config(), negate = false)))
        assertEquals(listOf("conditions[0].type: unknown condition type \"MOON_PHASE\""), errors)
    }

    @Test
    fun `triggers and actions go through the same checks`() {
        val f = flow().copy(
            triggers = listOf(TriggerJson("t", "FUTURE", config())),
            actions = listOf(ActionJson("a", "TOAST", config("colour" to "red"), order = 0, enabled = true)),
        )
        val errors = validator.validate(f)
        assertEquals(2, errors.size)
        assertTrue(errors[0], errors[0].startsWith("triggers[0].type:"))
        assertTrue(errors[1], errors[1].startsWith("actions[0].config: unknown key \"colour\""))
    }
}
