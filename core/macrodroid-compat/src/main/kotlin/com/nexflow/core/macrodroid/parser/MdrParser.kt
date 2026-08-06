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
package com.nexflow.core.macrodroid.parser

import com.nexflow.core.macrodroid.model.MdrRoot
import kotlinx.serialization.json.Json

private val mdrJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

object MdrParser {

    fun parse(content: String): Result<MdrRoot> = runCatching {
        mdrJson.decodeFromString<MdrRoot>(content)
    }

    /**
     * Whether this looks like a MacroDroid export rather than a NexFlow flow.
     *
     * Both formats are JSON objects, so "starts with a brace" tells them apart from nothing —
     * a MacroDroid file routed to the .flow reader just fails to parse. These two keys are
     * MacroDroid's own and appear in every export: the backup wrapper and the single-macro
     * wrapper respectively.
     */
    fun looksLikeMacroDroid(content: String): Boolean =
        MACRODROID_MARKERS.any { it in content }

    private val MACRODROID_MARKERS = listOf("\"macroList\"", "\"m_classType\"")
}
