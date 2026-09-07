package com.riftcompanion.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Standard semantic colors that adapt to dark/light theme for readability. */

// Green for "available" / "free" / "storage"
val FreeGreen = Color(0xFF43A047)
val FreeGreenDark = Color(0xFF66BB6A)

// Orange for "used" / "unavailable"
val UsedOrange = Color(0xFFFB8C00)
val UsedOrangeDark = Color(0xFFFFB74D)

/** Returns the appropriate color based on current theme darkness. */
@Composable
fun adaptiveColor(light: Color, dark: Color): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) dark else light
}

@Composable
fun freeColor(): Color = adaptiveColor(FreeGreen, FreeGreenDark)

@Composable
fun usedColor(): Color = adaptiveColor(UsedOrange, UsedOrangeDark)

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
