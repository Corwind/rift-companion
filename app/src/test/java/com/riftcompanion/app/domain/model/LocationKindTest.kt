package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-regression tests for LocationKind — ensures the enum values
 * and fromStorageValue mapping stay correct.
 */
class LocationKindTest {

    @Test
    fun `fromStorageValue returns Storage for storage`() {
        assertEquals(LocationKind.Storage, LocationKind.fromStorageValue("storage"))
    }

    @Test
    fun `fromStorageValue returns Deck for deck`() {
        assertEquals(LocationKind.Deck, LocationKind.fromStorageValue("deck"))
    }

    @Test
    fun `fromStorageValue returns Unavailable for unavailable`() {
        assertEquals(LocationKind.Unavailable, LocationKind.fromStorageValue("unavailable"))
    }

    @Test
    fun `fromStorageValue returns Storage as default for unknown value`() {
        assertEquals(LocationKind.Storage, LocationKind.fromStorageValue("unknown"))
    }

    @Test
    fun `fromStorageValue returns Storage as default for null`() {
        assertEquals(LocationKind.Storage, LocationKind.fromStorageValue(null))
    }

    @Test
    fun `Storage has correct title and icon`() {
        assertEquals("Storage", LocationKind.Storage.title)
        assertEquals("inventory_2", LocationKind.Storage.icon)
    }

    @Test
    fun `Deck has correct title and icon`() {
        assertEquals("Deck", LocationKind.Deck.title)
        assertEquals("style_button", LocationKind.Deck.icon)
    }

    @Test
    fun `Unavailable has correct title and icon`() {
        assertEquals("Unavailable", LocationKind.Unavailable.title)
        assertEquals("block", LocationKind.Unavailable.icon)
    }
}
