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
package com.nexflow.ai

import com.nexflow.core.automation.model.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders an existing flow as the context preamble for "edit this flow with Gemini".
 *
 * The shape deliberately mirrors `create_flow`'s arguments (uppercase type name + config as a
 * JSON-encoded string, see [AiTools]) so the model can echo the flow back with one part changed
 * instead of re-deriving the whole thing from prose.
 */
object FlowContextFormatter {

    fun format(flow: Flow): String = buildString {
        appendLine(
            "CURRENT FLOW — the user opened this existing flow and wants to modify it. " +
                "When they ask for a change, call create_flow with the COMPLETE revised flow: " +
                "every trigger and action listed below, with their change applied. Never send only " +
                "the changed parts — the app replaces this flow with whatever you send. If they " +
                "just ask what the flow does, answer in prose without calling create_flow.",
        )
        appendLine()
        appendLine("name: ${flow.name}")
        if (flow.description.isNotBlank()) appendLine("description: ${flow.description}")
        appendLine("trigger_logic: ${flow.triggerLogic.name}")

        appendLine("triggers:")
        if (flow.triggers.isEmpty()) {
            appendLine("  (none — this flow only runs manually)")
        } else {
            flow.triggers.forEach { appendLine("  - ${it.type.name} config: ${it.config.toConfigText()}") }
        }

        appendLine("actions:")
        if (flow.actions.isEmpty()) {
            appendLine("  (none)")
        } else {
            flow.actions.sortedBy { it.order }.forEachIndexed { index, action ->
                val disabled = if (action.enabled) "" else " (disabled)"
                appendLine("  ${index + 1}. ${action.type.name}$disabled config: ${action.config.toConfigText()}")
            }
        }

        if (flow.variables.isNotEmpty()) {
            appendLine("variables (referenced as {{name}}):")
            flow.variables.forEach { appendLine("  - ${it.name}: ${it.type.name} = ${it.defaultValue}") }
        }
    }.trimEnd()

    private fun Map<String, String>.toConfigText(): String =
        JsonObject(mapValues { (_, v) -> JsonPrimitive(v) }).toString()
}
