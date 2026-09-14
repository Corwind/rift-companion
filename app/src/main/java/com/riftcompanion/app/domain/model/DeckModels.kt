package com.riftcompanion.app.domain.model

/**
 * Deck zones matching RiftBuilder's DeckZone enum.
 * Order matters for display.
 */
enum class DeckZone(val displayName: String) {
    legend("Legend"),
    chosenChampion("Champion"),
    main("Main Deck"),
    rune("Runes"),
    battlefield("Battlefields"),
    sideboard("Sideboard");

    companion object {
        fun fromString(value: String): DeckZone? =
            entries.find { it.name == value }
    }
}

/**
 * A deck definition.
 */
data class Deck(
    val id: String,
    val name: String,
    val state: String = "planned",
    val rulesetId: String = "riftbound",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A single card entry in a deck.
 */
data class DeckEntry(
    val deckId: String,
    val zone: DeckZone,
    val nameSlug: String,
    val quantity: Int,
    val preferredProductId: Long? = null,
    val preferredFinish: String? = null,
    val preferredLanguage: String? = null,
)

/**
 * A parsed text deck entry before resolution against the catalogue.
 */
data class TextDeckEntry(
    val zone: DeckZone,
    val displayName: String,
    val quantity: Int,
    val lineNumber: Int,
)

/**
 * A parsed text deck document.
 */
data class TextDeckDocument(
    val entries: List<TextDeckEntry>,
    val suggestedDeckName: String?,
)

/**
 * Result of importing a deck.
 */
data class DeckImportResult(
    val deckId: String,
    val deckName: String,
    val totalCards: Int,
    val resolvedCount: Int,
    val unresolvedNames: List<String>,
)
