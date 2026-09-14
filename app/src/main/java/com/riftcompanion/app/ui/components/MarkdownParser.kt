package com.riftcompanion.app.ui.components

import com.riftcompanion.app.domain.model.RuleBlock
import com.riftcompanion.app.domain.model.TextSegment

/**
 * Parses raw markdown text (from LLM responses or other sources) into
 * structured [RuleBlock]s that can be rendered by [RuleBlocks].
 *
 * Supports: paragraphs, bold (**text**), italic (*text*), bullet lists (- text),
 * numbered lists (1. text), and headings (### text).
 */
fun parseMarkdownToBlocks(markdown: String): List<RuleBlock> {
    val blocks = mutableListOf<RuleBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trim()

        if (line.isEmpty()) {
            i++
            continue
        }

        // Heading (### or ##)
        val headingMatch = Regex("""^(#{2,3})\s+(.+)$""").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(RuleBlock.Heading(headingMatch.groupValues[2]))
            i++
            continue
        }

        // Bullet list (- text or * text)
        if (line.startsWith("- ") || (line.startsWith("* ") && !line.startsWith("**"))) {
            val items = mutableListOf<List<TextSegment>>()
            while (i < lines.size) {
                val l = lines[i].trim()
                if (l.startsWith("- ") || (l.startsWith("* ") && !l.startsWith("**"))) {
                    items.add(parseInlineSegments(l.removePrefix("- ").removePrefix("* ")))
                    i++
                } else break
            }
            blocks.add(RuleBlock.Bullets(items))
            continue
        }

        // Numbered list (1. text)
        if (Regex("""^\d+\.\s+(.+)""").matches(line)) {
            val items = mutableListOf<List<TextSegment>>()
            while (i < lines.size && Regex("""^\d+\.\s+(.+)""").matches(lines[i].trim())) {
                items.add(parseInlineSegments(Regex("""^\d+\.\s+(.+)""").find(lines[i].trim())!!.groupValues[1]))
                i++
            }
            blocks.add(RuleBlock.Numbered(items))
            continue
        }

        // Regular paragraph (collect consecutive non-empty, non-special lines)
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i].trim()
            if (l.isEmpty() || l.startsWith("- ") || l.startsWith("* ") && !l.startsWith("**") ||
                l.startsWith("#") || Regex("""^\d+\.\s+""").matches(l)
            ) break
            paraLines.add(l)
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(RuleBlock.Paragraph(parseInlineSegments(paraLines.joinToString(" "))))
        }
    }

    return blocks
}

/**
 * Parses inline markdown (**bold**, *italic*) into [TextSegment]s.
 */
private fun parseInlineSegments(text: String): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    var i = 0
    var plain = StringBuilder()

    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end >= 0) {
                    if (plain.isNotEmpty()) {
                        segments.add(TextSegment("text", plain.toString()))
                        plain = StringBuilder()
                    }
                    segments.add(TextSegment("bold", text.substring(i + 2, end)))
                    i = end + 2
                } else {
                    plain.append(text[i])
                    i++
                }
            }
            text[i] == '*' && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end >= 0 && end != i + 1) {
                    if (plain.isNotEmpty()) {
                        segments.add(TextSegment("text", plain.toString()))
                        plain = StringBuilder()
                    }
                    segments.add(TextSegment("italic", text.substring(i + 1, end)))
                    i = end + 1
                } else {
                    plain.append(text[i])
                    i++
                }
            }
            else -> {
                plain.append(text[i])
                i++
            }
        }
    }
    if (plain.isNotEmpty()) {
        segments.add(TextSegment("text", plain.toString()))
    }
    return segments
}
