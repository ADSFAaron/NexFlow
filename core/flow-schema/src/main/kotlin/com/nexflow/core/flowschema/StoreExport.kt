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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns an ordinary `.flow` export into a flow store submission: the store CLI's `from-export`,
 * and later the app's export wizard (P5).
 *
 * The one judgement it makes is privacy: whatever in the flow identifies its author (their Wi-Fi,
 * a contact's number, an app or a spot on their screen) is cleared and becomes a setup field the
 * installer fills in. Header values are marked secret, since that is where API tokens live.
 * The result still has to pass `validate`; this only gets it most of the way.
 */
object StoreExport {

    data class Submission(val meta: StoreMeta, val flow: FlowJson)

    fun fromExport(export: FlowJson, slug: String, language: String): Submission {
        val setup = mutableListOf<SetupField>()
        val used = mutableMapOf<String, Int>()

        fun strip(kind: String, item: String, config: JsonObject): JsonObject = JsonObject(
            config.mapValues { (key, value) ->
                val text = (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
                if (!FieldPolicy.isPersonal(key) || text.isBlank()) return@mapValues value
                val n = (used[key] ?: 0) + 1
                used[key] = n
                setup += SetupField(
                    id = if (n == 1) key else "${key}_$n",
                    target = SetupField.Target(kind, item, key),
                    label = mapOf(language to key.replace('_', ' ').replaceFirstChar(Char::uppercase)),
                    secret = key in SECRET_KEYS,
                )
                JsonPrimitive("")
            },
        )

        val flow = export.copy(
            enabled = false,
            author = null,
            triggers = export.triggers.map { it.copy(config = strip("trigger", it.id, it.config)) },
            conditions = export.conditions.map { it.copy(config = strip("condition", it.id, it.config)) },
            actions = export.actions.map { it.copy(config = strip("action", it.id, it.config)) },
        )
        val meta = StoreMeta(
            slug = slug,
            version = 1,
            languages = mapOf(language to StoreMeta.Text(export.name.take(60), export.description)),
            tags = export.tags,
            setup = setup,
        )
        return Submission(meta, flow)
    }

    private val SECRET_KEYS = setOf("headers")
}
