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
import com.nexflow.core.flowschema.StoreMeta
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.URLEncoder

/**
 * The store's web pages: a list, and one page per flow that says what it does in words a person
 * reads before installing. They stand in for a README per flow folder, which CI would have had to
 * commit back into a protected branch.
 *
 * Everything on a page came from a contributor, so every string is HTML-escaped, and the pages
 * carry a CSP that allows no scripts at all.
 */
object StoreSite {

    const val SOURCE = "https://github.com/ADSFAaron/nexflow-flow-store"

    fun write(out: File, index: StoreIndex, bundles: Map<String, StoreBundle>, source: String) {
        File(out, "index.html").writeText(list(index, source))
        val dir = File(out, "flows").apply { mkdirs() }
        index.flows.forEach { entry ->
            val bundle = bundles[entry.slug] ?: return@forEach
            File(dir, "${entry.slug}.html").writeText(flowPage(entry, bundle, source))
        }
    }

    private fun list(index: StoreIndex, source: String): String {
        val bySlug = index.flows.associateBy { it.slug }
        val featured = index.featured.mapNotNull { bySlug[it] }
        val body = buildString {
            append("<h1>NexFlow Flow Store</h1>\n")
            append("<p>Ready-made flows for the NexFlow automation app. Install them from the app; ")
            append("these pages are here so you can read what a flow does first.</p>\n")
            append("<p class=\"muted\">${index.flows.size} flows · updated ${esc(index.generatedAt)} · ")
            append("<a href=\"index.json\">index.json</a> · <a href=\"${esc(source)}\">Contribute a flow</a></p>\n")
            if (featured.isNotEmpty()) {
                append("<h2>Featured</h2>\n")
                append(cards(featured))
            }
            append("<h2>All flows</h2>\n")
            append(if (index.flows.isEmpty()) "<p>No flows yet.</p>\n" else cards(index.flows.sortedBy { it.slug }))
        }
        return page("NexFlow Flow Store", body, "")
    }

