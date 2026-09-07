package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Non-regression tests for InventoryLocation normalization — the bug
 * where URLEncoder produces '+' for spaces caused NOT_FOUND errors.
 * These tests ensure the normalize() logic stays correct.
 */
class InventoryLocationTest {

    @Test
    fun `normalize lowercases the name`() {
        assertEquals("box a", InventoryLocation.normalize("Box A"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("box a", InventoryLocation.normalize("  Box A  "))
    }

    @Test
    fun `normalize returns __unlocated__ for null`() {
        assertEquals("__unlocated__", InventoryLocation.normalize(null))
    }

    @Test
    fun `normalize returns __unlocated__ for empty string`() {
        assertEquals("__unlocated__", InventoryLocation.normalize(""))
    }

    @Test
    fun `normalize returns __unlocated__ for whitespace-only string`() {
        assertEquals("__unlocated__", InventoryLocation.normalize("   "))
    }

    @Test
    fun `normalizedName property matches companion normalize`() {
        val loc = InventoryLocation(name = "Trade Binder")
        assertEquals(InventoryLocation.normalize("Trade Binder"), loc.normalizedName)
        assertEquals("trade binder", loc.normalizedName)
    }
}
