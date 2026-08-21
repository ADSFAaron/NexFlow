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
package com.nexflow.data

import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.flowschema.GlobalVariableJson
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Shared FlowJson→domain mapper used by both JSON import and the AI assistant. */
class FlowJsonMapperTest {

    private fun flowJson(
        enabled: Boolean = true,
        triggerType: String = "MANUAL",
        actionType: String = "TOAST",
        actionConfig: JsonObject = buildJsonObject { put("message", "hi") },
        triggerLogic: String = "ALL",
        createdAt: String = "2026-01-02T03:04:05Z",
        variables: List<VariableJson> = emptyList(),
    ) = FlowJson(
        schemaVersion = 1,
        id = "0f0e0d0c-0b0a-4908-8706-050403020100",
        name = "Sample",
        description = "desc",
        tags = listOf("tag"),
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = "2026-01-02T03:04:05Z",
        triggers = listOf(TriggerJson("t1", triggerType, buildJsonObject { })),
        triggerLogic = triggerLogic,
        conditions = emptyList(),
        actions = listOf(ActionJson("a1", actionType, actionConfig, order = 0, enabled = true)),
        variables = variables,
    )

    @Test
    fun `forceDisabled is the default and overrides the json flag`() {
        assertFalse(flowJson(enabled = true).toDomain().enabled)
        assertTrue(flowJson(enabled = true).toDomain(forceDisabled = false).enabled)
        assertFalse(flowJson(enabled = false).toDomain(forceDisabled = false).enabled)
    }

    @Test
    fun `known types, logic and timestamps map through`() {
        val domain = flowJson().toDomain()
        assertEquals("0f0e0d0c-0b0a-4908-8706-050403020100", domain.id)
        assertEquals(TriggerType.MANUAL, domain.triggers.single().type)
        assertEquals(ActionType.TOAST, domain.actions.single().type)
        assertEquals(TriggerLogic.ALL, domain.triggerLogic)
        assertEquals(
            java.time.Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(),
            domain.createdAt,
        )
        assertEquals("hi", domain.actions.single().config["message"])
    }

    @Test
    fun `unknown types fall back leniently for import compatibility`() {
        val domain = flowJson(triggerType = "FUTURE_TRIGGER", actionType = "FUTURE_ACTION")
            .toDomain()
        assertEquals(TriggerType.MANUAL, domain.triggers.single().type)
        assertEquals(ActionType.TOAST, domain.actions.single().type)
    }

    @Test
    fun `invalid trigger logic falls back to ANY`() {
        assertEquals(TriggerLogic.ANY, flowJson(triggerLogic = "SOMETIMES").toDomain().triggerLogic)
    }

    @Test
    fun `unparseable createdAt falls back to now`() {
        val before = System.currentTimeMillis()
        val domain = flowJson(createdAt = "not-a-date").toDomain()
        assertTrue(domain.createdAt >= before)
    }

    @Test
    fun `non-primitive config values keep their JSON text`() {
        val domain = flowJson(
            actionType = "SHOW_MENU",
            actionConfig = buildJsonObject {
                putJsonArray("options") {
                    add("A")
                    add("B")
                }
            },
        ).toDomain()
        // Editor stores menu options as a JSON array string; the mapper must match
        assertEquals("""["A","B"]""", domain.actions.single().config["options"])
    }

    // ----- global_variables (optional field, added after the first releases) -----

    @Test
    fun `global variable declarations round-trip through the file format`() {
        val encoded = FlowSerializer.encode(
            flowJson().copy(
                globalVariables = listOf(GlobalVariableJson("counter", "INTEGER", "0")),
            ),
        )
        val decoded = FlowSerializer.decode(encoded).getOrThrow()
        assertEquals("counter", decoded.globalVariables.single().name)
        assertEquals("0", decoded.globalVariables.single().defaultValue)
    }

    @Test
    fun `declared variables survive the import`() {
        // They used to be dropped on the floor: the mapper hardcoded an empty list, so every
        // exported flow came back with none of its variables. At runtime that is not a missing
        // declaration but a wrong result — "{{counter}} + 1" with no counter is not arithmetic,
        // so the literal text got stored in the variable and shown to the user.
        val flow = flowJson(
            variables = listOf(
                VariableJson("counter", "INTEGER", JsonPrimitive("5")),
                VariableJson("label", "STRING", JsonPrimitive("hi")),
            ),
        ).toDomain()

        assertEquals(listOf("counter", "label"), flow.variables.map { it.name })
        assertEquals(VariableType.INTEGER, flow.variables[0].type)
        assertEquals("5", flow.variables[0].defaultValue)
    }

    @Test
    fun `a variable default keeps its content rather than its JSON spelling`() {
        // A typed default arrives as a JsonElement. Taking toString() would store a string
        // default still wrapped in the quotes it was written with.
        val flow = flowJson(
            variables = listOf(
                VariableJson("n", "INTEGER", JsonPrimitive(5)),
                VariableJson("s", "STRING", JsonPrimitive("text")),
                VariableJson("b", "BOOLEAN", JsonPrimitive(true)),
            ),
        ).toDomain()

        assertEquals(listOf("5", "text", "true"), flow.variables.map { it.defaultValue })
    }

    @Test
    fun `an unknown variable type falls back to STRING like the other enums do`() {
        val flow = flowJson(
            variables = listOf(VariableJson("v", "NUMBER", JsonPrimitive("1"))),
        ).toDomain()

        assertEquals(VariableType.STRING, flow.variables.single().type)
    }

    @Test
    fun `a file written before global variables existed still decodes`() {
        val legacy = FlowSerializer.encode(flowJson())
            .replace(Regex(""",?\s*"global_variables"\s*:\s*\[]"""), "")
        assertFalse("global_variables" in legacy, "fixture must not contain the field")
        assertTrue(FlowSerializer.decode(legacy).getOrThrow().globalVariables.isEmpty())
    }
}
