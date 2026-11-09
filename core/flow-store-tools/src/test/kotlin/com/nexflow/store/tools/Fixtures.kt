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
package com.nexflow.store.tools

import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowCatalog
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.GlobalVariableJson
import com.nexflow.core.flowschema.SetupField
import com.nexflow.core.flowschema.StoreMeta
import com.nexflow.core.flowschema.TriggerJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** Builds store flows for tests: a valid one by default, bent one rule at a time. */
object Fixtures {
    val catalog: FlowCatalog = FlowCatalog.decode(File("../../docs/flow-catalog.json").readText())

    private var n = 0
    fun uuid() = "00000000-0000-4000-8000-%012d".format(n++)

    fun cfg(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    fun trigger(type: String, vararg config: Pair<String, String>) = TriggerJson(uuid(), type, cfg(*config))

    fun condition(type: String, vararg config: Pair<String, String>) = ConditionJson(uuid(), type, cfg(*config), negate = false)

    private var order = 0
    fun action(type: String, vararg config: Pair<String, String>) =
        ActionJson(uuid(), type, cfg(*config), order = order++, enabled = true)

    fun flow(
        triggers: List<TriggerJson> = listOf(trigger("TIME", "time" to "07:30", "repeat" to "DAILY")),
        actions: List<ActionJson> = listOf(action("TOAST", "message" to "Good morning")),
        conditions: List<ConditionJson> = emptyList(),
        globals: List<String> = emptyList(),
    ) = FlowJson(
        schemaVersion = 1, id = uuid(), name = "Morning", description = "", tags = emptyList(),
        enabled = false, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
        triggers = triggers, triggerLogic = "ANY", conditions = conditions, actions = actions,
        variables = emptyList(),
        globalVariables = globals.map { GlobalVariableJson(it, "STRING", "") },
    )

    fun meta(slug: String = "good-morning", version: Int = 1, setup: List<SetupField> = emptyList()) = StoreMeta(
        slug = slug,
        version = version,
        languages = mapOf("en" to StoreMeta.Text("Good morning", "Says good morning.")),
        tags = listOf("morning"),
        setup = setup,
    )

    fun setup(id: String, kind: String, item: String, key: String) =
        SetupField(id, SetupField.Target(kind, item, key), mapOf("en" to "Your $key"))
}
