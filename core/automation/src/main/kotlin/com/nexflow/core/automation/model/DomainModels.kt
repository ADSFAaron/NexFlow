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

/**
 * A global (cross-flow) variable, referenced in any flow as `{{g:name}}`.
 * Unlike [Variable] it is not owned by a flow and its [currentValue] persists between runs,
 * so one flow can write it and another can read the updated value.
 */
data class GlobalVariable(
    val name: String,
    val type: VariableType,
    val defaultValue: String,
    val currentValue: String,
)

data class ExecutionLog(
    val id: String,
    val flowId: String,
    val triggeredAt: Long,
    val status: ExecutionStatus,
    val errorMessage: String?,
    val executionDurationMs: Long,
)

/**
 * One action's outcome within a single run — the rows behind the per-run build log.
 *
 * A run's [ExecutionLog] says only whether the flow as a whole worked; that leaves "it failed"
 * with no way to tell *which* of twenty actions failed, and a successful run with nothing to show
 * at all. These steps are that missing detail.
 *
 * Steps are stored flat, in execution order ([seq]), with the nesting recovered from [depth] —
 * a tree would have to be rebuilt against the flow's current action list, and the flow may have
 * been edited since the run.
 */
data class ExecutionStep(
    val logId: String,
    /** Position in the run, from 0. Not [Action.order]: a repeated action appears once per round. */
    val seq: Int,
    val actionId: String,
    val actionType: ActionType,
    /** Nesting level for the UI's indent: 0 = top level, +1 inside each IF/REPEAT/menu branch. */
    val depth: Int,
    /** Round of the innermost enclosing REPEAT, from 0; 0 when not inside one. */
    val iteration: Int,
    val status: ExecutionStatus,
    /**
     * Why it failed, verbatim from the executor. Untranslated — it is engine output, and the
     * language it was produced in is the language it stays in for the life of the row.
     */
    val errorMessage: String?,
    /**
     * A structural remark the UI localizes at render time, as `token` or `token:argument`:
     * `disabled`, `if_true`, `if_false`, `repeat:5`, `menu:Coffee`. Stored as a token rather than
     * a sentence so an old row still reads in the user's language after they switch it.
     */
    val note: String?,
    /** The action's config after variable substitution — only when the user opted into detail. */
    val resolvedConfig: String?,
    val durationMs: Long,
)
