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
package com.nexflow.core.flowschema

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Import refuses a file only on [ValidationError.Severity.ERROR], so the split between errors and
 * warnings decides what a user can import. Anything NexFlow's own editor can produce must stay a
 * warning, or a flow exported from the app would not import back.
 */
class FlowSchemaValidatorTest {

    private var n = 0
    private fun uuid() = "00000000-0000-4000-8000-%012d".format(n++)

    private fun valid() = FlowJson(
        schemaVersion = 1, id = uuid(), name = "Morning", description = "", tags = emptyList(),
        enabled = false, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
        triggers = listOf(TriggerJson(uuid(), "TIME", JsonObject(emptyMap()))),
        triggerLogic = "ANY", conditions = emptyList(),
        actions = listOf(ActionJson(uuid(), "TOAST", JsonObject(emptyMap()), order = 0, enabled = true)),
        variables = emptyList(),
    )

    private fun FlowJson.problems() = FlowSchemaValidator.validate(this)

    @Test
    fun `a well-formed flow has no problems`() {
        assertEquals(emptyList<ValidationError>(), valid().problems())
    }

    @Test
    fun `what the editor can produce is only a warning`() {
        val flow = valid().copy(
            name = "x".repeat(101),
            description = "d".repeat(2001),
            triggers = emptyList(),
            actions = emptyList(),
            variables = listOf(VariableJson("my var", "STRING", JsonPrimitive(""))),
        )
        val problems = flow.problems()
        assertEquals(5, problems.size, "$problems")
        assertTrue(problems.none { it.isError }, "$problems")
    }

    @Test
    fun `what the app cannot store or run is an error`() {
        val ifBlock = ActionJson(uuid(), "IF_BLOCK", JsonObject(emptyMap()), order = 1, enabled = true)
        val cases = mapOf(
            "schema_version" to valid().copy(schemaVersion = 99),
            "id" to valid().copy(id = "not-a-uuid"),
            "name" to valid().copy(name = " "),
            "trigger_logic" to valid().copy(triggerLogic = "SOME"),
            "triggers[0].id" to valid().copy(triggers = listOf(TriggerJson("t1", "TIME", JsonObject(emptyMap())))),
            "actions" to valid().let { it.copy(actions = it.actions + ifBlock) },
            "variables[0].type" to valid().copy(variables = listOf(VariableJson("ok", "LIST", JsonPrimitive("")))),
        )
        cases.forEach { (field, flow) ->
            val errors = flow.problems().filter { it.isError }
            assertTrue(errors.any { it.field == field }, "$field should be an error, got ${flow.problems()}")
        }
    }
}
