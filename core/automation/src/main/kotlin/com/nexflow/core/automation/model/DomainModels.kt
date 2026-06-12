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
package com.nexflow.core.automation.model

enum class VariableType { STRING, INTEGER, BOOLEAN, DECIMAL }

enum class ExecutionStatus { SUCCESS, FAIL, SKIPPED }

enum class TriggerLogic { ANY, ALL }

data class Flow(
    val id: String,
    val schemaVersion: Int,
    val name: String,
    val description: String,
    val author: String?,
    /** Key into the built-in icon catalog (see app FlowIcons); null = default icon. */
    val icon: String? = null,
    /** Background color as ARGB hex, e.g. "#FF6750A4"; null = theme default. */
    val iconColor: String? = null,
    val tags: List<String>,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val triggers: List<Trigger>,
    val triggerLogic: TriggerLogic,
    val conditions: List<Condition>,
    val actions: List<Action>,
    val variables: List<Variable>,
)

data class Trigger(
    val id: String,
    val type: TriggerType,
    val config: Map<String, String>,
)

data class Action(
    val id: String,
    val type: ActionType,
    val config: Map<String, String>,
    val order: Int,
    val enabled: Boolean,
)

data class Condition(
    val id: String,
    val type: String,
    val config: Map<String, String>,
    val negate: Boolean,
)

data class Variable(
    val name: String,
    val type: VariableType,
    val defaultValue: String,
)

data class ExecutionLog(
    val id: String,
    val flowId: String,
    val triggeredAt: Long,
    val status: ExecutionStatus,
    val errorMessage: String?,
    val executionDurationMs: Long,
)
