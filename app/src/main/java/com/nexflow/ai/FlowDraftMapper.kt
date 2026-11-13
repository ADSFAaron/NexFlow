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

import android.content.Context
import com.nexflow.FlavorFeatures
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSchemaValidator
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.validation.FlowConfigValidator
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Maps `create_flow` function-call arguments into a [FlowJson] the app owns: the AI supplies
 * only names, types, and config; ids, timestamps, order, and the disabled flag are injected
 * here so they can never be wrong.
 *
 * Unlike the import path, unknown types/keys are ERRORS (not fallbacks) — they go back to
 * Gemini as a functionResponse so it can correct itself.
 */
object FlowDraftMapper {

    data class DraftResult(val flow: FlowJson?, val errors: List<String>) {
        val isValid: Boolean get() = flow != null && errors.isEmpty()
    }

    /**
     * @param knownGlobals names of the global variables that exist on this device. A generated
     *   SET_VARIABLE writing any other `g:` name is an error, not a new global — the engine
     *   refuses to run it, so it has to go back to Gemini for repair instead of shipping.
     * @param isInstalled whether a package name is real on this device. A made-up one is
     *   well-formed, so without this it passes validation and the flow only fails when it runs.
     */
    fun fromArgs(
        args: JsonObject,
        context: Context,
        knownGlobals: Set<String> = emptySet(),
        isInstalled: (String) -> Boolean = { true },
    ): DraftResult {
        val errors = mutableListOf<String>()

        val name = args.stringValue("name")?.trim().orEmpty()
        if (name.isBlank()) errors += "name: must not be blank"
        val description = args.stringValue("description")?.trim().orEmpty()
        val triggerLogic = args.stringValue("trigger_logic")?.uppercase() ?: "ANY"
        if (triggerLogic !in listOf("ANY", "ALL")) errors += "trigger_logic: must be ANY or ALL"

        val triggers = (args["triggers"] as? JsonArray).orEmpty()
            .mapIndexedNotNull { i, element -> parseTrigger(element, i, context, isInstalled, errors) }
        if (triggers.isEmpty()) errors += "triggers: at least one trigger is required"

        val actions = (args["actions"] as? JsonArray).orEmpty()
            .mapIndexedNotNull { i, element -> parseAction(element, i, context, isInstalled, errors) }
        if (actions.isEmpty()) errors += "actions: at least one action is required"

        errors += validateMenuMarkers(actions)
        errors += validateGlobalWrites(actions, knownGlobals)

        if (errors.isNotEmpty()) return DraftResult(flow = null, errors = errors)

        val now = Instant.now().toString()
        val flow = FlowJson(
            schemaVersion = 1,
            id = UUID.randomUUID().toString(),
            name = name.take(100),
            description = description.take(2000),
            author = null,
            icon = null,
            iconColor = null,
            tags = emptyList(),
            enabled = false,
            createdAt = now,
            updatedAt = now,
            triggers = triggers,
            triggerLogic = triggerLogic,
            conditions = emptyList(),
            actions = actions,
            variables = emptyList(),
        )

        val schemaErrors = FlowSchemaValidator.validate(flow).map { "${it.field}: ${it.message}" }
        return DraftResult(flow = flow.takeIf { schemaErrors.isEmpty() }, errors = schemaErrors)
    }

