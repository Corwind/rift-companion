package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the price domain models and their behavior.
 */
class CardPriceTest {

    @Test
    fun `CardPrice with all fields populated`() {
        val price = CardPrice(
            productID = 50212L,
            nameSlug = "test-card",
            finish = "Standard",
            cardmarketMarketValue = 43.75,
            cardmarketLow = 38.90,
            cardmarketChange24h = 0.4,
            cardmarketChange7d = -1.2,
            cardmarketChange30d = 5.7,
            tcgplayerMarketValue = 47.20,
            tcgplayerLow = 42.50,
            tcgplayerChange24h = 0.2,
            tcgplayerChange7d = -0.8,
            tcgplayerChange30d = 6.1,
            cardnexusLow = 38.50,
            cardnexusListingCount = 14,
        )
        assertEquals(50212L, price.productID)
        assertEquals("test-card", price.nameSlug)
        assertEquals("Standard", price.finish)
        assertEquals(43.75, price.cardmarketMarketValue!!, 0.001)
        assertEquals(47.20, price.tcgplayerMarketValue!!, 0.001)
        assertEquals(38.50, price.cardnexusLow!!, 0.001)
        assertEquals(14, price.cardnexusListingCount)
    }

    @Test
    fun `CardPrice with null fields for unpriced card`() {
        val price = CardPrice(
            productID = 99999L,
            nameSlug = "unpriced-card",
            finish = "Standard",
            cardmarketMarketValue = null,
            cardmarketLow = null,
            cardmarketChange24h = null,
            cardmarketChange7d = null,
            cardmarketChange30d = null,
            tcgplayerMarketValue = null,
            tcgplayerLow = null,
            tcgplayerChange24h = null,
            tcgplayerChange7d = null,
            tcgplayerChange30d = null,
            cardnexusLow = null,
            cardnexusListingCount = null,
        )
        assertNull(price.cardmarketMarketValue)
        assertNull(price.tcgplayerMarketValue)
        assertNull(price.cardnexusLow)
    }

    @Test
    fun `CollectionValue with mixed priced and unpriced cards`() {
        val value = CollectionValue(
            totalValueEur = 150.50,
            totalValueUsd = 165.00,
            pricedCardCount = 3,
            unpricedCardCount = 2,
            perCard = listOf(
                CardValueSummary("card-a", "Card A", 2, 100.0, 110.0, 5.0),
                CardValueSummary("card-b", "Card B", 1, 50.50, 55.00, -2.0),
                CardValueSummary("card-c", "Card C", 1, null, null, null),
            ),
        )
        assertEquals(150.50, value.totalValueEur, 0.001)
        assertEquals(165.00, value.totalValueUsd, 0.001)
        assertEquals(3, value.pricedCardCount)
        assertEquals(2, value.unpricedCardCount)
        assertEquals(3, value.perCard.size)
    }

    @Test
    fun `CollectionValue with no prices returns zero totals`() {
        val value = CollectionValue(
            totalValueEur = 0.0,
            totalValueUsd = 0.0,
            pricedCardCount = 0,
            unpricedCardCount = 5,
            perCard = emptyList(),
        )
        assertEquals(0.0, value.totalValueEur, 0.001)
        assertEquals(0.0, value.totalValueUsd, 0.001)
        assertEquals(0, value.pricedCardCount)
        assertEquals(5, value.unpricedCardCount)
        assertTrue(value.perCard.isEmpty())
    }

    @Test
    fun `CardValueSummary with null prices represents unpriced card`() {
        val summary = CardValueSummary(
            nameSlug = "unpriced",
            displayName = "Unpriced Card",
            quantity = 3,
            marketValueEur = null,
            marketValueUsd = null,
            change7d = null,
        )
        assertNull(summary.marketValueEur)
        assertNull(summary.marketValueUsd)
        assertNull(summary.change7d)
        assertEquals(3, summary.quantity)
    }
}
