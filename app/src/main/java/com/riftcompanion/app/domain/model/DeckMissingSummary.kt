package com.riftcompanion.app.domain.model

/**
 * Pure function that computes missing cards across all non-built decks,
 * accounting for the fact that a single physical card instance can't be
 * used in multiple decks.
 *
 * Built decks "reserve" their cards at the deck location. Remaining inventory
 * is shared across non-built decks in priority order (alphabetical by deck name).
 *
 * Within a single deck, entries are processed in zone priority order
 * (legend → champion → main → sideboard), and cards claimed by earlier
 * zones reduce availability for later zones — same logic as DeckAvailability.
 */
object DeckMissingSummary {

    data class MissingCard(
        val nameSlug: String,
        val displayName: String,
        val needed: Int,
        val available: Int,
        val missing: Int,
    )

    data class DeckMissingInfo(
        val deckId: String,
        val deckName: String,
        val missingCards: List<MissingCard>,
        val totalMissing: Int,
    )

    data class Summary(
        val decks: List<DeckMissingInfo>,
        val totalMissingCards: Int,
        /** Aggregated across all decks: nameSlug → total missing count */
        val aggregatedMissing: Map<String, Int>,
    )

    data class DeckInput(
        val deckId: String,
        val deckName: String,
        val isBuilt: Boolean,
        val linkedLocationName: String?,
        val entries: List<EntryInput>,
    )

    data class EntryInput(
        val nameSlug: String,
        val displayName: String,
        val zone: DeckZone,
        val quantity: Int,
    )

    data class InventoryLine(
        val nameSlug: String,
        val locationName: String?,
        val quantity: Int,
    )

    /**
     * @param decks all decks (built and non-built)
     * @param inventoryLines all inventory lines
     * @param storageLocationNames set of storage location names (case-insensitive)
     * @param deckLocationNames set of deck location names (case-insensitive)
     */
    fun compute(
        decks: List<DeckInput>,
        inventoryLines: List<InventoryLine>,
        storageLocationNames: Set<String>,
        deckLocationNames: Set<String>,
    ): Summary {
        // Group inventory by slug
        val inventoryBySlug = inventoryLines.groupBy { it.nameSlug }

        // Built decks reserve cards at their linked location — per unique slug, not per entry
        val reservedBySlug = mutableMapOf<String, Int>()
        for (deck in decks.filter { it.isBuilt }) {
            // Total needed per slug for this built deck
            val neededBySlug = deck.entries
                .filter { it.zone != DeckZone.rune && it.zone != DeckZone.battlefield }
                .groupBy { it.nameSlug }
                .mapValues { (_, entryList) -> entryList.sumOf { it.quantity } }
            
            for ((nameSlug, totalNeeded) in neededBySlug) {
                val lines = inventoryBySlug[nameSlug] ?: emptyList()
                val atDeckLocation = lines
                    .filter { it.locationName?.trim()?.equals(deck.linkedLocationName?.trim(), ignoreCase = true) == true }
                    .sumOf { it.quantity }
                // Only reserve what the deck actually needs, not all cards at the location
                reservedBySlug[nameSlug] = (reservedBySlug[nameSlug] ?: 0) + minOf(atDeckLocation, totalNeeded)
            }
        }

        // Process non-built decks in alphabetical order
        val nonBuiltDecks = decks.filter { !it.isBuilt }.sortedBy { it.deckName.lowercase() }

        // Track how many of each card have been claimed from STORAGE by earlier non-built decks
        val claimedFromStorageByOtherDecks = mutableMapOf<String, Int>()
        val deckResults = mutableListOf<DeckMissingInfo>()
        val aggregated = mutableMapOf<String, Int>()

        for (deck in nonBuiltDecks) {
            val claimedFromStorageInDeck = mutableMapOf<String, Int>()
            val claimedFromDeckLocInDeck = mutableMapOf<String, Int>()
            val missingCards = mutableListOf<MissingCard>()

            // Aggregate entries by nameSlug (sum quantities across zones)
            val neededBySlug: Map<String, Pair<String, Int>> = deck.entries
                .filter { it.zone != DeckZone.rune && it.zone != DeckZone.battlefield }
                .groupBy { it.nameSlug }
                .mapValues { (_, entryList) ->
                    entryList.first().displayName to entryList.sumOf { it.quantity }
                }

            for ((nameSlug, info) in neededBySlug) {
                val displayName = info.first
                val totalNeeded = info.second

                val lines = inventoryBySlug[nameSlug] ?: emptyList()
                val inStorage = lines
                    .filter { loc -> loc.locationName != null && storageLocationNames.any { s -> loc.locationName!!.trim().equals(s, ignoreCase = true) } }
                    .sumOf { it.quantity }
                val inDeckLocation = lines
                    .filter { loc -> loc.locationName?.trim()?.equals(deck.linkedLocationName?.trim(), ignoreCase = true) == true }
                    .sumOf { it.quantity }

                val reserved = reservedBySlug[nameSlug] ?: 0
                val claimedOtherFromStorage = claimedFromStorageByOtherDecks[nameSlug] ?: 0
                val claimedStorage = claimedFromStorageInDeck[nameSlug] ?: 0
                val claimedDeckLoc = claimedFromDeckLocInDeck[nameSlug] ?: 0

                val availableInStorage = maxOf(0, inStorage - claimedOtherFromStorage - claimedStorage)
                val availableInDeckLocation = maxOf(0, inDeckLocation - reserved - claimedDeckLoc)
                val available = availableInStorage + availableInDeckLocation
                val missing = maxOf(0, totalNeeded - available)

                if (missing > 0) {
                    missingCards.add(MissingCard(
                        nameSlug = nameSlug,
                        displayName = displayName,
                        needed = totalNeeded,
                        available = available,
                        missing = missing,
                    ))
                }

                // Track how many this deck claims from each pool
                // Claim from deck location first, then storage only for the remainder
                val claimed = minOf(available, totalNeeded)
                val claimedFromDeckLoc = minOf(availableInDeckLocation, claimed)
                val claimedFromStorage = claimed - claimedFromDeckLoc
                claimedFromStorageInDeck[nameSlug] = claimedFromStorage
                claimedFromDeckLocInDeck[nameSlug] = claimedFromDeckLoc
            }

            // After processing this deck, add its storage claims to the global pool
            for ((slug, qty) in claimedFromStorageInDeck) {
                claimedFromStorageByOtherDecks[slug] = (claimedFromStorageByOtherDecks[slug] ?: 0) + qty
            }

            if (missingCards.isNotEmpty()) {
                deckResults.add(DeckMissingInfo(
                    deckId = deck.deckId,
                    deckName = deck.deckName,
                    missingCards = missingCards.sortedBy { it.displayName },
                    totalMissing = missingCards.sumOf { it.missing },
                ))
                for (card in missingCards) {
                    aggregated[card.nameSlug] = (aggregated[card.nameSlug] ?: 0) + card.missing
                }
            }
        }

        return Summary(
            decks = deckResults,
            totalMissingCards = aggregated.values.sum(),
            aggregatedMissing = aggregated,
        )
    }
}
