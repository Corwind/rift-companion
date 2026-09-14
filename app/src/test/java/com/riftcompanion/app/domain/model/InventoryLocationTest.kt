package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Non-regression tests for InventoryLocation identity.
 *
 * Locations use the EXACT name from the API as their identity — NOT a
 * normalized/lowercased transformation. This was a deliberate change:
 * normalizing "Vi deck" and "vi Deck" to the same key would incorrectly
 * merge two distinct locations.
 */
class InventoryLocationTest {

    @Test
    fun `id is the exact name`() {
        val loc = InventoryLocation(name = "Vi deck")
        assertEquals("Vi deck", loc.id)
    }

    @Test
    fun `id preserves case`() {
        val loc = InventoryLocation(name = "vi Deck")
        assertEquals("vi Deck", loc.id)
    }

    @Test
    fun `two locations with different case are distinct`() {
        val a = InventoryLocation(name = "Vi deck")
        val b = InventoryLocation(name = "vi Deck")
        // Distinct names must NOT collapse to the same identity
        assert(a.id != b.id)
    }

    @Test
    fun `color and icon are preserved`() {
        val loc = InventoryLocation(name = "Red box", color = "#ff0000", icon = "box")
        assertEquals("#ff0000", loc.color)
        assertEquals("box", loc.icon)
    }
}
