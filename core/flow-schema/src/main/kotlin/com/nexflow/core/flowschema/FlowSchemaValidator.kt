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

private val UUID_REGEX = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
)
private val VARIABLE_NAME_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
private val SUPPORTED_SCHEMA_VERSIONS = setOf(1)
private const val MAX_ACTIONS_NESTING = 20

object FlowSchemaValidator {
    fun validate(flow: FlowJson): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (flow.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            errors += ValidationError("schema_version", "Unsupported version: ${flow.schemaVersion}")
        }
        if (!UUID_REGEX.matches(flow.id)) {
            errors += ValidationError("id", "Not a valid UUID v4: ${flow.id}")
        }
        if (flow.name.isBlank()) errors += ValidationError("name", "Must not be blank")
        if (flow.name.length > 100) errors += ValidationError("name", "Exceeds 100 characters")
        if (flow.description.length > 2000) errors += ValidationError("description", "Exceeds 2000 characters")
        if (flow.triggers.isEmpty()) errors += ValidationError("triggers", "Must contain at least one trigger")
        if (flow.actions.isEmpty()) errors += ValidationError("actions", "Must contain at least one action")
        if (flow.triggerLogic !in listOf("ANY", "ALL")) {
            errors += ValidationError("trigger_logic", "Must be ANY or ALL")
        }

        flow.triggers.forEachIndexed { i, t ->
            if (!UUID_REGEX.matches(t.id)) errors += ValidationError("triggers[$i].id", "Invalid UUID")
        }

        flow.actions.forEachIndexed { i, a ->
            if (!UUID_REGEX.matches(a.id)) errors += ValidationError("actions[$i].id", "Invalid UUID")
            if (a.order < 0) errors += ValidationError("actions[$i].order", "Must be ≥ 0")
        }

        errors += validateControlFlow(flow.actions)

        flow.variables.forEachIndexed { i, v ->
            if (!VARIABLE_NAME_REGEX.matches(v.name)) {
                errors += ValidationError("variables[$i].name", "Invalid identifier: ${v.name}")
            }
            if (v.type !in listOf("STRING", "INTEGER", "BOOLEAN", "DECIMAL")) {
                errors += ValidationError("variables[$i].type", "Unknown type: ${v.type}")
            }
        }

        return errors
    }

    private fun validateControlFlow(actions: List<ActionJson>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        var ifDepth = 0
        var repeatDepth = 0

        actions.sortedBy { it.order }.forEach { action ->
            when (action.type) {
                "IF_BLOCK" -> { ifDepth++; if (ifDepth > MAX_ACTIONS_NESTING) errors += ValidationError("actions", "IF nesting exceeds limit") }
                "END_IF" -> { ifDepth--; if (ifDepth < 0) errors += ValidationError("actions", "Unexpected END_IF") }
                "REPEAT_BLOCK" -> { repeatDepth++; if (repeatDepth > MAX_ACTIONS_NESTING) errors += ValidationError("actions", "REPEAT nesting exceeds limit") }
                "END_REPEAT" -> { repeatDepth--; if (repeatDepth < 0) errors += ValidationError("actions", "Unexpected END_REPEAT") }
            }
        }
        if (ifDepth != 0) errors += ValidationError("actions", "Unclosed IF_BLOCK (depth=$ifDepth)")
        if (repeatDepth != 0) errors += ValidationError("actions", "Unclosed REPEAT_BLOCK (depth=$repeatDepth)")
        return errors
    }
}

data class ValidationError(val field: String, val message: String)
