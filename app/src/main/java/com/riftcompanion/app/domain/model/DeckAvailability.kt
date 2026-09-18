package com.riftcompanion.app.domain.model

/**
 * Pure helper for computing deck card availability.
 *
 * Extracted from DeckViewModel so it can be unit-tested without Room/Hilt.
 *
 * A card is "missing" if the quantity in the deck definition exceeds what is
 * available. Available cards are those in storage locations PLUS those already
 * at the deck's linked location. Runes and battlefields are never missing by
 * definition (they are created at the deck location when building).
 *
 * Location matching is case-insensitive: the deck's linkedLocationName may
 * come from different sources (create-from-location uses the exact API name,
 * build uses a generated name), and inventory line location names may differ
 * in casing from the API. Case-insensitive comparison here is MATCHING only,
 * not identity — distinct locations still have distinct names. The original
 * (non-lowercased) names are always used when passing to the API.
 */
object DeckAvailability {

    data class Result(
        val availableInStorage: Int,
        val inDeckLocation: Int,
        val inOtherDecks: Int,
        val totalOwned: Int,
        val isMissing: Boolean,
        val missingCount: Int,
    )

    /**
     * @param quantity quantity of this card in the deck definition
     * @param zone the deck zone this card belongs to
     * @param lineLocations location names of all inventory lines for this card
     * @param lineQuantities quantities of each inventory line (parallel to [lineLocations])
     * @param storageLocations names of storage-type locations
     * @param deckLocations names of all deck-type locations
     * @param linkedLocation the deck's linked location name (may be null)
     */
    fun compute(
        quantity: Int,
        zone: DeckZone,
        lineLocations: List<String?>,
        lineQuantities: List<Int>,
        storageLocations: Set<String>,
        deckLocations: Set<String>,
        linkedLocation: String?,
    ): Result {
        require(lineLocations.size == lineQuantities.size) {
            "lineLocations and lineQuantities must be parallel"
        }

        val linkedTrimmed = linkedLocation?.trim()

        fun String?.matchesAny(names: Set<String>): Boolean =
            this != null && names.any { other -> this.trim().equals(other, ignoreCase = true) }

        fun String?.matches(name: String?): Boolean =
            this != null && name != null && this.trim().equals(name, ignoreCase = true)

        val inStorage = lineLocations.zip(lineQuantities)
            .filter { (loc, _) -> loc.matchesAny(storageLocations) }
            .sumOf { it.second }

        val inDeckLocation = lineLocations.zip(lineQuantities)
            .filter { (loc, _) -> loc.matches(linkedTrimmed) }
            .sumOf { it.second }

        val inDecks = lineLocations.zip(lineQuantities)
            .filter { (loc, _) ->
                loc.matchesAny(deckLocations) && !loc.matches(linkedTrimmed)
            }
            .sumOf { it.second }

        val total = lineQuantities.sum()

        val isRuneOrBattlefield = zone == DeckZone.rune || zone == DeckZone.battlefield
        val availableTotal = if (isRuneOrBattlefield) quantity else inStorage + inDeckLocation
        val missing = if (isRuneOrBattlefield) 0 else maxOf(0, quantity - availableTotal)

        return Result(
            availableInStorage = if (isRuneOrBattlefield) quantity else inStorage,
            inDeckLocation = if (isRuneOrBattlefield) quantity else inDeckLocation,
            inOtherDecks = if (isRuneOrBattlefield) 0 else inDecks,
            totalOwned = if (isRuneOrBattlefield) quantity else total,
            isMissing = missing > 0,
            missingCount = missing,
        )
    }
}
