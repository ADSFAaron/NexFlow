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
package com.nexflow.ui.common

/**
 * The markdown subset Gemini actually emits in chat: headings, bullet/numbered lists, fenced and
 * inline code, bold/italic/strikethrough, links, block quotes and rules.
 *
 * Parsing lives here as plain data (no Compose types) so it is unit-testable on the JVM;
 * [MarkdownText] turns it into composables.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock

    /** [level] is 1..6, matching `#`..`######`. */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** One list row. [marker] is the rendered bullet or number label. */
    data class ListItem(
        val text: String,
        val ordered: Boolean,
        val marker: String,
        val indent: Int,
    ) : MarkdownBlock

    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data object Rule : MarkdownBlock
}

/** One inline run inside a block: text plus the styles that apply to it. */
data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val linkUrl: String? = null,
)

object MarkdownParser {

    /** True when [text] contains anything worth running through the renderer. */
    fun hasMarkup(text: String): Boolean = MARKUP_HINT.containsMatchIn(text)

    fun parseBlocks(source: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = source.replace("\r\n", "\n").split('\n')
        val paragraph = mutableListOf<String>()
        val quote = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        fun flushQuote() {
            if (quote.isNotEmpty()) {
                blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
                quote.clear()
            }
        }

        fun flushAll() {
            flushParagraph()
            flushQuote()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            val fence = FENCE.matchEntire(trimmed)
            if (fence != null) {
                flushAll()
                val language = fence.groupValues[2].trim().ifBlank { null }
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && FENCE.matchEntire(lines[i].trim()) == null) {
                    code += lines[i]
                    i++
                }
                // Unterminated fences are common in streamed/truncated replies — still render.
                if (i < lines.size) i++
                blocks += MarkdownBlock.CodeBlock(code.joinToString("\n").trimEnd(), language)
                continue
            }

            if (trimmed.isEmpty()) {
                flushAll()
                i++
                continue
            }

            if (RULE.matches(trimmed)) {
                flushAll()
                blocks += MarkdownBlock.Rule
                i++
                continue
            }

            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                flushAll()
                blocks += MarkdownBlock.Heading(
                    level = heading.groupValues[1].length,
                    text = heading.groupValues[2].trim(),
                )
                i++
                continue
            }

            if (trimmed.startsWith(">")) {
                flushParagraph()
                quote += trimmed.removePrefix(">").trim()
                i++
                continue
            }

            val bullet = BULLET.matchEntire(line)
            if (bullet != null) {
                flushAll()
                blocks += MarkdownBlock.ListItem(
                    text = bullet.groupValues[3].trim(),
                    ordered = false,
                    marker = "•",
                    indent = indentLevel(bullet.groupValues[1]),
                )
                i++
                continue
            }

            val numbered = NUMBERED.matchEntire(line)
            if (numbered != null) {
                flushAll()
                blocks += MarkdownBlock.ListItem(
                    text = numbered.groupValues[3].trim(),
                    ordered = true,
                    marker = "${numbered.groupValues[2]}.",
                    indent = indentLevel(numbered.groupValues[1]),
                )
                i++
                continue
            }

            flushQuote()
            paragraph += trimmed
            i++
        }
        flushAll()
        return blocks
    }

    fun parseInline(text: String): List<MarkdownSpan> {
        val out = mutableListOf<MarkdownSpan>()
        parseInto(text, MarkdownSpan(""), out)
        return out
    }

    private fun parseInto(text: String, style: MarkdownSpan, out: MutableList<MarkdownSpan>) {
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                out += style.copy(text = buffer.toString())
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                    buffer.append(text[i + 1])
                    i += 2
                }

                // Code spans win over everything: their content is literal.
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        flush()
                        out += style.copy(text = text.substring(i + 1, end), code = true)
                        i = end + 1
                    } else {
                        buffer.append(c)
                        i++
                    }
                }

                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val delimiter = text.substring(i, i + 2)
                    val end = text.indexOf(delimiter, i + 2)
                    if (end > i + 2) {
                        flush()
                        parseInto(text.substring(i + 2, end), style.copy(bold = true), out)
                        i = end + 2
                    } else {
                        buffer.append(c)
                        i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flush()
                        parseInto(text.substring(i + 2, end), style.copy(strikethrough = true), out)
                        i = end + 2
                    } else {
                        buffer.append(c)
                        i++
                    }
                }

                // `_` only opens emphasis at a word boundary, so snake_case config keys and
                // {{some_var}} references survive intact.
                (c == '*' || (c == '_' && isWordBoundary(text, i - 1))) -> {
                    val end = text.indexOf(c, i + 1)
                    if (end > i + 1) {
                        flush()
                        parseInto(text.substring(i + 1, end), style.copy(italic = true), out)
                        i = end + 1
                    } else {
                        buffer.append(c)
                        i++
                    }
                }

                c == '[' -> {
                    val link = matchLink(text, i)
                    if (link != null) {
                        flush()
                        parseInto(link.label, style.copy(linkUrl = link.url), out)
                        i = link.endExclusive
                    } else {
                        buffer.append(c)
                        i++
                    }
                }

                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
    }

    private data class Link(val label: String, val url: String, val endExclusive: Int)

    private fun matchLink(text: String, start: Int): Link? {
        val labelEnd = text.indexOf(']', start + 1).takeIf { it > start } ?: return null
        if (labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
        val urlEnd = text.indexOf(')', labelEnd + 2).takeIf { it > labelEnd + 1 } ?: return null
        val url = text.substring(labelEnd + 2, urlEnd).trim()
        if (url.isEmpty()) return null
        return Link(text.substring(start + 1, labelEnd), url, urlEnd + 1)
    }

    private fun isWordBoundary(text: String, index: Int): Boolean =
        index < 0 || !text[index].isLetterOrDigit()

    /** Two spaces per level, so both 2- and 4-space indented sub-lists step in visibly. */
    private fun indentLevel(leading: String): Int =
        (leading.replace("\t", "  ").length / 2).coerceIn(0, 3)

    private const val ESCAPABLE = "\\`*_~[]()#>-+."

    private val FENCE = Regex("""^(```|~~~)(.*)$""")
    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET = Regex("""^([ \t]*)([-*+])[ \t]+(.*)$""")
    private val NUMBERED = Regex("""^([ \t]*)(\d{1,3})[.)][ \t]+(.*)$""")

    /** Cheap pre-check: any of the constructs above appearing anywhere in the text. */
    private val MARKUP_HINT = Regex(
        """(\*\*|~~|`|^#{1,6}\s|^[ \t]*[-*+][ \t]+|^[ \t]*\d{1,3}[.)][ \t]+|^>\s|\[[^\]]+\]\([^)]+\))""",
        RegexOption.MULTILINE,
    )
}
