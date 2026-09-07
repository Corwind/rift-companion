package com.riftcompanion.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Domain colors (ported from macOS app) ──────────────────────────────
val FuryRed = Color(0xFFE53935)
val CalmGreen = Color(0xFF43A047)
val ChaosPurple = Color(0xFF8E24AA)
val OrderYellow = Color(0xFFFDD835)
val MindBlue = Color(0xFF1E88E5)
val BodyOrange = Color(0xFFFB8C00)

// ── Accent palettes (ported from macOS AppAccentPalette) ────────────────
// Each palette has a light and dark variant, matching the macOS adaptive colors
// exactly. The macOS app uses NSColor(srgbRed:green:blue:alpha:) with values
// in 0–1 range; here they are converted to 8-bit sRGB (value × 255, rounded).

data class AccentPaletteColors(
    val light: Color,
    val dark: Color,
)

enum class AppAccentPalette(val title: String, val colors: AccentPaletteColors) {
    RiftBlue(
        "Rift Blue",
        // macOS: light (0.05, 0.32, 0.72)  dark (0.25, 0.56, 1.00)
        AccentPaletteColors(
            light = Color(0xFF0D52B8),
            dark = Color(0xFF408FFF),
        ),
    ),
    ArcanePurple(
        "Arcane Purple",
        // macOS: light (0.39, 0.18, 0.68)  dark (0.66, 0.42, 0.96)
        AccentPaletteColors(
            light = Color(0xFF632EAD),
            dark = Color(0xFFA86BF5),
        ),
    ),
    Ember(
        "Ember",
        // macOS: light (0.74, 0.22, 0.05)  dark (1.00, 0.39, 0.20)
        AccentPaletteColors(
            light = Color(0xFFBD380D),
            dark = Color(0xFFFF6333),
        ),
    ),
    Verdant(
        "Verdant",
        // macOS: light (0.06, 0.43, 0.23)  dark (0.22, 0.72, 0.44)
        AccentPaletteColors(
            light = Color(0xFF0F6E3B),
            dark = Color(0xFF38B870),
        ),
    ),
    RadiantYellow(
        "Radiant Yellow",
        // macOS: light (0.65, 0.41, 0.00)  dark (1.00, 0.76, 0.14)
        AccentPaletteColors(
            light = Color(0xFFA66900),
            dark = Color(0xFFFFC224),
        ),
    ),
    CrimsonRed(
        "Crimson Red",
        // macOS: light (0.66, 0.05, 0.10)  dark (0.96, 0.22, 0.28)
        AccentPaletteColors(
            light = Color(0xFFA80D1A),
            dark = Color(0xFFF53847),
        ),
    ),
    Monochrome(
        "Monochrome",
        // macOS uses Color.primary: black in light mode, white in dark mode
        AccentPaletteColors(
            light = Color(0xFF000000),
            dark = Color(0xFFFFFFFF),
        ),
    );

    val supportsCombination: Boolean get() = this != Monochrome

    companion object {
        val allEntries get() = entries.toList()
    }
}

enum class AppAppearance(val title: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

fun domainColor(domain: String): Color {
    return when (domain.lowercase()) {
        "fury" -> FuryRed
        "calm" -> CalmGreen
        "chaos" -> ChaosPurple
        "order" -> OrderYellow
        "mind" -> MindBlue
        "body" -> BodyOrange
        else -> MindBlue
    }
}
