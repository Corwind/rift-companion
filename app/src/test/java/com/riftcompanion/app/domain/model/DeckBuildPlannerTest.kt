package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for DeckBuildPlanner — the pure function that computes
 * movements (storage → deck), returns (deck → storage), and missing cards.
 */
class DeckBuildPlannerTest {

    private val storage = setOf("red box", "grey box")
    private val storageDisplay = mapOf(
        "red box" to "Red Box",
        "grey box" to "Grey Box",
    )
    private val deckLoc = "vi deck"
    private val deckDisplay = "Vi Deck"

    private fun entry(
        slug: String,
        name: String = slug,
        qty: Int = 1,
        zone: DeckZone = DeckZone.main,
        source: String? = null,
    ) = DeckBuildPlanner.EntryInfo(slug, name, qty, zone, source)

    private fun line(slug: String, loc: String, qty: Int) =
        DeckBuildPlanner.LineInfo(slug, loc, qty)

    // ── Never-built deck ──

    @Test
    fun `never-built deck with cards already at deck location counts them as available`() {
        // Deck state is "planned" but cards are physically at the deck location
        // (e.g. deck was built, edited, state reverted, but cards not moved back)
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3)),
            lines = listOf(line("card-a", "Vi Deck", 3)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `never-built deck moves all cards from storage`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3)),
            lines = listOf(line("card-a", "Red Box", 3)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertEquals(1, plan.movements.size)
        assertEquals(3, plan.movements[0].quantity)
        assertEquals("Red Box", plan.movements[0].fromLocation)
        assertEquals("Vi Deck", plan.movements[0].toLocation)
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `never-built deck with missing cards`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 4)),
            lines = listOf(line("card-a", "Red Box", 2)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertEquals(1, plan.movements.size)
        assertEquals(2, plan.movements[0].quantity)
        assertEquals(1, plan.missing.size)
        assertEquals(4, plan.missing[0].needed)
        assertEquals(2, plan.missing[0].available)
    }

    @Test
    fun `never-built deck with no inventory is fully missing`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3)),
            lines = emptyList(),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertTrue(plan.movements.isEmpty())
        assertEquals(1, plan.missing.size)
        assertEquals(3, plan.missing[0].needed)
        assertEquals(0, plan.missing[0].available)
    }

    // ── Already-built deck, no changes ──

    @Test
    fun `built deck with no changes has no movements or returns`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3, source = "Red Box")),
            lines = listOf(line("card-a", "Vi Deck", 3)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    // ── Built deck + added card ──

    @Test
    fun `built deck with added card moves new card from storage`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(
                entry("card-a", qty = 3, source = "Red Box"),
                entry("card-b", qty = 2, source = null),
            ),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-b", "Red Box", 2),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        // card-a already at deck, no movement
        // card-b needs to be moved from storage
        assertEquals(1, plan.movements.size)
        assertEquals("card-b", plan.movements[0].nameSlug)
        assertEquals(2, plan.movements[0].quantity)
        assertEquals("Red Box", plan.movements[0].fromLocation)
        assertTrue(plan.returns.isEmpty())
    }

    // ── Built deck + removed card ──

    @Test
    fun `built deck with removed card returns it to storage`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3, source = "Red Box")),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-b", "Vi Deck", 2), // card-b was removed from deck definition
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertTrue(plan.movements.isEmpty())
        assertEquals(1, plan.returns.size)
        assertEquals("card-b", plan.returns[0].nameSlug)
        assertEquals(2, plan.returns[0].quantity)
        assertEquals("Vi Deck", plan.returns[0].fromLocation)
        // Returns to default storage (first in set)
        assertTrue(plan.returns[0].toLocation in listOf("Red Box", "Grey Box"))
    }

    // ── Built deck + reduced quantity ──

    @Test
    fun `built deck with reduced quantity returns excess to source`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 2, source = "Red Box")),
            lines = listOf(line("card-a", "Vi Deck", 5)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertTrue(plan.movements.isEmpty())
        assertEquals(1, plan.returns.size)
        assertEquals(3, plan.returns[0].quantity) // 5 - 2 = 3 excess
        assertEquals("Vi Deck", plan.returns[0].fromLocation)
        assertEquals("Red Box", plan.returns[0].toLocation) // source location
    }

    // ── Built deck + increased quantity ──

    @Test
    fun `built deck with increased quantity moves shortfall from storage`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 5, source = "Red Box")),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-a", "Red Box", 2),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertEquals(1, plan.movements.size)
        assertEquals(2, plan.movements[0].quantity) // 5 - 3 = 2 shortfall
        assertEquals("Red Box", plan.movements[0].fromLocation)
        assertTrue(plan.returns.isEmpty())
    }

    // ── Built deck + complex changes ──

    @Test
    fun `built deck with added removed and increased cards`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(
                entry("card-a", qty = 5, source = "Red Box"),   // increased from 3 to 5
                entry("card-c", qty = 2, source = null),          // added new
            ),
            lines = listOf(
                line("card-a", "Vi Deck", 3),  // 3 at deck, need 5 → move 2
                line("card-a", "Grey Box", 2), // available in storage
                line("card-b", "Vi Deck", 4),  // removed from deck → return 4
                line("card-c", "Red Box", 2), // new card in storage
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        // Movements: card-a (2 from Grey Box), card-c (2 from Red Box)
        assertEquals(2, plan.movements.size)
        val moveA = plan.movements.find { it.nameSlug == "card-a" }!!
        assertEquals(2, moveA.quantity)
        val moveC = plan.movements.find { it.nameSlug == "card-c" }!!
        assertEquals(2, moveC.quantity)

        // Returns: card-b (4 from Vi Deck to Red Box)
        assertEquals(1, plan.returns.size)
        assertEquals("card-b", plan.returns[0].nameSlug)
        assertEquals(4, plan.returns[0].quantity)
        assertEquals("Vi Deck", plan.returns[0].fromLocation)

        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `planned deck with cards at deck location and shortfall in storage`() {
        // Deck was built with 3, edited to need 5, state reverted to planned
        // 3 at deck + 2 in storage = 5, no missing
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 5, source = "Red Box")),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-a", "Red Box", 2),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertEquals(1, plan.movements.size)
        assertEquals(2, plan.movements[0].quantity)
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `planned deck with cards at deck location and insufficient storage shows missing`() {
        // 3 at deck + 1 in storage = 4, need 5 → 1 missing
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 5, source = "Red Box")),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-a", "Red Box", 1),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertEquals(1, plan.movements.size)
        assertEquals(1, plan.movements[0].quantity)
        assertEquals(1, plan.missing.size)
        assertEquals(5, plan.missing[0].needed)
        assertEquals(4, plan.missing[0].available)
    }

    @Test
    fun `planned deck with removed card at deck location does not return it`() {
        // card-b was removed from deck definition but is still at deck location.
        // Since deck is not built (imported from location), don't return it —
        // it might be another card stored there.
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3)),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-b", "Vi Deck", 2),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `planned deck with reduced quantity returns excess even when not marked built`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 2, source = "Red Box")),
            lines = listOf(line("card-a", "Vi Deck", 5)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertTrue(plan.movements.isEmpty())
        assertEquals(1, plan.returns.size)
        assertEquals(3, plan.returns[0].quantity)
    }

    // ── Runes and battlefields ──

    @Test
    fun `runes are never missing and never moved from storage`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("rune-1", qty = 12, zone = DeckZone.rune)),
            lines = emptyList(),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `built runes with excess are returned to source if available`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("rune-1", qty = 6, zone = DeckZone.rune, source = "Red Box")),
            lines = listOf(line("rune-1", "Vi Deck", 12)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertTrue(plan.movements.isEmpty())
        assertEquals(1, plan.returns.size)
        assertEquals(6, plan.returns[0].quantity)
        assertEquals("Red Box", plan.returns[0].toLocation)
    }

    @Test
    fun `built runes with excess and no source are not returned`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("rune-1", qty = 6, zone = DeckZone.rune, source = null)),
            lines = listOf(line("rune-1", "Vi Deck", 12)),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty()) // no source → excess is deleted, not returned
    }

    // ── Case-insensitive location matching ──

    @Test
    fun `location matching is case-insensitive`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3, source = "Red Box")),
            lines = listOf(line("card-a", "VI DECK", 3)), // different casing
            storageLocations = storage,
            deckLocationName = "vi deck",
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        // Cards at "VI DECK" should match deck location "vi deck" (case-insensitive)
        assertTrue(plan.movements.isEmpty())
        assertTrue(plan.returns.isEmpty())
    }

    // ── Multiple storage locations ──

    @Test
    fun `movements span multiple storage locations`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 5)),
            lines = listOf(
                line("card-a", "Red Box", 2),
                line("card-a", "Grey Box", 3),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = false,
        )
        assertEquals(2, plan.movements.size)
        val totalMoved = plan.movements.sumOf { it.quantity }
        assertEquals(5, totalMoved)
        assertTrue(plan.movements.all { it.toLocation == deckDisplay })
    }

    // ── Missing with partial availability ──

    @Test
    fun `built deck with increased quantity and insufficient storage shows missing`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 10, source = "Red Box")),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-a", "Red Box", 2),
            ),
            storageLocations = storage,
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = storageDisplay,
            isAlreadyBuilt = true,
        )
        // 3 at deck + 2 in storage = 5, need 10 → 5 missing
        assertEquals(1, plan.movements.size)
        assertEquals(2, plan.movements[0].quantity)
        assertEquals(1, plan.missing.size)
        assertEquals(10, plan.missing[0].needed)
        assertEquals(5, plan.missing[0].available)
    }

    // ── No storage locations ──

    @Test
    fun `removed card with no storage locations returns to unlocated`() {
        val plan = DeckBuildPlanner.computePlan(
            entries = listOf(entry("card-a", qty = 3, source = null)),
            lines = listOf(
                line("card-a", "Vi Deck", 3),
                line("card-b", "Vi Deck", 2), // removed card
            ),
            storageLocations = emptySet(),
            deckLocationName = deckLoc,
            deckLocationDisplayName = deckDisplay,
            storageDisplayNames = emptyMap(),
            isAlreadyBuilt = true,
        )
        assertEquals(1, plan.returns.size)
        assertEquals("card-b", plan.returns[0].nameSlug)
        assertEquals("Storage", plan.returns[0].toLocation) // default fallback
    }
}
