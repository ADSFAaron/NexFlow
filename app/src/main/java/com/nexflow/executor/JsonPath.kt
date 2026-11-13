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
package com.nexflow.executor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.nexflow.core.automation.LogText

/**
 * Pulls a single value out of a JSON document with a dot path: `main.temp`, `items.0.name`.
 *
 * Flow variables are plain strings, so an API response is only useful once one field of it has
 * been reduced to text an `IF_BLOCK` expression can compare. That is all this does — no filters,
 * no wildcards, no recursive descent, deliberately not a JSONPath implementation.
 *
 * Every failure carries a message naming *where* the path stopped matching: the result lands in
 * the flow's execution log, which is the only debugging tool the user has for a request whose
 * shape they cannot see.
 */
internal object JsonPath {

    /**
     * @param path segments separated by `.`; an integer segment indexes an array. A leading `$.`
     *   or `$` is tolerated for anyone used to writing real JSONPath. A blank path returns the
     *   document unchanged.
     * @return the value as text — an unquoted string, a number/boolean as written, `null` for a
     *   JSON null, and compact JSON for an object or array — or a failure explaining what broke.
     */
    fun extract(json: String, path: String): Result<String> {
        val cleaned = path.trim().removePrefix("$").removePrefix(".")
        if (cleaned.isEmpty()) return Result.success(json)

        val root = runCatching { Json.parseToJsonElement(json) }.getOrElse {
            return failure("json_invalid", it.message)
        }

        var current: JsonElement = root
        val walked = StringBuilder()
        for (segment in cleaned.split('.')) {
            if (segment.isEmpty()) return failure("json_empty_segment", path)
            val next = when (val node = current) {
                is JsonObject -> node[segment]
                    ?: return failure("json_missing_key", walked, segment, node.keys.joinToString(", "))

                is JsonArray -> {
                    val index = segment.toIntOrNull()
                        ?: return failure("json_not_number", walked, segment)
                    node.getOrNull(index)
                        ?: return failure("json_out_of_range", walked, node.size, index)
                }

                else -> return failure("json_scalar", walked, segment)
            }
            current = next
            if (walked.isNotEmpty()) walked.append('.')
            walked.append(segment)
        }
        return Result.success(current.asText())
    }

    /** Objects and arrays come back as compact JSON so a second action can narrow them further. */
    private fun JsonElement.asText(): String = when (this) {
        JsonNull -> "null"
        is JsonPrimitive -> content
        else -> toString()
    }

    /**
     * The first argument is always where the walk had got to — empty at the top of the document,
     * which the log renders as "the response" in the reader's language.
     */
    private fun failure(code: String, vararg args: Any?): Result<String> =
        Result.failure(IllegalArgumentException(LogText.of(code, *args)))
}
