package com.riftcompanion.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.domain.model.RuleBlock
import com.riftcompanion.app.domain.model.TextSegment

/**
 * Renders structured rule blocks into native Compose UI.
 * Bold text uses the theme's primary color for emphasis.
 */
@Composable
fun RuleBlocks(
    blocks: List<RuleBlock>,
    modifier: Modifier = Modifier,
    keywords: Map<String, String> = emptyMap(),
    sectionIndex: Map<String, String> = emptyMap(),
    onKeywordClick: (String) -> Unit = {},
    onSectionRefClick: (String) -> Unit = {},
) {
    val boldColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is RuleBlock.Paragraph -> Text(
                    text = renderSegments(block.segments, boldColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                is RuleBlock.Heading -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )

                is RuleBlock.Bullets -> {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        for (item in block.items) {
                            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                Text(
                                    "•  ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = renderSegments(item, boldColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                is RuleBlock.Numbered -> {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        for ((index, item) in block.items.withIndex()) {
                            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                Text(
                                    "${index + 1}.  ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = renderSegments(item, boldColor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                is RuleBlock.Table -> RuleTable(block, boldColor)
            }
        }
    }
}

@Composable
private fun RuleTable(table: RuleBlock.Table, boldColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            for ((i, cell) in table.headers.withIndex()) {
                Text(
                    text = renderInlineMarkdown(cell, boldColor),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(if (i == 0) 1.2f else 2f)
                        .padding(end = 8.dp),
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        for (row in table.rows) {
            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                for ((i, cell) in row.withIndex()) {
                    Text(
                        text = renderInlineMarkdown(cell, boldColor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(if (i == 0) 1.2f else 2f)
                            .padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders typed [TextSegment]s into an [AnnotatedString].
 * Bold segments use the theme's primary color.
 */
private fun renderSegments(segments: List<TextSegment>, boldColor: Color): AnnotatedString =
    buildAnnotatedString {
        for (seg in segments) {
            when (seg.type) {
                "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                    append(seg.text)
                }
                "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(seg.text)
                }
                else -> append(seg.text)
            }
        }
    }

/**
 * Parses inline markdown (bold/italic) from a raw string into an [AnnotatedString].
 * Bold text uses the theme's primary color.
 * Used for table cells which store raw markdown text.
 */
private fun renderInlineMarkdown(text: String, boldColor: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end >= 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' && !text.startsWith("**", i) -> {
                    val end = text.indexOf('*', i + 1)
                    if (end >= 0 && end != i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
