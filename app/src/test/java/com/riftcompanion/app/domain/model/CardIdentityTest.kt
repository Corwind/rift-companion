package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-regression tests for CardIdentity domain logic.
 */
class CardIdentityTest {

    @Test
    fun `appIsRune returns true when cardType contains rune`() {
        val identity = CardIdentity(
            nameSlug = "calm-rune",
            displayName = "Calm Rune",
            cardType = "Rune",
        )
        assertTrue(identity.appIsRune)
    }

    @Test
    fun `appIsRune returns true when tags contain rune`() {
        val identity = CardIdentity(
            nameSlug = "some-card",
            displayName = "Some Card",
            cardType = "Unit",
            tags = listOf("Rune", "Champion"),
        )
        assertTrue(identity.appIsRune)
    }

    @Test
    fun `appIsRune returns false for non-rune cards`() {
        val identity = CardIdentity(
            nameSlug = "ahri",
            displayName = "Ahri",
            cardType = "Unit",
            tags = listOf("Champion"),
        )
        assertFalse(identity.appIsRune)
    }

    @Test
    fun `appIsBattlefield returns true when cardType contains battlefield`() {
        val identity = CardIdentity(
            nameSlug = "spirit-realm",
            displayName = "Spirit Realm",
            cardType = "Battlefield",
        )
        assertTrue(identity.appIsBattlefield)
    }

    @Test
    fun `appVisibleDomains filters out neutral`() {
        val identity = CardIdentity(
            nameSlug = "test",
            displayName = "Test",
            domains = listOf("Fury", "Neutral", "Chaos"),
        )
        assertEquals(listOf("Fury", "Chaos"), identity.appVisibleDomains)
    }

    @Test
    fun `appVisibleDomains returns empty for battlefield cards`() {
        val identity = CardIdentity(
            nameSlug = "battlefield-1",
            displayName = "Battlefield",
            cardType = "Battlefield",
            domains = listOf("Calm"),
        )
        assertTrue(identity.appVisibleDomains.isEmpty())
    }

    @Test
    fun `appSearchText includes display name, name slug, card type, domains and tags`() {
        val identity = CardIdentity(
            nameSlug = "ahri-charmer",
            displayName = "Ahri, Charmer",
            cardType = "Unit",
            domains = listOf("Calm", "Chaos"),
            tags = listOf("Champion"),
        )
        val searchText = identity.appSearchText
        assertTrue(searchText.contains("Ahri, Charmer"))
        assertTrue(searchText.contains("ahri-charmer"))
        assertTrue(searchText.contains("Unit"))
        assertTrue(searchText.contains("Calm"))
        assertTrue(searchText.contains("Chaos"))
        assertTrue(searchText.contains("Champion"))
    }
}
