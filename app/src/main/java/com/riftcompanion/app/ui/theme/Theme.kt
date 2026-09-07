package com.riftcompanion.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Theme state exposed via CompositionLocal ────────────────────────────

data class ThemeState(
    val appearance: AppAppearance = AppAppearance.System,
    val accent: AppAccentPalette = AppAccentPalette.RiftBlue,
    val secondaryAccent: AppAccentPalette? = null,
    val backgroundTransparency: Float = 0f,
) {
    val isDark: Boolean
        @Composable get() = when (appearance) {
            AppAppearance.System -> isSystemInDarkTheme()
            AppAppearance.Light -> false
            AppAppearance.Dark -> true
        }

    val accentColor: Color
        @Composable get() = if (isDark) accent.colors.dark else accent.colors.light

    val secondaryAccentColor: Color?
        @Composable get() = secondaryAccent?.let { if (isDark) it.colors.dark else it.colors.light }

    val gradientColors: List<Color>
        @Composable get() = buildList {
            add(accentColor)
            val secondary = secondaryAccentColor
            if (secondary != null) {
                add(secondary)
            } else {
                add(accentColor)
            }
        }

    val gradient: Brush
        @Composable get() = Brush.linearGradient(
            colors = gradientColors,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(Float.MAX_VALUE, Float.MAX_VALUE),
        )
}

val LocalThemeState = compositionLocalOf { ThemeState() }

/**
 * Computes whether a color is light enough to need dark text on top.
 */
private fun Color.isLight(): Boolean {
    val r = red
    val g = green
    val b = blue
    // Perceived luminance (ITU-R BT.601)
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return luminance > 0.6f
}

@Composable
fun RiftCompanionTheme(
    themeState: ThemeState,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeState.appearance) {
        AppAppearance.System -> isSystemInDarkTheme()
        AppAppearance.Light -> false
        AppAppearance.Dark -> true
    }

    val accent = if (isDark) themeState.accent.colors.dark else themeState.accent.colors.light
    val secondary = themeState.secondaryAccent?.let { if (isDark) it.colors.dark else it.colors.light }

    // Choose onPrimary based on accent luminance so text is always readable
    val onPrimary = if (accent.isLight()) Color.Black else Color.White
    val onSecondary = if ((secondary ?: accent).isLight()) Color.Black else Color.White

    // Always use our own color scheme — not dynamic color — so the app's
    // accent palette is respected and text contrast is guaranteed.
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onPrimary,
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFEEEEEE),
            secondary = secondary ?: accent.copy(alpha = 0.8f),
            onSecondary = onSecondary,
            tertiary = secondary ?: accent.copy(alpha = 0.6f),
            background = Color(0xFF0F0E13),
            onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF1A1920),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF252330),
            onSurfaceVariant = Color(0xFFE0E0E0),
            surfaceTint = accent,
            outline = Color(0xFFB0B0B0),
            outlineVariant = Color(0xFF6A6A6A),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onPrimary,
            primaryContainer = accent.copy(alpha = 0.12f),
            onPrimaryContainer = Color(0xFF1A1A1A),
            secondary = secondary ?: accent.copy(alpha = 0.8f),
            onSecondary = onSecondary,
            tertiary = secondary ?: accent.copy(alpha = 0.6f),
            background = Color(0xFFF7F5FA),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF0EDF5),
            onSurfaceVariant = Color(0xFF333333),
            surfaceTint = accent,
            outline = Color(0xFF555555),
            outlineVariant = Color(0xFFAAAAAA),
        )
    }

    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RiftTypography,
            content = content,
        )
    }
}

@Composable
fun currentThemeState(): ThemeState = LocalThemeState.current
