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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Serializable mirror of the .flow JSON format. See docs/FLOW_SCHEMA.md for field semantics. */
@Serializable
data class FlowJson(
    @SerialName("schema_version") val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val author: String? = null,
    val icon: String? = null,
    @SerialName("icon_color") val iconColor: String? = null,
    val tags: List<String>,
    val enabled: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val triggers: List<TriggerJson>,
    @SerialName("trigger_logic") val triggerLogic: String,
    val conditions: List<ConditionJson>,
    val actions: List<ActionJson>,
    val variables: List<VariableJson>,
)

@Serializable
data class TriggerJson(
    val id: String,
    val type: String,
    val config: JsonObject,
)

@Serializable
data class ConditionJson(
    val id: String,
    val type: String,
    val config: JsonObject,
    val negate: Boolean,
)

@Serializable
data class ActionJson(
    val id: String,
    val type: String,
    val config: JsonObject,
    val order: Int,
    val enabled: Boolean,
)

@Serializable
data class VariableJson(
    val name: String,
    val type: String,
    @SerialName("default_value") val defaultValue: kotlinx.serialization.json.JsonElement,
)
