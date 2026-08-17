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

import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * FlowJson → domain mapper shared by JSON import and the AI assistant.
 *
 * Unknown trigger/action types fall back to MANUAL/TOAST — lenient on purpose so files from
 * newer app versions still import. Callers that need strict types (the AI repair loop) must
 * reject unknown types before calling this.
 */
fun FlowJson.toDomain(forceDisabled: Boolean = true): AutomationFlow {
    val now = System.currentTimeMillis()
    fun String.toEpochMs() = runCatching {
        java.time.Instant.parse(this).toEpochMilli()
    }.getOrDefault(now)

    return AutomationFlow(
        id = id,
        schemaVersion = schemaVersion,
        name = name,
        description = description,
        author = author,
        icon = icon,
        iconColor = iconColor,
        tags = tags,
        // Security: never trust the `enabled` flag of a flow the user didn't build by hand.
        // An attacker-crafted (or AI-hallucinated) flow must not auto-run SMS/HTTP/file
        // actions the moment it lands; the user enables it explicitly, which also routes
        // through the missing-permission wizard.
        enabled = if (forceDisabled) false else enabled,
        createdAt = createdAt.toEpochMs(),
        updatedAt = now,
        triggers = triggers.map { tj ->
            Trigger(
                id = tj.id,
                type = runCatching { TriggerType.valueOf(tj.type) }.getOrDefault(TriggerType.MANUAL),
                config = tj.config.toStringMap(),
            )
        },
        triggerLogic = runCatching { TriggerLogic.valueOf(triggerLogic) }.getOrDefault(TriggerLogic.ANY),
        conditions = conditions.map { cj ->
            Condition(
                id = cj.id,
                type = cj.type,
                config = cj.config.toStringMap(),
                negate = cj.negate,
            )
        },
        actions = actions.map { aj ->
            Action(
                id = aj.id,
                type = runCatching { ActionType.valueOf(aj.type) }.getOrDefault(ActionType.TOAST),
                config = aj.config.toStringMap(),
                order = aj.order,
                enabled = aj.enabled,
            )
        },
        variables = variables.map { vj ->
            Variable(
                name = vj.name,
                type = runCatching { VariableType.valueOf(vj.type) }.getOrDefault(VariableType.STRING),
                // default_value is typed in the file (5, "5", true), but a flow variable holds
                // text, so unwrap a primitive to its content rather than its JSON spelling —
                // otherwise a string default arrives still wrapped in its quotes.
                defaultValue = (vj.defaultValue as? JsonPrimitive)?.contentOrNull
                    ?: vj.defaultValue.toString(),
            )
        },
    )
}

/**
 * domain → FlowJson, the inverse of [toDomain]. Used by export and by the AI assistant's diff,
 * which has to compare a stored flow against the one the model just proposed.
 *
 * Global variable declarations are the caller's job: which globals a flow references is an
 * export concern, and the diff doesn't care.
 */
fun AutomationFlow.toFlowJson(): FlowJson = FlowJson(
    schemaVersion = schemaVersion,
    id = id,
    name = name,
    description = description,
    author = author,
    icon = icon,
    iconColor = iconColor,
    tags = tags,
    enabled = enabled,
    createdAt = java.time.Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = java.time.Instant.ofEpochMilli(updatedAt).toString(),
    triggers = triggers.map { TriggerJson(it.id, it.type.name, it.config.toJsonObject()) },
    triggerLogic = triggerLogic.name,
    conditions = conditions.map {
        ConditionJson(it.id, it.type, it.config.toJsonObject(), it.negate)
    },
    actions = actions.map {
        ActionJson(it.id, it.type.name, it.config.toJsonObject(), it.order, it.enabled)
    },
    variables = variables.map { VariableJson(it.name, it.type.name, JsonPrimitive(it.defaultValue)) },
)

// Non-primitive values (e.g. a real JSON array for SHOW_MENU options) keep their JSON text,
// matching how the editor stores them.
private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }

private fun Map<String, String>.toJsonObject(): JsonObject =
    JsonObject(mapValues { (_, v) -> JsonPrimitive(v) })
