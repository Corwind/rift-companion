package com.riftcompanion.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
            secondaryAccentColor?.let { add(it) }
        }

    val gradient: Brush
        @Composable get() = Brush.linearGradient(
            colors = gradientColors,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(Float.MAX_VALUE, Float.MAX_VALUE),
        )
}

val LocalThemeState = compositionLocalOf { ThemeState() }

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

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            primaryContainer = accent.copy(alpha = 0.25f),
            onPrimaryContainer = accent,
            secondary = secondary ?: accent.copy(alpha = 0.8f),
            tertiary = secondary ?: accent.copy(alpha = 0.6f),
            background = Color(0xFF0F0E13),
            surface = Color(0xFF1A1920),
            surfaceVariant = Color(0xFF252330),
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.12f),
            onPrimaryContainer = accent,
            secondary = secondary ?: accent.copy(alpha = 0.8f),
            tertiary = secondary ?: accent.copy(alpha = 0.6f),
            background = Color(0xFFF7F5FA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0EDF5),
        )
    }

    // Override primary with our accent to ensure consistency
    val finalColorScheme = colorScheme.copy(
        primary = accent,
        secondary = secondary ?: accent.copy(alpha = 0.8f),
    )

    CompositionLocalProvider(LocalThemeState provides themeState) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = RiftTypography,
            content = content,
        )
    }
}

@Composable
fun currentThemeState(): ThemeState = LocalThemeState.current
