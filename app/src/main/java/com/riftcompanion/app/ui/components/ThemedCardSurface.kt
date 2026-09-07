package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Themed card surface with gradient tint, ported from the macOS ThemedCardSurface.
 * Uses the accent gradient at low opacity for a subtle frosted-glass feel.
 */
@Composable
fun ThemedCardSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 13,
    tintStrength: Float = 0.08f,
    content: @Composable () -> Unit,
) {
    val themeState = currentThemeState()
    val gradientBrush = Brush.linearGradient(themeState.gradientColors)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(gradientBrush, alpha = tintStrength),
    ) {
        content()
    }
}
