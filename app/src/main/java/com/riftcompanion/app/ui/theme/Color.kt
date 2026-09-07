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
// Each palette has a light and dark variant, matching the macOS adaptive colors.

data class AccentPaletteColors(
    val light: Color,
    val dark: Color,
)

enum class AppAccentPalette(val title: String, val colors: AccentPaletteColors) {
    RiftBlue(
        "Rift Blue",
        AccentPaletteColors(
            light = Color(0xFF0E52B8), // (0.05, 0.32, 0.72)
            dark = Color(0xFF4090FF),  // (0.25, 0.56, 1.00)
        ),
    ),
    ArcanePurple(
        "Arcane Purple",
        AccentPaletteColors(
            light = Color(0xFF642EAE), // (0.39, 0.18, 0.68)
            dark = Color(0xFFA86BF5),  // (0.66, 0.42, 0.96)
        ),
    ),
    Ember(
        "Ember",
        AccentPaletteColors(
            light = Color(0xFFBD380D), // (0.74, 0.22, 0.05)
            dark = Color(0xFFFF6333),  // (1.00, 0.39, 0.20)
        ),
    ),
    Verdant(
        "Verdant",
        AccentPaletteColors(
            light = Color(0xFF0F6E3B), // (0.06, 0.43, 0.23)
            dark = Color(0xFF38B870),  // (0.22, 0.72, 0.44)
        ),
    ),
    RadiantYellow(
        "Radiant Yellow",
        AccentPaletteColors(
            light = Color(0xFFA66900), // (0.65, 0.41, 0.00)
            dark = Color(0xFFFFC224),  // (1.00, 0.76, 0.14)
        ),
    ),
    CrimsonRed(
        "Crimson Red",
        AccentPaletteColors(
            light = Color(0xFFA80D1A), // (0.66, 0.05, 0.10)
            dark = Color(0xFFF53847),  // (0.96, 0.22, 0.28)
        ),
    ),
    Monochrome(
        "Monochrome",
        AccentPaletteColors(
            light = Color(0xFF1C1B1F),
            dark = Color(0xFFE6E1E5),
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
