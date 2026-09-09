package com.riftcompanion.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Catalogue entities ─────────────────────────────────────────────────

@Entity(tableName = "card_identities")
data class CardIdentityEntity(
    @PrimaryKey val nameSlug: String,
    val gameID: String,
    val displayName: String,
    val cardType: String?,
    val superType: String?,
    val domainsCsv: String,      // comma-separated
    val tagsCsv: String,          // comma-separated
    val energyCost: Int?,
    val mightCost: Int?,
    val attributesJson: String,  // JSON string
)

@Entity(tableName = "card_printings", indices = [Index("nameSlug")])
data class CardPrintingEntity(
    @PrimaryKey val productID: Long,
    val nameSlug: String,
    val printingSlug: String,
    val displayName: String,
    val expansionID: Long?,
    val expansionSlug: String?,
    val printNumber: String?,
    val variant: String?,
    val rarity: String?,
    val finishesCsv: String,
    val languagesCsv: String,
    val imageURL: String?,
    val imageBackURL: String?,
    val attributesJson: String,
)

// ── Inventory entities ─────────────────────────────────────────────────

@Entity(tableName = "inventory_lines")
data class InventoryLineEntity(
    @PrimaryKey val id: String,
    val customId: String?,
    val productId: Long,
    val finish: String,
    val condition: String?,
    val language: String?,
    val quantity: Int,
    val locationName: String?,
    val tagsCsv: String,
    val comment: String?,
    val notes: String?,
    val forSale: Boolean,
    val updatedAt: String,
)

@Entity(tableName = "inventory_locations")
data class InventoryLocationEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val color: String?,
    val icon: String?,
)

// ── Location policy (local overlay) ────────────────────────────────────

@Entity(tableName = "location_policies")
data class LocationPolicyEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val color: String?,
    val icon: String?,
    val kind: String,          // storage | deck | unavailable
    val countsAsAvailable: Boolean,
    val hidden: Boolean,
    val linkedDeckId: String? = null,  // if kind == "deck", which deck this location belongs to
)

// ── Sync metadata ──────────────────────────────────────────────────────

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
