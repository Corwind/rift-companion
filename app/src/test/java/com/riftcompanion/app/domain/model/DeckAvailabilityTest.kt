package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-regression tests for deck card availability.
 *
 * These guard against the recurring bug where cards physically present at a
 * deck's linked location were reported as "missing". The availability logic
 * lives in DeckAvailability (a pure function) so it can be tested here.
 */
class DeckAvailabilityTest {

    private val storage = setOf("red box", "grey box - order cards")
    private val decks = setOf("vi deck", "azir deck")

    @Test
    fun `cards at the deck linked location are available`() {
        val result = DeckAvailability.compute(
            quantity = 3,
            zone = DeckZone.main,
            lineLocations = listOf("Vi deck", "Red box"),
            lineQuantities = listOf(2, 1),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "Vi deck",
        )
        // 1 in storage + 2 at deck location = 3 available, not missing
        assertEquals(3, result.availableInStorage)
        assertFalse(result.isMissing)
        assertEquals(0, result.missingCount)
    }

    @Test
    fun `cards at deck location count even when casing differs`() {
        // linkedLocationName stored as "vi deck" (from build) but lines at "Vi deck" (from API)
        val result = DeckAvailability.compute(
            quantity = 3,
            zone = DeckZone.main,
            lineLocations = listOf("Vi deck"),
            lineQuantities = listOf(3),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "vi deck",
        )
        assertEquals(3, result.availableInStorage)
        assertFalse(result.isMissing)
    }

    @Test
    fun `cards only in other decks are not available`() {
        val result = DeckAvailability.compute(
            quantity = 3,
            zone = DeckZone.main,
            lineLocations = listOf("Azir deck"),
            lineQuantities = listOf(3),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "Vi deck",
        )
        assertEquals(0, result.availableInStorage)
        assertEquals(3, result.inOtherDecks)
        assertTrue(result.isMissing)
        assertEquals(3, result.missingCount)
    }

    @Test
    fun `cards in storage count as available`() {
        val result = DeckAvailability.compute(
            quantity = 2,
            zone = DeckZone.main,
            lineLocations = listOf("Red box"),
            lineQuantities = listOf(2),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "Vi deck",
        )
        assertEquals(2, result.availableInStorage)
        assertFalse(result.isMissing)
    }

    @Test
    fun `partial availability shows missing count`() {
        val result = DeckAvailability.compute(
            quantity = 4,
            zone = DeckZone.main,
            lineLocations = listOf("Red box", "Vi deck"),
            lineQuantities = listOf(1, 1),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "Vi deck",
        )
        // 1 storage + 1 at deck = 2 available, need 4 → 2 missing
        assertEquals(2, result.availableInStorage)
        assertTrue(result.isMissing)
        assertEquals(2, result.missingCount)
    }

    @Test
    fun `runes are never missing`() {
        val result = DeckAvailability.compute(
            quantity = 12,
            zone = DeckZone.rune,
            lineLocations = emptyList(),
            lineQuantities = emptyList(),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = null,
        )
        assertEquals(12, result.availableInStorage)
        assertFalse(result.isMissing)
        assertEquals(0, result.missingCount)
    }

    @Test
    fun `battlefields are never missing`() {
        val result = DeckAvailability.compute(
            quantity = 3,
            zone = DeckZone.battlefield,
            lineLocations = emptyList(),
            lineQuantities = emptyList(),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = null,
        )
        assertEquals(3, result.availableInStorage)
        assertFalse(result.isMissing)
        assertEquals(0, result.missingCount)
    }

    @Test
    fun `no inventory means fully missing for main deck`() {
        val result = DeckAvailability.compute(
            quantity = 3,
            zone = DeckZone.main,
            lineLocations = emptyList(),
            lineQuantities = emptyList(),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = null,
        )
        assertEquals(0, result.availableInStorage)
        assertTrue(result.isMissing)
        assertEquals(3, result.missingCount)
    }

    @Test
    fun `cards at deck location are not counted as in other decks`() {
        val result = DeckAvailability.compute(
            quantity = 2,
            zone = DeckZone.main,
            lineLocations = listOf("Vi deck"),
            lineQuantities = listOf(2),
            storageLocations = storage,
            deckLocations = decks,
            linkedLocation = "Vi deck",
        )
        assertEquals(0, result.inOtherDecks)
        assertEquals(2, result.availableInStorage)
    }
}
