package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Applies the app's accent gradient as a subtle background.
 * The gradient runs diagonally (top-leading to bottom-trailing) at low
 * opacity so it tints the surface without hurting text readability.
 *
 * Ported from the macOS ThemeTintedSurface concept.
 */
@Composable
fun Modifier.gradientBackground(): Modifier {
    val themeState = currentThemeState()
    val gradientBrush = Brush.linearGradient(themeState.gradientColors)
    val tintAlpha = if (themeState.isDark) 0.12f else 0.06f

    return this
        .background(MaterialTheme.colorScheme.background)
        .background(gradientBrush, alpha = tintAlpha)
}
