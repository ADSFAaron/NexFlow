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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the [MarkdownParser] subset. Sizes and colors are derived from [style] and [color],
 * so the same composable reads correctly inside any bubble or surface.
 *
 * Plain text (no markup) is rendered as a single [Text] — the common case pays nothing.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = color.takeOrElse { LocalContentColor.current }

    if (!MarkdownParser.hasMarkup(markdown)) {
        Text(text = markdown, style = style, color = resolvedColor, modifier = modifier)
        return
    }

    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    // Tinted from the content color rather than the theme, so code and quotes stay legible on
    // whichever container the caller placed this in.
    val codeBackground = resolvedColor.copy(alpha = 0.08f)
    val accentColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = block.text.toAnnotated(codeBackground, accentColor),
                    style = style,
                    color = resolvedColor,
                )

                is MarkdownBlock.Heading -> Text(
                    text = block.text.toAnnotated(codeBackground, accentColor),
                    style = style.copy(
                        fontSize = style.fontSize * headingScale(block.level),
                        fontWeight = FontWeight.Bold,
                    ),
                    color = resolvedColor,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is MarkdownBlock.ListItem -> Row(
                    modifier = Modifier.padding(start = (block.indent * 16).dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = block.marker,
                        style = style,
                        color = resolvedColor,
                        modifier = Modifier.width(if (block.ordered) 22.dp else 14.dp),
                    )
                    Text(
                        text = block.text.toAnnotated(codeBackground, accentColor),
                        style = style,
                        color = resolvedColor,
                    )
                }

                is MarkdownBlock.CodeBlock -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBackground)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = block.code,
                        style = style.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = style.fontSize * 0.9f,
                        ),
                        color = resolvedColor,
                        // Code lines must not wrap mid-token; the block scrolls instead.
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }

                is MarkdownBlock.Quote -> Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Spacer(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(accentColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = block.text.toAnnotated(codeBackground, accentColor),
                        style = style.copy(fontStyle = FontStyle.Italic),
                        color = resolvedColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                MarkdownBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = resolvedColor.copy(alpha = 0.2f),
                )
            }
        }
    }
}

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.45f
    2 -> 1.28f
    3 -> 1.14f
    else -> 1f
}

private fun String.toAnnotated(codeBackground: Color, accentColor: Color): AnnotatedString =
    buildAnnotatedString {
        MarkdownParser.parseInline(this@toAnnotated).forEach { span ->
            val spanStyle = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
                textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
            )
            val url = span.linkUrl
            if (url == null) {
                withStyle(spanStyle) { append(span.text) }
            } else {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        styles = TextLinkStyles(
                            style = spanStyle.copy(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) {
                    append(span.text)
                }
            }
        }
    }
