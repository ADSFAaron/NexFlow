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

import com.nexflow.core.flowschema.FlowCatalog
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.flowschema.StoreExport
import com.nexflow.core.flowschema.StoreJson
import com.nexflow.core.flowschema.StoreMeta
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

private const val USAGE = """flow-store-tools <command> [options]

  validate     --repo DIR --catalog FILE [--base DIR]
               Check every flow in DIR. With --base (the target branch's checkout), also require a
               higher version for changed flows and refuse removed or renamed ones.
  dry-run      --repo DIR --slug SLUG
               Run one flow with every action stubbed and print the steps it takes.
  build-index  --repo DIR --catalog FILE --out DIR [--authors FILE] [--now ISO-8601]
               Write index.json and the bundles. --authors is a JSON object of slug to login.
  from-export  --export FILE --slug SLUG --lang LANG --repo DIR
               Turn a NexFlow .flow export into flows/SLUG/{flow.json,meta.json}, personal
               values moved to setup fields.

Exits 1 when anything failed; the reasons go to stdout, one per line."""

fun main(args: Array<String>) {
    exitProcess(Cli(::println).run(args.toList()))
}

/** The commands, separated from main so tests can run them without exiting the JVM. */
class Cli(private val out: (String) -> Unit) {

    fun run(args: List<String>): Int {
        val command = args.firstOrNull() ?: return usage()
        val options = parse(args.drop(1)) ?: return usage()
        return runCatching {
            when (command) {
                "validate" -> validate(options)
                "dry-run" -> dryRun(options)
                "build-index" -> buildIndex(options)
                "from-export" -> fromExport(options)
                else -> usage()
            }
        }.getOrElse { e ->
            out("error: ${e.message}")
            2
        }
    }

    private fun validate(o: Map<String, String>): Int {
        val repo = StoreRepo(File(o.require("repo")))
        val validator = validator(repo, o)
        val base = o["base"]?.let { StoreRepo(File(it)) }
        val problems = mutableListOf<String>()
        repo.flows().forEach { e ->
            if (e.error != null) { problems += "${e.folder}: ${e.error}"; return@forEach }
            val before = base?.flow(e.folder)?.takeIf { it.error == null }?.let { it.meta!! to it.flow!! }
            val report = validator.check(e.folder, e.meta!!, e.flow!!, before)
            report.problems.forEach { problems += "${e.folder}: $it" }
            if (report.ok) out("ok ${e.folder} v${e.meta.version} min_app=${report.minAppVersion} flavors=${report.flavors} risks=${report.risks}")
        }
        if (base != null) {
            val now = repo.flows().map { it.folder }.toSet()
            base.flows().map { it.folder }.filter { it !in now }.forEach {
                problems += "$it: a published flow may not be removed or renamed; a maintainer revokes it instead"
            }
        }
        problems.forEach { out("FAIL $it") }
        return if (problems.isEmpty()) 0 else 1
    }

    private fun dryRun(o: Map<String, String>): Int {
        val entry = StoreRepo(File(o.require("repo"))).flow(o.require("slug")) ?: error("no flow ${o["slug"]}")
        entry.error?.let { error(it) }
        val result = DryRun.run(entry.meta!!, entry.flow!!)
        result.steps.forEach { out(it) }
        if (!result.ok) out("FAIL ${result.message}")
        return if (result.ok) 0 else 1
    }

    private fun buildIndex(o: Map<String, String>): Int {
        val repo = StoreRepo(File(o.require("repo")))
        val authors = o["authors"]?.let {
            StoreJson.json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), File(it).readText())
        }.orEmpty()
        val problems = IndexBuilder.build(
            repo, validator(repo, o), File(o.require("out")), authors, o["now"] ?: Instant.now().toString(),
        )
        problems.forEach { out("FAIL $it") }
        return if (problems.isEmpty()) 0 else 1
    }

    private fun fromExport(o: Map<String, String>): Int {
        val export = FlowSerializer.decode(File(o.require("export")).readText()).getOrThrow()
        val slug = o.require("slug")
        val submission = StoreExport.fromExport(export, slug, o.require("lang"))
        val dir = File(o.require("repo"), "flows/$slug")
        if (dir.exists()) error("flows/$slug already exists")
        dir.mkdirs()
        File(dir, "flow.json").writeText(FlowSerializer.encode(submission.flow) + "\n")
        File(dir, "meta.json").writeText(StoreJson.json.encodeToString(StoreMeta.serializer(), submission.meta) + "\n")
        out("wrote flows/$slug with ${submission.meta.setup.size} setup field(s)")
        submission.meta.setup.forEach { out("  setup ${it.id}: ${it.target.kind} ${it.target.item} ${it.target.key}${if (it.secret) " (secret)" else ""}") }
        return 0
    }

    private fun validator(repo: StoreRepo, o: Map<String, String>) = StoreValidator(
        FlowCatalog.decode(File(o.require("catalog")).readText()).also {
            require(it.catalogVersion <= FlowCatalog.CURRENT_VERSION) {
                "catalog version ${it.catalogVersion} is newer than this tool understands; update the tool"
            }
        },
        repo.revoked.revoked.map { it.slug }.toSet(),
    )

    private fun parse(args: List<String>): Map<String, String>? {
        if (args.size % 2 != 0) return null
        return args.chunked(2).associate { (k, v) -> if (!k.startsWith("--")) return null else k.removePrefix("--") to v }
    }

    private fun Map<String, String>.require(key: String) = this[key] ?: error("missing --$key")

    private fun usage(): Int {
        out(USAGE)
        return 2
    }
}