    private fun parseTrigger(
        element: JsonElement,
        index: Int,
        context: Context,
        isInstalled: (String) -> Boolean,
        errors: MutableList<String>,
    ): TriggerJson? {
        val obj = element as? JsonObject
            ?: return null.also { errors += "triggers[$index]: must be an object" }
        val typeName = obj.stringValue("type")?.uppercase().orEmpty()
        val type = runCatching { TriggerType.valueOf(typeName) }.getOrNull()
            ?.takeIf { it !in FlavorFeatures.hiddenTriggerTypes }
            ?: return null.also {
                errors += "triggers[$index].type: unknown trigger type \"$typeName\". " +
                    "Valid: ${
                        TriggerType.entries.filter { it !in FlavorFeatures.hiddenTriggerTypes }
                            .joinToString(",") { it.name }
                    }"
            }
        val config = parseConfig(obj["config"], "triggers[$index].config", errors)
        errors += FlowConfigValidator(context, isInstalled).trigger(type, config, "triggers[$index]")
        return TriggerJson(id = UUID.randomUUID().toString(), type = type.name, config = config)
    }

    private fun parseAction(
        element: JsonElement,
        index: Int,
        context: Context,
        isInstalled: (String) -> Boolean,
        errors: MutableList<String>,
    ): ActionJson? {
        val obj = element as? JsonObject
            ?: return null.also { errors += "actions[$index]: must be an object" }
        val typeName = obj.stringValue("type")?.uppercase().orEmpty()
        val type = runCatching { ActionType.valueOf(typeName) }.getOrNull()
            ?.takeIf { it !in FlavorFeatures.hiddenActionTypes }
            ?: return null.also {
                errors += "actions[$index].type: unknown action type \"$typeName\". " +
                    "Valid: ${
                        ActionType.entries.filter { it !in FlavorFeatures.hiddenActionTypes }
                            .joinToString(",") { it.name }
                    }"
            }
        val config = parseConfig(obj["config"], "actions[$index].config", errors)
        errors += FlowConfigValidator(context, isInstalled).action(type, config, "actions[$index]")
        return ActionJson(
            id = UUID.randomUUID().toString(),
            type = type.name,
            config = config,
            order = index,
            enabled = true,
        )
    }

    // `config` is declared to Gemini as a JSON-encoded string (see AiTools), but tolerate a
    // real object too — models don't always follow the declared shape.
    private fun parseConfig(
        element: JsonElement?,
        field: String,
        errors: MutableList<String>,
    ): JsonObject = when (element) {
        null -> JsonObject(emptyMap())
        is JsonObject -> element
        is JsonPrimitive -> {
            val text = element.contentOrNull?.trim().orEmpty()
            if (text.isEmpty() || text == "{}") {
                JsonObject(emptyMap())
            } else {
                runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse {
                    errors += "$field: not a valid JSON object string"
                    JsonObject(emptyMap())
                }
            }
        }
        else -> {
            errors += "$field: must be a JSON object (encoded as string)"
            JsonObject(emptyMap())
        }
    }

    // FlowSchemaValidator covers IF/REPEAT balance; menu markers are app-level.
    private fun validateMenuMarkers(actions: List<ActionJson>): List<String> {
        val errors = mutableListOf<String>()
        var menuDepth = 0
        actions.forEach { action ->
            when (action.type) {
                "SHOW_MENU" -> menuDepth++
                "MENU_CASE" -> if (menuDepth <= 0) errors += "actions: MENU_CASE outside SHOW_MENU…END_MENU"
                "END_MENU" -> {
                    menuDepth--
                    if (menuDepth < 0) errors += "actions: unexpected END_MENU"
                }
            }
        }
        if (menuDepth > 0) errors += "actions: unclosed SHOW_MENU (missing END_MENU)"
        return errors
    }

    /** SET_VARIABLE may create a local variable on the fly, but never a global. */
    private fun validateGlobalWrites(actions: List<ActionJson>, knownGlobals: Set<String>): List<String> =
        actions.withIndex()
            .filter { (_, a) -> a.type == "SET_VARIABLE" }
            .mapNotNull { (i, a) ->
                val target = (a.config["variable_name"] as? JsonPrimitive)?.contentOrNull?.trim()
                    ?: return@mapNotNull null
                val prefix = FlowInterpreter.GLOBAL_PREFIX
                if (!target.startsWith(prefix)) return@mapNotNull null
                val bare = target.removePrefix(prefix)
                if (bare in knownGlobals) return@mapNotNull null
                "actions[$i].config.variable_name: global variable \"$target\" does not exist. " +
                    "Existing globals: ${
                        knownGlobals.joinToString(", ") { "$prefix$it" }.ifEmpty { "(none)" }
                    }. Use a local variable name (no \"$prefix\" prefix) instead."
            }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()
}
