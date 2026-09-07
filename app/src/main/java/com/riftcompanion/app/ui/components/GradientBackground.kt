package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Applies the app's accent gradient as a bold, visible background.
 * The gradient runs diagonally (top-leading to bottom-trailing).
 * Ported from the macOS ThemeTintedSurface concept.
 */
@Composable
fun Modifier.gradientBackground(): Modifier {
    val themeState = currentThemeState()
    val gradientBrush = Brush.linearGradient(themeState.gradientColors)

    return this
        .background(MaterialTheme.colorScheme.background)
        .background(gradientBrush, alpha = if (themeState.isDark) 0.35f else 0.22f)
}
