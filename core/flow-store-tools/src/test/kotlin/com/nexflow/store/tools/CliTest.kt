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
import com.nexflow.core.flowschema.StoreIndex
import com.nexflow.core.flowschema.StoreJson
import com.nexflow.core.flowschema.StoreMeta
import com.nexflow.store.tools.Fixtures.action
import com.nexflow.store.tools.Fixtures.flow
import com.nexflow.store.tools.Fixtures.meta
import com.nexflow.store.tools.Fixtures.trigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The commands end to end, on a store repo laid out in a temp dir. */
class CliTest {

    @TempDir lateinit var tmp: File

    private val catalog = File("../../docs/flow-catalog.json").absolutePath
    private val output = mutableListOf<String>()
    private fun cli(vararg args: String) = Cli { output += it }.run(args.toList())

    private fun repo(name: String = "repo") = File(tmp, name).apply { File(this, "flows").mkdirs() }

    private fun put(repo: File, meta: StoreMeta, flow: FlowJson) {
        val dir = File(repo, "flows/${meta.slug}").apply { mkdirs() }
        File(dir, "meta.json").writeText(StoreJson.json.encodeToString(StoreMeta.serializer(), meta))
        File(dir, "flow.json").writeText(FlowSerializer.encode(flow))
    }

    @Test
    fun `from-export moves personal values into setup, and the result validates`() {
        val export = flow(
            triggers = listOf(trigger("WIFI", "event" to "CONNECTED", "ssid" to "MyHome")),
            actions = listOf(
                action("HTTP_REQUEST", "url" to "https://api.example.com/arrived", "method" to "POST", "headers" to "Authorization: Bearer abc"),
            ),
        ).copy(enabled = true)
        val file = File(tmp, "export.flow").apply { writeText(FlowSerializer.encode(export)) }
        val repo = repo()

        assertEquals(0, cli("from-export", "--export", file.path, "--slug", "home-arrival", "--lang", "en", "--repo", repo.path))

        val meta = StoreJson.json.decodeFromString(StoreMeta.serializer(), File(repo, "flows/home-arrival/meta.json").readText())
        assertEquals(listOf("ssid", "headers"), meta.setup.map { it.id })
        assertEquals(listOf(false, true), meta.setup.map { it.secret })
        val flowText = File(repo, "flows/home-arrival/flow.json").readText()
        assertTrue("MyHome" !in flowText && "Bearer" !in flowText, "personal values must not reach the submission")

        output.clear()
        assertEquals(0, cli("validate", "--repo", repo.path, "--catalog", catalog), "$output")
    }

    @Test
    fun `build-index writes bundles whose sha256 the index records, and leaves revoked flows out`() {
        val repo = repo()
        put(repo, meta("good-morning"), flow())
        put(repo, meta("old-one"), flow())
        File(repo, "revoked.json").writeText("""{"revoked":[{"slug":"old-one","reason":"broken","date":"2026-11-09"}]}""")
        File(tmp, "authors.json").writeText("""{"good-morning":"someone"}""")
        val out = File(tmp, "site")

        assertEquals(
            0,
            cli("build-index", "--repo", repo.path, "--catalog", catalog, "--out", out.path,
                "--authors", File(tmp, "authors.json").path, "--now", "2026-11-09T00:00:00Z"),
            "$output",
        )

        val index = StoreJson.json.decodeFromString(StoreIndex.serializer(), File(out, "index.json").readText())
        assertEquals("2026-11-09T00:00:00Z", index.generatedAt)
        val entry = index.flows.single()
        assertEquals("good-morning", entry.slug)
        assertEquals("someone", entry.author)
        assertEquals(IndexBuilder.sha256(File(out, entry.bundle).readBytes()), entry.sha256)
        assertEquals(listOf("old-one"), index.revoked.map { it.slug })
    }

    @Test
    fun `build-index refuses to publish while any flow fails`() {
        val repo = repo()
        put(repo, meta("good-morning"), flow().copy(enabled = true))
        val out = File(tmp, "site")
        assertEquals(1, cli("build-index", "--repo", repo.path, "--catalog", catalog, "--out", out.path))
        assertTrue(!File(out, "index.json").exists())
    }

    @Test
    fun `validate against the base refuses a removed flow`() {
        val base = repo("base").also { put(it, meta("good-morning"), flow()) }
        val head = repo("head")
        assertEquals(1, cli("validate", "--repo", head.path, "--catalog", catalog, "--base", base.path))
        assertTrue(output.any { "may not be removed or renamed" in it }, "$output")
    }

    @Test
    fun `dry-run walks the flow's steps`() {
        val repo = repo()
        put(
            repo, meta("good-morning"),
            flow(actions = listOf(action("REPEAT_BLOCK", "count" to "2"), action("TOAST", "message" to "hi"), action("END_REPEAT"))),
        )
        assertEquals(0, cli("dry-run", "--repo", repo.path, "--slug", "good-morning"), "$output")
        assertEquals(2, output.count { "TOAST" in it }, "$output")
    }

