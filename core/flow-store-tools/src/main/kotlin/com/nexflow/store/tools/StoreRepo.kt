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

import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.flowschema.StoreFeatured
import com.nexflow.core.flowschema.StoreJson
import com.nexflow.core.flowschema.StoreMaintainers
import com.nexflow.core.flowschema.StoreMeta
import com.nexflow.core.flowschema.StoreRevoked
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * A checkout of the flow store repo: `flows/<slug>/{meta.json, flow.json}`, plus `revoked.json`,
 * `featured.json` and `maintainers.json` at the root. A missing root file reads as empty.
 */
class StoreRepo(val root: File) {

    /** One flow folder; [error] is set when its files could not be read. */
    data class Entry(val folder: String, val meta: StoreMeta?, val flow: FlowJson?, val error: String?)

    val revoked: StoreRevoked by lazy { read("revoked.json", StoreRevoked.serializer()) ?: StoreRevoked() }
    val featured: StoreFeatured by lazy { read("featured.json", StoreFeatured.serializer()) ?: StoreFeatured() }
    val maintainers: StoreMaintainers by lazy { read("maintainers.json", StoreMaintainers.serializer()) ?: StoreMaintainers() }

    private fun <T> read(name: String, serializer: KSerializer<T>): T? = File(root, name).takeIf { it.exists() }?.let {
        runCatching { StoreJson.json.decodeFromString(serializer, it.readText()) }
            .getOrElse { e -> error("$name: ${e.message}") }
    }

    fun flows(): List<Entry> = File(root, "flows").listFiles { f -> f.isDirectory }.orEmpty()
        .sortedBy { it.name }
        .map { load(it) }

    fun flow(folder: String): Entry? = File(root, "flows/$folder").takeIf { it.isDirectory }?.let { load(it) }

    private fun load(dir: File): Entry {
        val metaFile = File(dir, "meta.json")
        val flowFile = File(dir, "flow.json")
        if (!metaFile.exists() || !flowFile.exists()) return Entry(dir.name, null, null, "needs both meta.json and flow.json")
        // Anything else would be published nowhere, and a README here would go stale against meta.json.
        val extra = dir.list().orEmpty().filter { it !in FILES && it != ".DS_Store" }.sorted()
        if (extra.isNotEmpty()) return Entry(dir.name, null, null, "only meta.json and flow.json belong here, not $extra")
        val meta = runCatching { StoreJson.json.decodeFromString(StoreMeta.serializer(), metaFile.readText()) }
            .getOrElse { return Entry(dir.name, null, null, "meta.json: ${it.message}") }
        val flow = FlowSerializer.decode(flowFile.readText())
            .getOrElse { return Entry(dir.name, meta, null, "flow.json: ${it.message}") }
        return Entry(dir.name, meta, flow, null)
    }

    private companion object {
        val FILES = setOf("meta.json", "flow.json")
    }
}
