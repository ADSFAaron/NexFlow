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
package com.nexflow.validation

import android.content.Context
import com.nexflow.FlavorFeatures
import com.nexflow.ai.AiCatalog
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.info
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Checks a step's config against what its editor offers: only known keys, dropdown values from
 * the list, sliders within range, apps that are installed. Shared by the AI assistant (whose
 * drafts must be repairable from the messages), flow store installs and store exports.
 *
 * Messages are in English and name the exact field, because their first reader is a model that
 * has to fix the draft from them.
 *
 * Known keys are [AiCatalog.capturableKeys]: what a picker writes (coordinates, a location) is also
 * an ordinary field, so a model and a person can set the same keys.
 *
 * @param isInstalled whether a package name is real on this device.
 */
class FlowConfigValidator(
    private val context: Context,
    private val isInstalled: (String) -> Boolean = { true },
) {

    fun trigger(type: TriggerType, config: JsonObject, prefix: String): List<String> =
        config(config, type.info(context).fields, prefix, type.name)

    fun condition(type: ConditionType, config: JsonObject, prefix: String): List<String> =
        config(config, type.info(context).fields, prefix, type.name)

    fun action(type: ActionType, config: JsonObject, prefix: String): List<String> =
        config(config, type.info(context).fields, prefix, type.name)

    /**
     * Every trigger, condition and action of [flow]. Unlike import, which keeps what it cannot
     * run and warns, a type that is unknown or hidden in this flavor is an error here: a store
     * flow that cannot run on this device must not install.
     */
    fun validate(flow: FlowJson): List<String> = buildList {
        flow.triggers.forEachIndexed { i, t ->
            val type = TriggerType.entries.find { it.name == t.type }?.takeIf { it !in FlavorFeatures.hiddenTriggerTypes }
            if (type == null) add("triggers[$i].type: unknown or unavailable trigger type \"${t.type}\"")
            else addAll(trigger(type, t.config, "triggers[$i]"))
        }
        flow.conditions.forEachIndexed { i, c ->
            val type = ConditionType.fromId(c.type)
            if (type == null) add("conditions[$i].type: unknown condition type \"${c.type}\"")
            else addAll(condition(type, c.config, "conditions[$i]"))
        }
        flow.actions.forEachIndexed { i, a ->
            val type = ActionType.entries.find { it.name == a.type }?.takeIf { it !in FlavorFeatures.hiddenActionTypes }
            if (type == null) add("actions[$i].type: unknown or unavailable action type \"${a.type}\"")
            else addAll(action(type, a.config, "actions[$i]"))
        }
    }

    private fun config(
        config: JsonObject,
        fields: List<ConfigField>,
        prefix: String,
        typeName: String,
    ): List<String> = buildList {
        val allowedKeys = AiCatalog.capturableKeys(fields)
        val packageKeys = AiCatalog.packageKeys(fields)
        config.forEach { (key, value) ->
            if (key !in allowedKeys) {
                add(
                    "$prefix.config: unknown key \"$key\" for $typeName. " +
                        if (allowedKeys.isEmpty()) "$typeName takes no config."
                        else "Allowed: ${allowedKeys.joinToString(",")}",
                )
                return@forEach
            }
            val content = (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
            AiCatalog.allowedValues(fields, key)?.let { allowed ->
                if (content !in allowed) {
                    add("$prefix.config.$key: invalid value \"$content\". Allowed: ${allowed.joinToString(",")}")
                }
            }
            AiCatalog.numericRange(fields, key)?.let { range ->
                val n = content.toIntOrNull()
                if (n == null || n !in range) {
                    add("$prefix.config.$key: must be an integer in ${range.first}..${range.last}")
                }
            }
            if (key in packageKeys && content.isNotBlank() && !isInstalled(content)) {
                add(
                    "$prefix.config.$key: \"$content\" is not installed on this device. " +
                        "Call search_installed_apps and use a package_name it returns.",
                )
            }
        }
    }
}