    @Test
    fun `a catalog newer than the tool is refused, not misread`() {
        val repo = repo()
        val newer = File(tmp, "catalog.json").apply {
            writeText(File(catalog).readText().replaceFirst("\"catalog_version\": 1", "\"catalog_version\": 99"))
        }
        assertEquals(2, cli("validate", "--repo", repo.path, "--catalog", newer.path))
        assertTrue(output.any { "newer than this tool" in it }, "$output")
    }

    @Test
    fun `validate --json records each flow, and --format github cannot be steered by the pull request`() {
        val repo = repo()
        put(repo, meta("good-morning"), flow())
        // A slug that tries to end the annotation line and start a workflow command of its own.
        val dir = File(repo, "flows/sneaky").apply { mkdirs() }
        File(dir, "meta.json").writeText(
            StoreJson.json.encodeToString(StoreMeta.serializer(), meta("x\n::notice::pwned%0A::warning::too")),
        )
        File(dir, "flow.json").writeText(FlowSerializer.encode(flow()))
        val json = File(tmp, "result.json")

        assertEquals(1, cli("validate", "--repo", repo.path, "--catalog", catalog, "--json", json.path, "--format", "github"))

        assertTrue(output.none { it.startsWith("::") && !it.startsWith("::error ") }, "$output")
        assertTrue(output.filter { it.startsWith("::error ") }.all { "\n" !in it && "%0A::" !in it.substringAfter("::error ").substringBefore("::") }, "$output")
        val result = StoreJson.json.decodeFromString(ValidationResult.serializer(), json.readText())
        assertEquals(listOf("good-morning" to true, "sneaky" to false), result.flows.map { it.slug to it.ok })
        assertTrue(result.flows.first().dryRun.any { "TOAST" in it }, "a passing flow is dry-run too")
        assertTrue(result.flows.flatMap { it.problems }.none { "\n" in it })
    }

    @Test
    fun `revoking a flow is the one way to remove it`() {
        val base = repo("base").also { put(it, meta("good-morning"), flow()) }
        val head = repo("head")
        File(head, "revoked.json").writeText("""{"revoked":[{"slug":"good-morning","reason":"malicious","date":"2026-11-11"}]}""")
        assertEquals(0, cli("validate", "--repo", head.path, "--catalog", catalog, "--base", base.path), "$output")
    }

    @Test
    fun `featured json must name flows that exist, and a flow folder holds only its two files`() {
        val repo = repo()
        put(repo, meta("good-morning"), flow())
        File(repo, "featured.json").writeText("""{"featured":["good-morning","nowhere"]}""")
        File(repo, "flows/good-morning/README.md").writeText("stale")

        assertEquals(1, cli("validate", "--repo", repo.path, "--catalog", catalog))
        assertTrue(output.any { "\"nowhere\" is not a flow" in it }, "$output")
        assertTrue(output.any { "only meta.json and flow.json" in it }, "$output")
    }

    @Test
    fun `build-index marks maintainers' flows official, keeps featured order, and escapes the pages`() {
        val repo = repo()
        put(repo, meta("good-morning"), flow())
        val hostile = meta("night-mode").copy(
            languages = mapOf("en" to StoreMeta.Text("<script>alert(1)</script>", "\"><img src=x onerror=alert(1)>")),
        )
        put(repo, hostile, flow())
        File(repo, "featured.json").writeText("""{"featured":["night-mode","good-morning"]}""")
        File(repo, "maintainers.json").writeText("""{"maintainers":["Keeper"]}""")
        File(tmp, "authors.json").writeText("""{"good-morning":"keeper","night-mode":"someone"}""")
        val out = File(tmp, "site")

        assertEquals(
            0,
            cli("build-index", "--repo", repo.path, "--catalog", catalog, "--out", out.path, "--authors", File(tmp, "authors.json").path),
            "$output",
        )

        val index = StoreJson.json.decodeFromString(StoreIndex.serializer(), File(out, "index.json").readText())
        assertEquals(listOf("night-mode", "good-morning"), index.featured)
        assertEquals(mapOf("good-morning" to true, "night-mode" to false), index.flows.associate { it.slug to it.official })
        val pages = listOf(File(out, "index.html"), File(out, "flows/night-mode.html"), File(out, "flows/good-morning.html"))
        pages.forEach { assertTrue(it.exists(), "$it") }
        pages.forEach { page ->
            val html = page.readText()
            assertTrue("<script" !in html && "<img" !in html, "contributor text must be escaped in $page")
        }
        assertTrue("&lt;script&gt;" in File(out, "flows/night-mode.html").readText())
    }

    @Test
    fun `from-export --json lists the values it took out`() {
        val export = flow(triggers = listOf(trigger("WIFI", "event" to "CONNECTED", "ssid" to "MyHome")))
        val file = File(tmp, "export.flow").apply { writeText(FlowSerializer.encode(export)) }
        val json = File(tmp, "setup.json")

        assertEquals(0, cli("from-export", "--export", file.path, "--slug", "home", "--lang", "en", "--repo", repo().path, "--json", json.path))

        assertTrue("\"ssid\"" in json.readText() && "MyHome" !in json.readText(), json.readText())
    }
}
