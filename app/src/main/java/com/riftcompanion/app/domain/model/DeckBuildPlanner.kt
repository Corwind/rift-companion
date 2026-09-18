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

        // Total quantity needed per card across all zones (to avoid double-counting)
        val totalNeededBySlug = entries.groupBy { it.nameSlug }
            .mapValues { (_, entryList) -> entryList.sumOf { it.quantity } }

        // Track how many cards at deck location have been "consumed" by entries
        val atDeckConsumed = mutableMapOf<String, Int>()

        // ── Phase 1: Process each deck entry (movements + missing only) ──
        for (entry in entries) {
            val isRuneOrBattlefield = entry.zone == DeckZone.rune || entry.zone == DeckZone.battlefield
            val needed = entry.quantity

            // Total cards of this slug at the deck location
            val totalAtDeck = linesAtDeckBySlug[entry.nameSlug]?.sumOf { it.quantity } ?: 0
            // How many at deck have already been consumed by previous entries
            val consumed = atDeckConsumed[entry.nameSlug] ?: 0
            // Available at deck for this entry: remaining after previous entries consumed their share
            val atDeck = minOf(needed, maxOf(0, totalAtDeck - consumed))
            atDeckConsumed[entry.nameSlug] = consumed + atDeck

            // Cards in storage
            val storageLines = lines
                .filter {
                    it.nameSlug == entry.nameSlug &&
                        it.locationName.trim().lowercase() in storageLocations
                }
                .sortedBy { it.locationName }

            if (isRuneOrBattlefield) {
                // Runes/battlefields are created at the deck location, never missing
                continue
            }

            // Compute shortfall that needs to be moved from storage
            // Always count cards already at the deck location, even if deck state
            // is "planned" (e.g. deck was built, edited, state reverted, but cards
            // are still physically at the deck location).
            val shortfall = maxOf(0, needed - atDeck)
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

        // ── Phase 1.5: Compute returns per card slug (excess at deck location) ──
        // Returns are computed per slug, not per entry, to avoid double-counting
        // when a card appears in multiple zones (e.g. main + sideboard).
        // Only compute returns when the deck was previously built — if the deck
        // was imported from a location, extra cards at that location are just
        // other cards stored there, not excess from a previous build.
        if (isAlreadyBuilt) {
        for (nameSlug in entryNameSlugs) {
            val totalAtDeck = linesAtDeckBySlug[nameSlug]?.sumOf { it.quantity } ?: 0
            val totalNeeded = totalNeededBySlug[nameSlug] ?: 0
            val excess = totalAtDeck - totalNeeded
            if (excess <= 0) continue

            // Find the source location for this card (from any entry)
            val sourceEntry = entries.find { it.nameSlug == nameSlug }
            val isRuneOrBattlefield = sourceEntry?.zone == DeckZone.rune || sourceEntry?.zone == DeckZone.battlefield
            val returnToNorm = sourceEntry?.sourceLocationName?.trim()?.lowercase()
                ?: defaultStorage

            if (isRuneOrBattlefield) {
                // For runes/battlefields: only return to the original source location
                val returnToNorm = sourceEntry?.sourceLocationName?.trim()?.lowercase()
                if (returnToNorm != null && returnToNorm in storageLocations) {
                    returns.add(Movement(
                        nameSlug = nameSlug,
                        displayName = sourceEntry?.displayName ?: nameSlug,
                        quantity = excess,
                        fromLocation = deckLocationDisplayName,
                        toLocation = storageDisplayNames[returnToNorm] ?: returnToNorm,
                    ))
                }
                // If no source or source is not a storage location, excess is deleted (not returned)
            } else {
                if (returnToNorm != null) {
                    returns.add(Movement(
                        nameSlug = nameSlug,
                        displayName = sourceEntry?.displayName ?: nameSlug,
                        quantity = excess,
                        fromLocation = deckLocationDisplayName,
                        toLocation = storageDisplayNames[returnToNorm] ?: returnToNorm,
                    ))
                }
            }
        }
        }

        // ── Phase 2: Find cards at deck location not in deck definition (removed cards) ──
        // Only return removed cards when the deck was previously built (assembled),
        // not when imported from a location (other cards may be stored there).
        if (isAlreadyBuilt) {
            for ((nameSlug, cardLines) in linesAtDeckBySlug) {
                if (nameSlug !in entryNameSlugs) {
                    val totalQty = cardLines.sumOf { it.quantity }
                    if (totalQty > 0) {
                        val returnTo = defaultStorage ?: "unlocated"
                        returns.add(Movement(
                            nameSlug = nameSlug,
                            displayName = nameSlug,
                            quantity = totalQty,
                            fromLocation = deckLocationDisplayName,
                            toLocation = storageDisplayNames[returnTo] ?: defaultStorageDisplay,
                        ))
                    }
                }
            }
        }

        // Merge movements by (nameSlug, fromLocation, toLocation) so the same card
        // from the same source shows as one movement with the total quantity
        val mergedMovements = movements
            .groupBy { Triple(it.nameSlug, it.fromLocation, it.toLocation) }
            .map { (_, group) ->
                group.first().copy(quantity = group.sumOf { it.quantity })
            }

        val mergedReturns = returns
            .groupBy { Triple(it.nameSlug, it.fromLocation, it.toLocation) }
            .map { (_, group) ->
                group.first().copy(quantity = group.sumOf { it.quantity })
            }

        return Plan(
            movements = mergedMovements,
            returns = mergedReturns,
            missing = missing,
        )
    }
}
