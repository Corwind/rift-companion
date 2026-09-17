package com.riftcompanion.app.domain.model

/**
 * Pure function that computes a deck build plan: which cards to move from
 * storage to the deck location (movements), which to return from the deck
 * location back to storage (returns), and which are missing.
 *
 * Extracted from DeckViewModel so it can be unit-tested without Room/Hilt.
 *
 * Location names are compared case-insensitively (trimmed + lowercased).
 */
object DeckBuildPlanner {

    data class EntryInfo(
        val nameSlug: String,
        val displayName: String,
        val quantity: Int,
        val zone: DeckZone,
        val sourceLocationName: String?, // where the card was moved from during the previous build
    )

    data class LineInfo(
        val nameSlug: String,
        val locationName: String, // raw location name (will be normalized internally)
        val quantity: Int,
    )

    data class Movement(
        val nameSlug: String,
        val displayName: String,
        val quantity: Int,
        val fromLocation: String, // display name
        val toLocation: String,   // display name
    )

    data class MissingCard(
        val nameSlug: String,
        val displayName: String,
        val needed: Int,
        val available: Int,
    )

    data class Plan(
        val movements: List<Movement>, // storage → deck location
        val returns: List<Movement>,   // deck location → storage
        val missing: List<MissingCard>,
    )

    /**
     * @param entries deck definition entries
     * @param lines all inventory lines for the cards in the deck (and at the deck location)
     * @param storageLocations normalized (trimmed+lowercased) storage location names
     * @param deckLocationName the deck location name (raw, will be normalized internally)
     * @param deckLocationDisplayName display name for the deck location
     * @param storageDisplayNames map of normalized storage location name → display name
     * @param isAlreadyBuilt whether the deck was previously built (cards may be at deck location)
     */
    fun computePlan(
        entries: List<EntryInfo>,
        lines: List<LineInfo>,
        storageLocations: Set<String>,
        deckLocationName: String,
        deckLocationDisplayName: String,
        storageDisplayNames: Map<String, String>,
        isAlreadyBuilt: Boolean,
    ): Plan {
        val deckLocNorm = deckLocationName.trim().lowercase()
        val movements = mutableListOf<Movement>()
        val returns = mutableListOf<Movement>()
        val missing = mutableListOf<MissingCard>()
        val defaultStorage = storageLocations.firstOrNull()
        val defaultStorageDisplay = defaultStorage?.let { storageDisplayNames[it] } ?: "Storage"

        val entryNameSlugs = entries.map { it.nameSlug }.toSet()

        // Lines at the deck location, grouped by nameSlug
        val linesAtDeckBySlug = lines
            .filter { it.locationName.trim().lowercase() == deckLocNorm }
            .groupBy { it.nameSlug }

        // ── Phase 1: Process each deck entry ──
        for (entry in entries) {
            val isRuneOrBattlefield = entry.zone == DeckZone.rune || entry.zone == DeckZone.battlefield
            val needed = entry.quantity

            // Cards already at the deck location
            val atDeck = linesAtDeckBySlug[entry.nameSlug]?.sumOf { it.quantity } ?: 0

            // Cards in storage
            val storageLines = lines
                .filter {
                    it.nameSlug == entry.nameSlug &&
                        it.locationName.trim().lowercase() in storageLocations
                }
                .sortedBy { it.locationName }

            if (isRuneOrBattlefield) {
                // Runes/battlefields are created at the deck location, never missing
                // If already built and excess at deck, return to source or delete
                if (isAlreadyBuilt && atDeck > needed) {
                    val returnQty = atDeck - needed
                    val returnTo = entry.sourceLocationName?.trim()?.lowercase()
                    if (returnTo != null && returnTo in storageLocations) {
                        returns.add(Movement(
                            nameSlug = entry.nameSlug,
                            displayName = entry.displayName,
                            quantity = returnQty,
                            fromLocation = deckLocationDisplayName,
                            toLocation = storageDisplayNames[returnTo] ?: returnTo,
                        ))
                    }
                    // If no source, excess runes/battlefields are just deleted (not returned)
                }
                continue
            }

            // Non-rune/battlefield card
            if (isAlreadyBuilt) {
                // Return excess to source location
                if (atDeck > needed) {
                    val returnQty = atDeck - needed
                    val returnToNorm = entry.sourceLocationName?.trim()?.lowercase()
                        ?: defaultStorage
                    if (returnToNorm != null) {
                        returns.add(Movement(
                            nameSlug = entry.nameSlug,
                            displayName = entry.displayName,
                            quantity = returnQty,
                            fromLocation = deckLocationDisplayName,
                            toLocation = storageDisplayNames[returnToNorm] ?: returnToNorm,
                        ))
                    }
                }
            }

            // Compute shortfall that needs to be moved from storage
            val shortfall = if (isAlreadyBuilt) maxOf(0, needed - atDeck) else needed
            var remaining = shortfall
            for (line in storageLines) {
                if (remaining <= 0) break
                val take = minOf(remaining, line.quantity)
                movements.add(Movement(
                    nameSlug = entry.nameSlug,
                    displayName = entry.displayName,
                    quantity = take,
                    fromLocation = line.locationName,
                    toLocation = deckLocationDisplayName,
                ))
                remaining -= take
            }

            // Compute missing
            if (remaining > 0) {
                val available = needed - remaining
                missing.add(MissingCard(
                    nameSlug = entry.nameSlug,
                    displayName = entry.displayName,
                    needed = needed,
                    available = available,
                ))
            }
        }

        // ── Phase 2: Find cards at deck location not in deck definition (removed cards) ──
        if (isAlreadyBuilt) {
            for ((nameSlug, cardLines) in linesAtDeckBySlug) {
                if (nameSlug !in entryNameSlugs) {
                    val totalQty = cardLines.sumOf { it.quantity }
                    if (totalQty > 0) {
                        // Return to default storage (we don't know the original source)
                        val returnTo = defaultStorage ?: "unlocated"
                        returns.add(Movement(
                            nameSlug = nameSlug,
                            displayName = nameSlug, // we don't have display name for removed cards
                            quantity = totalQty,
                            fromLocation = deckLocationDisplayName,
                            toLocation = storageDisplayNames[returnTo] ?: defaultStorageDisplay,
                        ))
                    }
                }
            }
        }

        return Plan(
            movements = movements,
            returns = returns,
            missing = missing,
        )
    }
}
