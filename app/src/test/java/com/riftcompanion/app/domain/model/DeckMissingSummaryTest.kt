package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMissingSummaryTest {

    private val storage = setOf("red box", "blue box")
    private val deckLocs = setOf("deck a", "deck b")

    private fun entry(slug: String, name: String, zone: DeckZone, qty: Int) =
        DeckMissingSummary.EntryInput(slug, name, zone, qty)

    private fun line(slug: String, loc: String?, qty: Int) =
        DeckMissingSummary.InventoryLine(slug, loc, qty)

    @Test
    fun `single deck with all cards available has no missing`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Deck 1", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 2),
            )),
        )
        val inventory = listOf(line("card-a", "red box", 3))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertTrue(result.decks.isEmpty())
        assertEquals(0, result.totalMissingCards)
    }

    @Test
    fun `single deck with insufficient cards shows missing`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Deck 1", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 3),
            )),
        )
        val inventory = listOf(line("card-a", "red box", 1))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertEquals(1, result.decks.size)
        assertEquals(2, result.decks[0].missingCards[0].missing)
        assertEquals(2, result.totalMissingCards)
    }

    @Test
    fun `two decks share inventory - first deck claims cards first`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Alpha Deck", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 2),
            )),
            DeckMissingSummary.DeckInput("d2", "Beta Deck", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 2),
            )),
        )
        val inventory = listOf(line("card-a", "red box", 3))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        // Alpha Deck goes first (alphabetical), gets 2 of 3 → 0 missing
        // Beta Deck gets remaining 1 → 1 missing
        assertEquals(1, result.decks.size)
        assertEquals("Beta Deck", result.decks[0].deckName)
        assertEquals(1, result.decks[0].totalMissing)
    }

    @Test
    fun `built deck reserves cards at its location`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d0", "Built Deck", true, "deck a", listOf(
                entry("card-a", "Card A", DeckZone.main, 2),
            )),
            DeckMissingSummary.DeckInput("d1", "New Deck", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 2),
            )),
        )
        // 2 at deck a (reserved by built deck), 1 in storage
        val inventory = listOf(
            line("card-a", "deck a", 2),
            line("card-a", "red box", 1),
        )

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        // Built deck reserves 2 at "deck a", leaving 1 in storage for New Deck
        assertEquals(1, result.decks.size)
        assertEquals("New Deck", result.decks[0].deckName)
        assertEquals(1, result.decks[0].totalMissing)
    }

    @Test
    fun `same card in main and sideboard within one deck does not double count`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Deck 1", false, null, listOf(
                entry("elder-dragon", "Elder Dragon", DeckZone.main, 2),
                entry("elder-dragon", "Elder Dragon", DeckZone.sideboard, 1),
            )),
        )
        val inventory = listOf(line("elder-dragon", "red box", 1))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertEquals(1, result.decks.size)
        val cards = result.decks[0].missingCards
        // Aggregated: needs 3 total, has 1 → 2 missing, one row
        assertEquals(1, cards.size)
        assertEquals(3, cards[0].needed)
        assertEquals(1, cards[0].available)
        assertEquals(2, cards[0].missing)
    }

    @Test
    fun `runes and battlefields are never missing`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Deck 1", false, null, listOf(
                entry("fury-rune", "Fury Rune", DeckZone.rune, 12),
                entry("battlefield", "Battlefield", DeckZone.battlefield, 3),
            )),
        )
        val inventory = emptyList<DeckMissingSummary.InventoryLine>()

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertTrue(result.decks.isEmpty())
        assertEquals(0, result.totalMissingCards)
    }

    @Test
    fun `aggregated missing sums across decks`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Alpha", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 3),
            )),
            DeckMissingSummary.DeckInput("d2", "Beta", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 3),
            )),
        )
        val inventory = listOf(line("card-a", "red box", 2))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        // Alpha: 2 available, needs 3 → 1 missing, claims 2
        // Beta: 0 available, needs 3 → 3 missing
        assertEquals(4, result.totalMissingCards)
        assertEquals(4, result.aggregatedMissing["card-a"])
    }

    @Test
    fun `card in storage is not over-claimed by earlier deck`() {
        // Two non-built decks both need temporal-breach
        // Deck A (alphabetically first) needs 1, Deck B needs 2
        // User has 1 in storage
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Alpha Deck", false, null, listOf(
                entry("temporal-breach", "Temporal Breach", DeckZone.main, 1),
            )),
            DeckMissingSummary.DeckInput("d2", "Beta Deck", false, null, listOf(
                entry("temporal-breach", "Temporal Breach", DeckZone.main, 2),
            )),
        )
        val inventory = listOf(line("temporal-breach", "red box", 1))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        // Alpha Deck: needs 1, has 1 in storage → 0 missing (claims 1 from storage)
        // Beta Deck: needs 2, 0 left in storage → 2 missing
        assertEquals(1, result.decks.size)
        assertEquals("Beta Deck", result.decks[0].deckName)
        assertEquals(0, result.decks[0].missingCards[0].available)
        assertEquals(2, result.decks[0].missingCards[0].missing)
    }

    @Test
    fun `single deck with card in storage shows correct availability`() {
        // One non-built deck needs 2 temporal breach, user has 1 in storage
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "My Deck", false, null, listOf(
                entry("temporal-breach", "Temporal Breach", DeckZone.main, 2),
            )),
        )
        val inventory = listOf(line("temporal-breach", "red box", 1))

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertEquals(1, result.decks.size)
        val card = result.decks[0].missingCards[0]
        assertEquals(1, card.available)
        assertEquals(1, card.missing)
        assertEquals(2, card.needed)
    }

    @Test
    fun `decks with no missing cards are not included in results`() {
        val decks = listOf(
            DeckMissingSummary.DeckInput("d1", "Complete Deck", false, null, listOf(
                entry("card-a", "Card A", DeckZone.main, 1),
            )),
            DeckMissingSummary.DeckInput("d2", "Incomplete Deck", false, null, listOf(
                entry("card-b", "Card B", DeckZone.main, 5),
            )),
        )
        val inventory = listOf(
            line("card-a", "red box", 3),
            line("card-b", "red box", 1),
        )

        val result = DeckMissingSummary.compute(decks, inventory, storage, deckLocs)

        assertEquals(1, result.decks.size)
        assertEquals("Incomplete Deck", result.decks[0].deckName)
    }
}
