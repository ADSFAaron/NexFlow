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
import com.nexflow.core.flowschema.SetupField
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

private const val USAGE = """flow-store-tools <command> [options]

  validate     --repo DIR --catalog FILE [--base DIR] [--json FILE] [--format text|github]
               Check every flow in DIR, and dry-run the ones that pass. With --base (the target
               branch's checkout), also require a higher version for changed flows and refuse
               removed or renamed ones. --json writes the results for a workflow to read;
               --format github adds an error annotation per problem.
  dry-run      --repo DIR --slug SLUG
               Run one flow with every action stubbed and print the steps it takes.
  build-index  --repo DIR --catalog FILE --out DIR [--authors FILE] [--now ISO-8601] [--source URL]
               Write index.json, the bundles, and the store's web pages. --authors is a JSON object
               of slug to login; --source is where the pages link a flow's folder to.
  from-export  --export FILE --slug SLUG --lang LANG --repo DIR [--json FILE]
               Turn a NexFlow .flow export into flows/SLUG/{flow.json,meta.json}, personal
               values moved to setup fields. --json lists those fields: each one had a value in
               the export.

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
        val entries = repo.flows()
        val flows = entries.map { check(it, validator, base) }

        val problems = mutableListOf<String>()
        val revoked = repo.revoked.revoked.map { it.slug }.toSet()
        if (base != null) {
            val now = entries.map { it.folder }.toSet()
            // Revoking a flow is the one way to take it out: revoked.json and the folder's removal, in one PR.
            base.flows().map { it.folder }.filter { it !in now && it !in revoked }.forEach {
                problems += "$it: a published flow may not be removed or renamed; a maintainer revokes it instead"
            }
        }
        val folders = entries.map { it.folder }.toSet()
        repo.featured.featured.forEach {
            if (it !in folders) problems += "featured.json: \"$it\" is not a flow in flows/"
        }
        repo.maintainers.maintainers.filterNot { LOGIN.matches(it) }.forEach {
            problems += "maintainers.json: \"$it\" is not a GitHub login"
        }

        val result = ValidationResult(flows.all { it.ok } && problems.isEmpty(), flows, problems.map(::oneLine))
        o["json"]?.let { File(it).writeText(StoreJson.json.encodeToString(ValidationResult.serializer(), result) + "\n") }
        val github = when (o["format"]) {
            null, "text" -> false
            "github" -> true
            else -> error("--format is text or github")
        }
        flows.forEach { f ->
            if (f.ok) out("ok ${f.slug} v${f.version} min_app=${f.minAppVersion} flavors=${f.flavors} risks=${f.risks}")
            f.problems.forEach {
                out("FAIL ${f.slug}: $it")
                if (github) out(annotation(f.slug, it))
            }
        }
        result.problems.forEach {
            out("FAIL $it")
            if (github) out(annotation(null, it))
        }
        return if (result.ok) 0 else 1
    }

    /** The rules, then a dry run for a flow that passed them: a flow that cannot run is broken too. */
    private fun check(e: StoreRepo.Entry, validator: StoreValidator, base: StoreRepo?): ValidationResult.Flow {
        val slug = oneLine(e.folder)
        if (e.error != null) return ValidationResult.Flow(slug, ok = false, problems = listOf(oneLine(e.error)))
        val meta = e.meta!!
        val flow = e.flow!!
        val before = base?.flow(e.folder)?.takeIf { it.error == null }?.let { it.meta!! to it.flow!! }
        val report = validator.check(e.folder, meta, flow, before)
        val dry = if (report.ok) DryRun.run(meta, flow) else null
        val problems = report.problems + listOfNotNull(dry?.takeIf { !it.ok }?.let { "dry run: ${it.message}" })
        return ValidationResult.Flow(
            slug = slug,
            version = meta.version,
            ok = problems.isEmpty(),
            problems = problems.map(::oneLine),
            minAppVersion = report.minAppVersion,
            flavors = report.flavors,
            risks = report.risks,
            dryRun = dry?.steps.orEmpty().map(::oneLine),
        )
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
            source = o["source"] ?: StoreSite.SOURCE,
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
        o["json"]?.let {
            File(it).writeText(StoreJson.json.encodeToString(ListSerializer(SetupField.serializer()), submission.meta.setup) + "\n")
        }
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

    /**
     * A GitHub Actions error annotation. The text comes from the pull request (a folder name, a
     * key, a URL), so it is escaped: an unescaped newline would let it start a workflow command.
     */
    private fun annotation(slug: String?, problem: String): String {
        val file = when {
            slug == null -> null
            problem.startsWith("meta.json") -> "flows/$slug/meta.json"
            else -> "flows/$slug/flow.json"
        }
        val properties = listOfNotNull(file?.let { "file=${escapeProperty(it)}" }, "title=${escapeProperty(slug ?: "flow store")}")
        return "::error ${properties.joinToString(",")}::${escapeData(problem)}"
    }

    private fun escapeData(s: String) = s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    private fun escapeProperty(s: String) = escapeData(s).replace(":", "%3A").replace(",", "%2C")

    /** Control characters out, so a line of output stays one line. */
    private fun oneLine(s: String) = s.replace(CONTROL, " ")

    private companion object {
        val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
        val LOGIN = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$")
    }

    private fun usage(): Int {
        out(USAGE)
        return 2
    }
}
