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
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.TriggerJson
import kotlinx.serialization.json.JsonObject
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
        variables = emptyList(),
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
}
