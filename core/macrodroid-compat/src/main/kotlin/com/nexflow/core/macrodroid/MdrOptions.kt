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
package com.nexflow.core.macrodroid

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reader over one MacroDroid item's settings, used by the per-class mappers in [MdrOptionMappers].
 *
 * It remembers which keys a mapper looked at, so whatever the mapper did *not* look at can be
 * reported to the user — a MacroDroid setting NexFlow has no home for is exactly the kind of
 * thing that must not disappear quietly.
 */
internal class MdrOptions(private val raw: JsonObject) {

    private val consumed = mutableSetOf<String>()

    fun string(key: String): String? = primitive(key)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun int(key: String): Int? = primitive(key)?.intOrNull

    fun double(key: String): Double? = primitive(key)?.doubleOrNull

    fun bool(key: String): Boolean? = primitive(key)?.booleanOrNull

    fun booleans(key: String): List<Boolean>? =
        array(key)?.mapNotNull { (it as? JsonPrimitive)?.booleanOrNull }

    fun strings(key: String): List<String>? =
        array(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    fun array(key: String): JsonArray? = element(key) as? JsonArray

    fun obj(key: String): JsonObject? = element(key) as? JsonObject

    /** Marks keys as deliberately not carried over, so they stay out of [leftovers]. */
    fun ignore(vararg keys: String) {
        consumed += keys
    }

    /**
     * Keys the mapper never read that still hold a value the user chose. Defaults (`false`, `0`,
     * empty string/list) are left out: reporting them would bury the settings that matter.
     */
    val leftovers: List<String>
        get() = raw.keys.filter { it !in consumed && raw[it].isMeaningful() }.sorted()

    private fun element(key: String) = raw[key].also { consumed += key }

    private fun primitive(key: String) = element(key) as? JsonPrimitive

    private fun JsonElement?.isMeaningful(): Boolean = when (this) {
        null -> false
        is JsonArray -> isNotEmpty()
        is JsonObject -> isNotEmpty()
        is JsonPrimitive -> contentOrNull?.let { it.isNotBlank() && it !in NO_VALUE } == true
    }

    private companion object {
        val NO_VALUE = setOf("false", "0", "0.0", "-1", "null")
    }
}
