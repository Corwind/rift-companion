package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStatsTest {

    private fun entry(
        slug: String,
        name: String = slug,
        qty: Int = 1,
        zone: DeckZone = DeckZone.main,
        cardType: String? = "Unit",
        superType: String? = null,
        domains: List<String> = listOf("Justice"),
        energyCost: Int? = 2,
        might: Int? = 3,
        rarity: String? = "Rare",
    ) = DeckStats.EntryInfo(slug, name, qty, zone, cardType, superType, domains, energyCost, might, rarity)

    @Test
    fun `basic counts for main deck and sideboard`() {
        val stats = DeckStats.compute(listOf(
            entry("card-a", qty = 3, zone = DeckZone.main),
            entry("card-b", qty = 2, zone = DeckZone.sideboard),
            entry("legend-1", qty = 1, zone = DeckZone.legend),
            entry("rune-1", qty = 12, zone = DeckZone.rune, cardType = "Rune"),
            entry("bf-1", qty = 3, zone = DeckZone.battlefield, cardType = "Battlefield"),
        ))
        assertEquals(21, stats.totalCards)
        assertEquals(3, stats.mainDeckCount)
        assertEquals(2, stats.sideboardCount)
        assertEquals(12, stats.runeCount)
        assertEquals(3, stats.battlefieldCount)
    }

    @Test
    fun `card type breakdown only counts main deck`() {
        val stats = DeckStats.compute(listOf(
            entry("unit-1", qty = 3, cardType = "Unit"),
            entry("spell-1", qty = 2, cardType = "Spell"),
            entry("side-1", qty = 1, zone = DeckZone.sideboard, cardType = "Unit"),
        ))
        val types = stats.cardTypeBreakdown.associate { it.cardType to it.count }
        assertEquals(3, types["Unit"])
        assertEquals(2, types["Spell"])
        // Sideboard unit not counted
        assertEquals(2, types.size)
    }

    @Test
    fun `domain breakdown excludes neutral and only counts main deck`() {
        val stats = DeckStats.compute(listOf(
            entry("card-a", qty = 3, domains = listOf("Justice")),
            entry("card-b", qty = 2, domains = listOf("Corruption", "neutral")),
            entry("card-c", qty = 1, domains = listOf("neutral")),
            entry("side-1", qty = 1, zone = DeckZone.sideboard, domains = listOf("Justice")),
        ))
        val domains = stats.domainBreakdown.associate { it.domain to it.count }
        assertEquals(3, domains["Justice"])
        assertEquals(2, domains["Corruption"])
        assertNull(domains["neutral"])
        assertEquals(2, domains.size)
    }

    @Test
    fun `energy curve groups by cost`() {
        val stats = DeckStats.compute(listOf(
            entry("card-a", qty = 2, energyCost = 1),
            entry("card-b", qty = 3, energyCost = 3),
            entry("card-c", qty = 1, energyCost = 1),
            entry("card-d", qty = 2, energyCost = null), // no cost, excluded
        ))
        val curve = stats.energyCurve.associate { it.cost to it.count }
        assertEquals(3, curve[1]) // 2 + 1
        assertEquals(3, curve[3])
        assertEquals(2, curve.size)
    }

    @Test
    fun `rarity breakdown counts main deck and sideboard`() {
        val stats = DeckStats.compute(listOf(
            entry("card-a", qty = 3, rarity = "Rare", zone = DeckZone.main),
            entry("card-b", qty = 2, rarity = "Common", zone = DeckZone.main),
            entry("card-c", qty = 1, rarity = "Rare", zone = DeckZone.sideboard),
        ))
        val rarities = stats.rarityBreakdown.associate { it.rarity to it.count }
        assertEquals(4, rarities["Rare"]) // 3 main + 1 side
        assertEquals(2, rarities["Common"])
    }

    @Test
    fun `average might only counts cards with might greater than zero`() {
        val stats = DeckStats.compute(listOf(
            entry("unit-a", qty = 2, might = 4),
            entry("unit-b", qty = 3, might = 2),
            entry("spell-a", qty = 2, might = null),
            entry("unit-c", qty = 1, might = 0), // might=0 excluded
        ))
        // (4*2 + 2*3) / (2+3) = 14/5 = 2.8
        assertEquals(2.8, stats.averageMight!!, 0.001)
    }

    @Test
    fun `average might is null when no units`() {
        val stats = DeckStats.compute(listOf(
            entry("spell-a", qty = 3, might = null, cardType = "Spell"),
        ))
        assertNull(stats.averageMight)
    }

    @Test
    fun `unique card count counts main deck and sideboard distinct slugs`() {
        val stats = DeckStats.compute(listOf(
            entry("card-a", qty = 3, zone = DeckZone.main),
            entry("card-a", qty = 1, zone = DeckZone.sideboard), // same slug, not double-counted
            entry("card-b", qty = 2, zone = DeckZone.main),
            entry("rune-1", qty = 12, zone = DeckZone.rune), // not counted
        ))
        assertEquals(2, stats.uniqueCardCount)
    }

    @Test
    fun `empty deck returns zeros`() {
        val stats = DeckStats.compute(emptyList())
        assertEquals(0, stats.totalCards)
        assertEquals(0, stats.mainDeckCount)
        assertTrue(stats.cardTypeBreakdown.isEmpty())
        assertTrue(stats.domainBreakdown.isEmpty())
        assertTrue(stats.energyCurve.isEmpty())
        assertNull(stats.averageMight)
        assertEquals(0, stats.uniqueCardCount)
    }
}
