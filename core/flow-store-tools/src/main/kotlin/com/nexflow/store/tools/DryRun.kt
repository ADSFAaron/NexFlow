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

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.interpreter.InterpreterResult
import com.nexflow.core.automation.interpreter.StepReport
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.StoreMeta
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Runs a store flow through the real interpreter with every action stubbed to succeed, setup
 * fields filled with placeholders and sample trigger values. Catches what static rules cannot:
 * control flow that fails at run time, a variable that never resolves.
 */
object DryRun {

    data class Result(val ok: Boolean, val message: String?, val steps: List<String>)

    private val SAMPLE_TRIGGER = mapOf(
        "body" to "sample message", "text" to "sample text", "title" to "Sample",
        "sender" to "+15550100", "number" to "+15550100", "package" to "com.example.app",
    )

    fun run(meta: StoreMeta, json: FlowJson): Result {
        val placeholders = meta.setup.associate { Triple(it.target.kind, it.target.item, it.target.key) to "<setup:${it.id}>" }
        fun config(kind: String, id: String, config: JsonObject): Map<String, String> = config.mapValues { (key, v) ->
            placeholders[Triple(kind, id, key)] ?: ((v as? JsonPrimitive)?.contentOrNull ?: v.toString())
        }
        val flow = Flow(
            id = json.id, schemaVersion = json.schemaVersion, name = json.name, description = json.description,
            author = null, tags = json.tags, enabled = true, createdAt = 0L, updatedAt = 0L,
            triggers = json.triggers.map { t ->
                Trigger(t.id, TriggerType.entries.find { it.name == t.type } ?: TriggerType.MANUAL, config("trigger", t.id, t.config))
            },
            triggerLogic = if (json.triggerLogic == "ALL") TriggerLogic.ALL else TriggerLogic.ANY,
            conditions = json.conditions.map { c -> Condition(c.id, c.type, config("condition", c.id, c.config), c.negate) },
            actions = json.actions.map { a ->
                Action(a.id, ActionType.entries.find { it.name == a.type } ?: ActionType.TOAST, config("action", a.id, a.config), a.order, a.enabled)
            },
            variables = json.variables.map { v ->
                Variable(
                    v.name,
                    VariableType.entries.find { it.name == v.type } ?: VariableType.STRING,
                    (v.defaultValue as? JsonPrimitive)?.contentOrNull ?: v.defaultValue.toString(),
                )
            },
        )
        val executors = ActionType.entries.associateWith { type ->
            object : ActionExecutor {
                override val supportedType = type
                override suspend fun execute(action: Action, variables: MutableMap<String, String>) = ActionResult.Success
            }
        }
        val steps = mutableListOf<String>()
        val globals = json.globalVariables.associate { it.name to it.defaultValue }
        val result = runBlocking {
            FlowInterpreter(executors).execute(
                flow,
                globalVariables = globals,
                triggerVariables = SAMPLE_TRIGGER,
                onStep = { r: StepReport -> steps += "${"  ".repeat(r.depth)}${r.actionType} ${r.status}${r.note?.let { " ($it)" } ?: ""}" },
            )
        }
        return when (result) {
            is InterpreterResult.Failure -> Result(false, result.message, steps)
            else -> Result(true, null, steps)
        }
    }
}
