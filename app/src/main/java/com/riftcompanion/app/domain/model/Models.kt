package com.riftcompanion.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InventoryLine(
    val id: String,
    val customId: String? = null,
    val productId: Long,
    val finish: String,
    val condition: String? = null,
    val language: String? = null,
    val quantity: Int,
    val graded: JsonValue? = null,
    val location: String? = null,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
    val notes: String? = null,
    val forSale: Boolean = false,
    val listing: JsonValue? = null,
    val updatedAt: String, // ISO-8601 string from API
) {
    val inventoryID: String get() = id
}

@Serializable
data class InventoryLocation(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
) {
    val id: String get() = name
}

@Serializable
data class CatalogueFeedMetadata(
    val feedType: String,
    val url: String,
    val checksum: String,
    val recordCount: Int,
    val encoding: String,
    val generatedAt: String,
)

@Serializable
data class CardAvailability(
    val totalOwned: Int = 0,
    val availableInStorage: Int = 0,
    val inTargetDeck: Int = 0,
    val inOtherDecks: Int = 0,
    val otherwiseUnavailable: Int = 0,
    val required: Int = 0,
) {
    val usedInDecks: Int get() = inTargetDeck + inOtherDecks
}

@Serializable
data class LocationQuantity(
    val locationName: String,
    val displayName: String,
    val color: String? = null,
    val icon: String? = null,
    val kind: String = "storage",
    val quantity: Int,
    val isAvailable: Boolean = true,
)

@Serializable
data class CardMarketListing(
    val productID: Long,
    val printingSlug: String,
    val expansionSlug: String? = null,
    val printNumber: String? = null,
    val url: String,
    val currency: String? = null,
    val priceCents: Int? = null,
    val priceSource: String? = null,
    val scrapedAt: String,
) {
    val id: Long get() = productID
}

@Serializable
data class CataloguePrintingMetadata(
    val productID: Long,
    val printingSlug: String,
    val expansionSlug: String? = null,
    val printNumber: String? = null,
    val rarity: String? = null,
    val imageURL: String? = null,
) {
    val id: Long get() = productID
}

@Serializable
data class CatalogueCardSummary(
    val identity: CardIdentity,
    val preferredPrinting: CataloguePrintingMetadata? = null,
    val printingCount: Int,
    val expansionSlugs: List<String>,
    val rarities: List<String>,
    val marketListings: List<CardMarketListing> = emptyList(),
) {
    val id: String get() = identity.nameSlug
    val preferredImageURL: String? get() = preferredPrinting?.imageURL
}

@Serializable
data class InventoryCardSummary(
    val identity: CardIdentity,
    val preferredImageURL: String?,
    val availability: CardAvailability,
    val locations: List<LocationQuantity>,
    val expansion: String? = null,
    val rarity: String? = null,
    val finish: String? = null,
    val language: String? = null,
    val marketListings: List<CardMarketListing> = emptyList(),
) {
    val id: String get() = identity.nameSlug
}

// ── Location policy (local settings overlay) ──────────────────────────

enum class LocationKind(val storageValue: String, val title: String, val icon: String) {
    Storage("storage", "Storage", "inventory_2"),
    Deck("deck", "Deck", "style_button"),
    Unavailable("unavailable", "Unavailable", "block");

    companion object {
        fun fromStorageValue(value: String?): LocationKind =
            entries.firstOrNull { it.storageValue == value } ?: Storage
    }
}

data class LocationPolicy(
    val name: String,
    val displayName: String,
    val color: String? = null,
    val icon: String? = null,
    val kind: LocationKind = LocationKind.Storage,
    val countsAsAvailable: Boolean = true,
    val hidden: Boolean = false,
) {
    val id: String get() = name
}

// ── Inventory location mutation requests ────────────────────────────────

data class InventoryLocationUpsertRequest(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

data class InventoryLocationUpdateRequest(
    val currentName: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
)

data class InventoryBulkMoveItem(
    val inventoryID: String,
    val destinationLocationName: String? = null,
    val quantityAdjustment: Int? = null,
    val count: Int? = null,
)

data class InventoryBulkMoveRequest(
    val idempotencyKey: String,
    val moves: List<InventoryBulkMoveItem>,
)

data class InventoryBulkMoveResponse(
    val updated: Int,
    val failed: Int = 0,
)
