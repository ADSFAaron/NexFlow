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

import com.nexflow.core.flowschema.StoreBundle
import com.nexflow.core.flowschema.StoreIndex
import com.nexflow.core.flowschema.StoreJson
import java.io.File
import java.security.MessageDigest

/**
 * Writes what the store publishes: `index.json` and one bundle per flow. Signing the index is
 * publish.yml's job, behind the production environment's approval, so this never sees the key.
 */
object IndexBuilder {

    /**
     * @param authors slug to GitHub login, worked out by the workflow from the repo's history.
     * @return problems that kept flows out; the index is written only when there are none.
     */
    fun build(
        repo: StoreRepo,
        validator: StoreValidator,
        out: File,
        authors: Map<String, String>,
        generatedAt: String,
    ): List<String> {
        val problems = mutableListOf<String>()
        val revoked = repo.revoked.revoked.map { it.slug }.toSet()
        val entries = mutableListOf<StoreIndex.Entry>()
        val bundles = mutableMapOf<String, ByteArray>()

        repo.flows().forEach { e ->
            if (e.error != null) { problems += "${e.folder}: ${e.error}"; return@forEach }
            val meta = e.meta!!
            val flow = e.flow!!
            if (meta.slug in revoked) return@forEach
            val report = validator.check(e.folder, meta, flow)
            if (!report.ok) { report.problems.forEach { problems += "${e.folder}: $it" }; return@forEach }

            val path = "bundles/${meta.slug}-${meta.version}.json"
            val bytes = (StoreJson.json.encodeToString(StoreBundle.serializer(), StoreBundle(meta, flow)) + "\n").toByteArray()
            bundles[path] = bytes
            entries += StoreIndex.Entry(
                slug = meta.slug,
                version = meta.version,
                author = authors[meta.slug] ?: "unknown",
                languages = meta.languages,
                tags = meta.tags,
                minAppVersion = report.minAppVersion,
                flavors = report.flavors,
                risks = report.risks,
                setupCount = meta.setup.size,
                bundle = path,
                sha256 = sha256(bytes),
            )
        }
        if (problems.isNotEmpty()) return problems

        out.mkdirs()
        bundles.forEach { (path, bytes) -> File(out, path).apply { parentFile.mkdirs() }.writeBytes(bytes) }
        val index = StoreIndex(generatedAt = generatedAt, flows = entries, revoked = repo.revoked.revoked)
        File(out, "index.json").writeText(StoreJson.json.encodeToString(StoreIndex.serializer(), index) + "\n")
        return emptyList()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