    private fun cards(entries: List<StoreIndex.Entry>) = buildString {
        append("<ul class=\"cards\">\n")
        entries.forEach { e ->
            val (lang, text) = preferred(e.languages)
            append("<li lang=\"${esc(lang)}\"><a href=\"flows/${url(e.slug)}.html\"><strong>${esc(text.title)}</strong></a>")
            if (e.official) append(" <span class=\"badge\">official</span>")
            append("<p>${esc(text.description.take(160))}${if (text.description.length > 160) "…" else ""}</p>")
            if (e.tags.isNotEmpty()) append("<p class=\"muted\">${e.tags.joinToString(" ") { "#" + esc(it) }}</p>")
            append("</li>\n")
        }
        append("</ul>\n")
    }

    private fun flowPage(e: StoreIndex.Entry, bundle: StoreBundle, source: String): String {
        val meta = bundle.meta
        val flow = bundle.flow
        val languages = StoreJson.LANGUAGES.filter { it in meta.languages } + meta.languages.keys.filter { it !in StoreJson.LANGUAGES }
        val (firstLang, first) = preferred(meta.languages)
        val setupLabels = meta.setup.associate { s ->
            Triple(s.target.kind, s.target.item, s.target.key) to (preferredLabel(s.label) + if (s.secret) " (secret)" else "")
        }

        val body = buildString {
            append("<p><a href=\"../index.html\">← All flows</a></p>\n")
            append("<section lang=\"${esc(firstLang)}\"><h1>${esc(first.title)}</h1>\n")
            append("<p class=\"desc\">${esc(first.description)}</p></section>\n")
            languages.filter { it != firstLang }.forEach { lang ->
                val text = meta.languages.getValue(lang)
                append("<details lang=\"${esc(lang)}\"><summary>${esc(lang)}: ${esc(text.title)}</summary>")
                append("<p class=\"desc\">${esc(text.description)}</p></details>\n")
            }

            append("<table>\n")
            row("Version", e.version.toString())
            row("Author", if (e.author == "unknown") "unknown" else "<a href=\"https://github.com/${url(e.author)}\">${esc(e.author)}</a>", raw = true)
            if (e.official) row("Official", "yes — published by a store maintainer")
            row("Works on", e.flavors.joinToString(", ") { FLAVORS[it] ?: it }.ifEmpty { "no current build" })
            row("Needs NexFlow", "${e.minAppVersion} or later")
            row("Risky actions", e.risks.joinToString(", ") { RISKS[it] ?: it }.ifEmpty { "none" })
            if (e.tags.isNotEmpty()) row("Tags", e.tags.joinToString(" ") { "#$it" })
            append("</table>\n")

            if (meta.setup.isNotEmpty()) {
                append("<h2>You fill in when installing</h2>\n<ul>\n")
                meta.setup.forEach { append("<li>${esc(preferredLabel(it.label))}${if (it.secret) " <span class=\"badge\">secret</span>" else ""}</li>\n") }
                append("</ul>\n")
            }

            append("<h2>What it does</h2>\n")
            append("<h3>Runs when ${if (flow.triggerLogic == "ALL") "all of these" else "any of these"} happen</h3>\n<ul>\n")
            flow.triggers.forEach { append(step("trigger", it.id, it.type, it.config, setupLabels)) }
            append("</ul>\n")
            if (flow.conditions.isNotEmpty()) {
                append("<h3>Only if</h3>\n<ul>\n")
                flow.conditions.forEach {
                    append(step("condition", it.id, (if (it.negate) "NOT " else "") + it.type, it.config, setupLabels))
                }
                append("</ul>\n")
            }
            append("<h3>Then</h3>\n<ol class=\"steps\">\n")
            var depth = 0
            flow.actions.sortedBy { it.order }.forEach { a ->
                val level = when (a.type) {
                    in CLOSERS -> { depth = (depth - 1).coerceAtLeast(0); depth }
                    in MIDDLES -> (depth - 1).coerceAtLeast(0)
                    else -> depth
                }
                val label = a.type + if (!a.enabled) " (off)" else ""
                append(step("action", a.id, label, a.config, setupLabels, level))
                if (a.type in OPENERS) depth++
            }
            append("</ol>\n")
            if (flow.globalVariables.isNotEmpty()) {
                append("<p class=\"muted\">Creates global variables: ")
                append(flow.globalVariables.joinToString(", ") { "<code>${esc(it.name)}</code>" })
                append(". They are renamed for each copy you install.</p>\n")
            }

            append("<h2>Files</h2>\n<p><a href=\"../${esc(e.bundle)}\">${esc(e.bundle)}</a><br>")
            append("<span class=\"muted\">sha256 <code>${esc(e.sha256)}</code></span></p>\n")
            append("<p><a href=\"${esc(source)}/tree/master/flows/${url(e.slug)}\">Source</a> · ")
            append("<a href=\"${esc(source)}/issues/new?template=report-flow.yml&amp;slug=${url(e.slug)}&amp;version=${e.version}\">Report this flow</a></p>\n")
        }
        return page(first.title, body, "../")
    }

    private fun StringBuilder.row(name: String, value: String, raw: Boolean = false) {
        append("<tr><th>${esc(name)}</th><td>${if (raw) value else esc(value)}</td></tr>\n")
    }

    private fun step(
        kind: String,
        id: String,
        label: String,
        config: JsonObject,
        setup: Map<Triple<String, String, String>, String>,
        level: Int = 0,
    ) = buildString {
        append("<li${if (level > 0) " style=\"margin-left:${level * 1.5}em\"" else ""}><code>${esc(label)}</code>")
        val values = config.mapNotNull { (key, value) ->
            val fill = setup[Triple(kind, id, key)]
            when {
                fill != null -> "${esc(key)}: <em>you fill in — ${esc(fill)}</em>"
                value.text().isBlank() -> null
                else -> "${esc(key)}: <code>${esc(value.text().let { if (it.length > 300) it.take(300) + "…" else it })}</code>"
            }
        }
        if (values.isNotEmpty()) append("<br><span class=\"muted\">${values.joinToString(" · ")}</span>")
        append("</li>\n")
    }

    private fun preferred(languages: Map<String, StoreMeta.Text>): Pair<String, StoreMeta.Text> {
        val lang = StoreJson.LANGUAGES.firstOrNull { it in languages } ?: languages.keys.first()
        return lang to languages.getValue(lang)
    }

    private fun preferredLabel(label: Map<String, String>): String =
        StoreJson.LANGUAGES.firstNotNullOfOrNull { label[it] } ?: label.values.firstOrNull().orEmpty()

    private fun JsonElement.text(): String = (this as? JsonPrimitive)?.contentOrNull ?: toString()

    private fun page(title: String, body: String, root: String) = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
<meta name="referrer" content="no-referrer">
<title>${esc(title)}</title>
<style>
:root { color-scheme: light dark; --bg: #fdfcfa; --fg: #1d1b19; --muted: #6b6560; --line: #e4e0db; --accent: #3c5bd6; }
@media (prefers-color-scheme: dark) { :root { --bg: #161514; --fg: #ecebe8; --muted: #a09a94; --line: #34312e; --accent: #9bb0ff; } }
body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.55 system-ui, sans-serif; }
main { max-width: 46rem; margin: 0 auto; padding: 1.5rem 1rem 4rem; }
a { color: var(--accent); }
.muted { color: var(--muted); font-size: .92em; }
.desc { white-space: pre-wrap; }
.badge { font-size: .75em; border: 1px solid var(--line); border-radius: 1em; padding: 0 .5em; color: var(--muted); }
.cards { list-style: none; padding: 0; }
.cards li { border-top: 1px solid var(--line); padding: .75rem 0; }
.cards p { margin: .25rem 0; }
table { border-collapse: collapse; margin: 1rem 0; }
th, td { text-align: left; vertical-align: top; padding: .3rem .8rem .3rem 0; border-bottom: 1px solid var(--line); }
th { font-weight: 600; white-space: nowrap; }
code { overflow-wrap: anywhere; }
.steps li, ul li { margin: .35rem 0; }
</style>
</head>
<body><main>
$body</main></body>
</html>
"""

    private fun esc(s: String) = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private fun url(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    private val OPENERS = setOf("IF_BLOCK", "REPEAT_BLOCK", "SHOW_MENU")
    private val MIDDLES = setOf("ELSE_BLOCK", "MENU_CASE")
    private val CLOSERS = setOf("END_IF", "END_REPEAT", "END_MENU")

    private val FLAVORS = mapOf("github" to "GitHub build", "play" to "Google Play build")

    private val RISKS = mapOf(
        "SEND_SMS" to "sends SMS",
        "CALL_PHONE" to "places calls",
        "HTTP_REQUEST" to "talks to a web server",
        "LAUNCH_SHORTCUT" to "opens an app screen",
        "SIMULATE_TAP" to "taps the screen",
        "SIMULATE_SWIPE" to "swipes the screen",
        "WRITE_FILE" to "writes files",
        "SET_WALLPAPER" to "changes the wallpaper",
    )
}
