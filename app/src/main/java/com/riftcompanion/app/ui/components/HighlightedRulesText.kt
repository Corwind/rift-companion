package com.riftcompanion.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Official Riftbound keyword abilities from the Core Rules Keyword Glossary
 * (riftrules.com). These are the actual game mechanic keywords that
 * appear highlighted on cards.
 *
 * Source: https://riftrules.com/?r=rule&slug=core-rules (rules 721-733)
 */
private val RIFTBOUND_KEYWORDS = setOf(
    // Keyword abilities (Core Rules v1.1, rules 721-733)
    "Accelerate",
    "Action",
    "Assault",
    "Deathknell",
    "Deflect",
    "Ganking",
    "Hidden",
    "Legion",
    "Reaction",
    "Shield",
    "Tank",
    "Temporary",
    "Vision",
)

/**
 * Renders rules text with Riftbound keywords highlighted in the accent color
 * and bold weight. Non-keyword text uses the normal body text color.
 *
 * Keywords are matched case-insensitively as whole words. Keywords with
 * parameters (e.g. "Assault X", "Shield X", "Deflect X") are matched
 * on the keyword name only; the parameter is left in normal text.
 */
@Composable
fun HighlightedRulesText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val themeState = currentThemeState()
    val accentColor = if (themeState.isDark) themeState.accent.colors.dark else themeState.accent.colors.light
    val baseColor = MaterialTheme.colorScheme.onSurface

    val annotated = buildAnnotatedRulesText(text, accentColor, baseColor)

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

private fun buildAnnotatedRulesText(
    text: String,
    keywordColor: Color,
    baseColor: Color,
): AnnotatedString {
    val keywordStyle = SpanStyle(
        color = keywordColor,
        fontWeight = FontWeight.Bold,
    )
    val baseStyle = SpanStyle(color = baseColor)

    return buildAnnotatedString {
        val regex = Regex("(\\b[A-Za-z]+\\b)")
        var lastIndex = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > lastIndex) {
                withStyle(baseStyle) {
                    append(text.substring(lastIndex, match.range.first))
                }
            }
            val word = match.value
            val isKeyword = RIFTBOUND_KEYWORDS.any { it.equals(word, ignoreCase = true) }
            if (isKeyword) {
                withStyle(keywordStyle) {
                    append(word)
                }
            } else {
                withStyle(baseStyle) {
                    append(word)
                }
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            withStyle(baseStyle) {
                append(text.substring(lastIndex))
            }
        }
    }
}
