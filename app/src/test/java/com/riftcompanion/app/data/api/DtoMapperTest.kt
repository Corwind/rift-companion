package com.riftcompanion.app.data.api

import com.riftcompanion.app.data.api.dto.CatalogueFeedMetadataDTO
import com.riftcompanion.app.data.api.dto.CatalogueProductDTO
import com.riftcompanion.app.data.api.dto.InventoryLineDTO
import com.riftcompanion.app.data.api.dto.InventoryLocationDTO
import com.riftcompanion.app.domain.model.InventoryLocation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-regression tests for DtoMapper — ensures domain model mapping
 * stays correct as the codebase evolves.
 */
class DtoMapperTest {

    @Test
    fun `maps InventoryLineDTO to domain with all fields`() {
        val dto = InventoryLineDTO(
            id = "inv-123",
            customId = "custom-1",
            productId = 456L,
            finish = "foil",
            condition = "near-mint",
            language = "English",
            quantity = 3,
            location = "Box A",
            tags = listOf("champion", "rune"),
            comment = "some comment",
            notes = "some notes",
            forSale = true,
            updatedAt = "2024-01-15T10:30:00Z",
        )

        val domain = DtoMapper.toDomain(dto)

        assertEquals("inv-123", domain.id)
        assertEquals("custom-1", domain.customId)
        assertEquals(456L, domain.productId)
        assertEquals("foil", domain.finish)
        assertEquals("near-mint", domain.condition)
        assertEquals("English", domain.language)
        assertEquals(3, domain.quantity)
        assertEquals("Box A", domain.location)
        assertEquals(listOf("champion", "rune"), domain.tags)
        assertEquals("some comment", domain.comment)
        assertEquals("some notes", domain.notes)
        assertTrue(domain.forSale)
        assertEquals("2024-01-15T10:30:00Z", domain.updatedAt)
    }

    @Test
    fun `maps InventoryLineDTO with null optional fields`() {
        val dto = InventoryLineDTO(
            id = "inv-1",
            productId = 1L,
            finish = "normal",
            quantity = 1,
            updatedAt = "2024-01-01T00:00:00Z",
        )

        val domain = DtoMapper.toDomain(dto)

        assertEquals("inv-1", domain.id)
        assertNull(domain.customId)
        assertNull(domain.condition)
        assertNull(domain.language)
        assertNull(domain.location)
        assertTrue(domain.tags.isEmpty())
        assertNull(domain.comment)
        assertNull(domain.notes)
        assertFalse(domain.forSale)
    }

    @Test
    fun `maps InventoryLocationDTO to domain`() {
        val dto = InventoryLocationDTO(
            name = "Box A",
            color = "blue",
            icon = "shippingbox",
        )

        val domain = DtoMapper.toDomain(dto)

        assertEquals("Box A", domain.name)
        assertEquals("blue", domain.color)
        assertEquals("shippingbox", domain.icon)
        assertEquals("box a", domain.normalizedName)
    }

    @Test
    fun `InventoryLocation normalizes empty name to __unlocated__`() {
        val loc = InventoryLocation(name = "")
        assertEquals("__unlocated__", loc.normalizedName)
    }

    @Test
    fun `InventoryLocation normalizes whitespace name to __unlocated__`() {
        val loc = InventoryLocation(name = "   ")
        assertEquals("__unlocated__", loc.normalizedName)
    }

    @Test
    fun `InventoryLocation normalizes mixed case to lowercase`() {
        val loc = InventoryLocation(name = "Box A")
        assertEquals("box a", loc.normalizedName)
    }

    @Test
    fun `maps CatalogueProductDTO with card type to domain`() {
        val attributes = buildJsonObject {
            put("cardType", JsonPrimitive("Unit"))
            put("domains", kotlinx.serialization.json.buildJsonArray {
                add(JsonPrimitive("Fury"))
                add(JsonPrimitive("Chaos"))
            })
            put("cardTags", kotlinx.serialization.json.buildJsonArray {
                add(JsonPrimitive("Champion"))
            })
            put("energyCost", JsonPrimitive(3))
        }

        val dto = CatalogueProductDTO(
            id = 100L,
            productType = "card",
            name = "Ahri, Charmer",
            nameSlug = "ahri-charmer",
            slug = "ahri-charmer-origins",
            expansionSlug = "origins",
            rarity = "Epic",
            finishes = listOf("normal", "foil"),
            languages = listOf("English", "French"),
            imageUrl = "https://example.com/card.png",
            attributes = attributes,
        )

        val domain = DtoMapper.toDomain(dto)

        assertNotNull(domain)
        assertEquals(100L, domain!!.productID)
        assertEquals("ahri-charmer", domain.nameSlug)
        assertEquals("Ahri, Charmer", domain.displayName)
        assertEquals("origins", domain.expansionSlug)
        assertEquals("Epic", domain.rarity)
        assertEquals(listOf("normal", "foil"), domain.finishes)
        assertEquals("https://example.com/card.png", domain.imageURL)
    }

    @Test
    fun `maps CatalogueProductDTO with non-card type returns null`() {
        val dto = CatalogueProductDTO(
            id = 1L,
            productType = "accessory",
            name = "Playmat",
            nameSlug = "playmat",
            slug = "playmat-1",
        )

        val domain = DtoMapper.toDomain(dto)
        assertNull(domain)
    }

    @Test
    fun `maps CatalogueFeedMetadataDTO to domain`() {
        val dto = CatalogueFeedMetadataDTO(
            feedType = "catalog",
            url = "https://example.com/feed.gz",
            checksum = "abc123",
            recordCount = 500,
            encoding = "gzip",
            generatedAt = "2024-01-15T10:00:00Z",
        )

        val domain = DtoMapper.toDomain(dto)

        assertEquals("catalog", domain.feedType)
        assertEquals("https://example.com/feed.gz", domain.url)
        assertEquals("abc123", domain.checksum)
        assertEquals(500, domain.recordCount)
        assertEquals("gzip", domain.encoding)
    }
}
