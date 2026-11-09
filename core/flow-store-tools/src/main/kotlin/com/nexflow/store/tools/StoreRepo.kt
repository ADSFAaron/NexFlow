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
import com.nexflow.core.flowschema.StoreJson
import com.nexflow.core.flowschema.StoreMeta
import com.nexflow.core.flowschema.StoreRevoked
import java.io.File

/** A checkout of the flow store repo: `flows/<slug>/{meta.json, flow.json}` and `revoked.json`. */
class StoreRepo(val root: File) {

    /** One flow folder; [error] is set when its files could not be read. */
    data class Entry(val folder: String, val meta: StoreMeta?, val flow: FlowJson?, val error: String?)

    val revoked: StoreRevoked by lazy {
        val file = File(root, "revoked.json")
        if (file.exists()) StoreJson.json.decodeFromString(StoreRevoked.serializer(), file.readText()) else StoreRevoked()
    }

    fun flows(): List<Entry> = File(root, "flows").listFiles { f -> f.isDirectory }.orEmpty()
        .sortedBy { it.name }
        .map { load(it) }

    fun flow(folder: String): Entry? = File(root, "flows/$folder").takeIf { it.isDirectory }?.let { load(it) }

    private fun load(dir: File): Entry {
        val metaFile = File(dir, "meta.json")
        val flowFile = File(dir, "flow.json")
        if (!metaFile.exists() || !flowFile.exists()) return Entry(dir.name, null, null, "needs both meta.json and flow.json")
        val meta = runCatching { StoreJson.json.decodeFromString(StoreMeta.serializer(), metaFile.readText()) }
            .getOrElse { return Entry(dir.name, null, null, "meta.json: ${it.message}") }
        val flow = FlowSerializer.decode(flowFile.readText())
            .getOrElse { return Entry(dir.name, meta, null, "flow.json: ${it.message}") }
        return Entry(dir.name, meta, flow, null)
    }
}
