package com.riftcompanion.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class InventoryPageDTO(
    val data: List<InventoryLineDTO>,
    val pagination: PaginationDTO,
)

@Serializable
data class PaginationDTO(
    val nextCursor: String? = null,
)

@Serializable
data class InventoryLineDTO(
    val id: String,
    val customId: String? = null,
    val productId: Long,
    val finish: String,
    val condition: String? = null,
    val language: String? = null,
    val quantity: Int,
    val graded: kotlinx.serialization.json.JsonElement? = null,
    val location: String? = null,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
    val notes: String? = null,
    val forSale: Boolean = false,
    val listing: kotlinx.serialization.json.JsonElement? = null,
    val updatedAt: String,
)

@Serializable
data class InventoryLocationDTO(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class CatalogueFeedMetadataDTO(
    val feedType: String,
    val url: String,
    val checksum: String,
    val recordCount: Int,
    val encoding: String,
    val generatedAt: String,
)

@Serializable
data class CatalogueProductDTO(
    val id: Long,
    val productType: String,
    val name: String,
    val nameSlug: String,
    val slug: String,
    val expansionId: Long? = null,
    val expansionSlug: String? = null,
    val printNumber: String? = null,
    val variant: String? = null,
    val rarity: String? = null,
    val finishes: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val imageUrl: String? = null,
    val imageBackUrl: String? = null,
    val attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

// ── Write DTOs ──────────────────────────────────────────────────────────

@Serializable
data class InventoryLocationUpsertDTO(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val upsert: Boolean = true,
)

@Serializable
data class InventoryLocationUpdateDTO(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

@Serializable
data class InventoryBulkUpdateItemDTO(
    val inventoryId: String,
    val quantity: QuantityAdjustmentDTO? = null,
    val location: String? = null,
    val count: Int? = null,
)

@Serializable
data class QuantityAdjustmentDTO(
    val adjust: Int,
)

@Serializable
data class InventoryBulkUpdateRequestDTO(
    val items: List<InventoryBulkUpdateItemDTO>,
)

@Serializable
data class InventoryBulkUpdateResponseDTO(
    val updated: Int = 0,
    val failed: Int = 0,
)

// ── Error ──────────────────────────────────────────────────────────────

@Serializable
data class CardNexusAPIErrorEnvelope(
    val code: String,
    val status: Int,
    val message: String,
    val data: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

// ── Price feed DTOs ────────────────────────────────────────────────────

@Serializable
data class PriceFeedMetadataDTO(
    val feedType: String,
    val url: String,
    val checksum: String,
    val recordCount: Int,
    val encoding: String,
    val generatedAt: String,
    val lastRefreshedAt: String? = null,
)

@Serializable
data class PriceFeedProductDTO(
    val productId: Long,
    val pricesByFinish: Map<String, PriceFeedFinishDTO> = emptyMap(),
)

@Serializable
data class PriceFeedFinishDTO(
    val cardmarket: PriceFeedCardmarketDTO? = null,
    val tcgplayer: PriceFeedTcgplayerDTO? = null,
    val cardnexus: PriceFeedCardnexusDTO? = null,
)

@Serializable
data class PriceFeedCardmarketDTO(
    val currency: String? = null,
    val low: Double? = null,
    val mid: Double? = null,
    val high: Double? = null,
    val marketValue: Double? = null,
    val change24h: Double? = null,
    val change7d: Double? = null,
    val change30d: Double? = null,
    val date: String? = null,
)

@Serializable
data class PriceFeedTcgplayerDTO(
    val currency: String? = null,
    val low: Double? = null,
    val mid: Double? = null,
    val high: Double? = null,
    val marketValue: Double? = null,
    val change24h: Double? = null,
    val change7d: Double? = null,
    val change30d: Double? = null,
    val date: String? = null,
)

@Serializable
data class PriceFeedCardnexusDTO(
    val low: PriceFeedCardnexusLowDTO? = null,
    val listingCount: Int? = null,
    val availableQuantity: Int? = null,
)

@Serializable
data class PriceFeedCardnexusLowDTO(
    val amount: Double? = null,
    val currency: String? = null,
)
