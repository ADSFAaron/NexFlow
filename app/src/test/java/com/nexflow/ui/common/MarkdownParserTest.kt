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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownParserTest {

    @Test
    fun `plain text is a single paragraph and reports no markup`() {
        assertFalse(MarkdownParser.hasMarkup("已建立流程,請檢查後啟用。"))
        assertEquals(
            listOf(MarkdownBlock.Paragraph("已建立流程,請檢查後啟用。")),
            MarkdownParser.parseBlocks("已建立流程,請檢查後啟用。"),
        )
    }

    @Test
    fun `headings capture level and text`() {
        assertEquals(
            listOf(
                MarkdownBlock.Heading(2, "Flow summary"),
                MarkdownBlock.Paragraph("Runs at 07:00."),
            ),
            MarkdownParser.parseBlocks("## Flow summary\nRuns at 07:00."),
        )
    }

    @Test
    fun `bullets and numbered items keep their markers and indent`() {
        val blocks = MarkdownParser.parseBlocks(
            """
            - first
              - nested
            1. one
            2) two
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.ListItem("first", ordered = false, marker = "•", indent = 0),
                MarkdownBlock.ListItem("nested", ordered = false, marker = "•", indent = 1),
                MarkdownBlock.ListItem("one", ordered = true, marker = "1.", indent = 0),
                MarkdownBlock.ListItem("two", ordered = true, marker = "2.", indent = 0),
            ),
            blocks,
        )
    }

    @Test
    fun `fenced code keeps its language and inner blank lines`() {
        val blocks = MarkdownParser.parseBlocks("```json\n{\n\n  \"a\": 1\n}\n```")
        assertEquals(listOf(MarkdownBlock.CodeBlock("{\n\n  \"a\": 1\n}", "json")), blocks)
    }

    @Test
    fun `unterminated fence still produces a code block`() {
        val blocks = MarkdownParser.parseBlocks("```\nhalf a snippet")
        assertEquals(listOf(MarkdownBlock.CodeBlock("half a snippet", null)), blocks)
    }

    @Test
    fun `quotes merge consecutive lines and rules stand alone`() {
        val blocks = MarkdownParser.parseBlocks("> one\n> two\n\n---")
        assertEquals(listOf(MarkdownBlock.Quote("one\ntwo"), MarkdownBlock.Rule), blocks)
    }

    @Test
    fun `bold italic and code spans split the run`() {
        assertEquals(
            listOf(
                MarkdownSpan("Set "),
                MarkdownSpan("volume", bold = true),
                MarkdownSpan(" to "),
                MarkdownSpan("80", code = true),
                MarkdownSpan(" "),
                MarkdownSpan("now", italic = true),
            ),
            MarkdownParser.parseInline("Set **volume** to `80` *now*"),
        )
    }

    @Test
    fun `nested emphasis keeps both styles`() {
        assertEquals(
            listOf(MarkdownSpan("both", bold = true, italic = true)),
            MarkdownParser.parseInline("**_both_**"),
        )
    }

    @Test
    fun `underscores inside identifiers are not emphasis`() {
        assertEquals(
            listOf(MarkdownSpan("use {{some_var_name}} here")),
            MarkdownParser.parseInline("use {{some_var_name}} here"),
        )
    }

    @Test
    fun `links carry their url`() {
        assertEquals(
            listOf(
                MarkdownSpan("See "),
                MarkdownSpan("docs", linkUrl = "https://example.com"),
            ),
            MarkdownParser.parseInline("See [docs](https://example.com)"),
        )
    }

    @Test
    fun `unmatched markers stay literal`() {
        assertEquals(listOf(MarkdownSpan("2 * 3 = 6")), MarkdownParser.parseInline("2 * 3 = 6"))
        assertEquals(listOf(MarkdownSpan("a `dangling")), MarkdownParser.parseInline("a `dangling"))
    }

    @Test
    fun `escaped markers are rendered literally`() {
        assertEquals(listOf(MarkdownSpan("**not bold**")), MarkdownParser.parseInline("\\*\\*not bold\\*\\*"))
    }

    @Test
    fun `hasMarkup detects the constructs the renderer handles`() {
        assertTrue(MarkdownParser.hasMarkup("**bold**"))
        assertTrue(MarkdownParser.hasMarkup("line\n- item"))
        assertTrue(MarkdownParser.hasMarkup("line\n1. item"))
        assertTrue(MarkdownParser.hasMarkup("# Title"))
        assertTrue(MarkdownParser.hasMarkup("[a](b)"))
        assertFalse(MarkdownParser.hasMarkup("just words, 2 - 1 = 1"))
    }
}
