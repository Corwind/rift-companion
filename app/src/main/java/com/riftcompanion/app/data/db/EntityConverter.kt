package com.riftcompanion.app.data.db

import com.riftcompanion.app.domain.model.CardIdentity
import com.riftcompanion.app.domain.model.CardPrinting
import com.riftcompanion.app.domain.model.InventoryLine
import com.riftcompanion.app.domain.model.InventoryLocation
import com.riftcompanion.app.domain.model.JsonValue
import kotlinx.serialization.json.Json

object EntityConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── CardIdentity ─────────────────────────────────────────────────────

    fun toEntity(identity: CardIdentity): CardIdentityEntity {
        return CardIdentityEntity(
            nameSlug = identity.nameSlug,
            gameID = identity.gameID,
            displayName = identity.displayName,
            cardType = identity.cardType,
            superType = identity.superType,
            domainsCsv = identity.domains.joinToString(","),
            tagsCsv = identity.tags.joinToString(","),
            energyCost = identity.energyCost,
            mightCost = identity.mightCost,
            attributesJson = json.encodeToString(JsonValue.serializer(), JsonValue.Obj(identity.attributes)),
        )
    }

    fun toDomain(entity: CardIdentityEntity): CardIdentity {
        val attributes = try {
            val value = json.decodeFromString(JsonValue.serializer(), entity.attributesJson)
            (value as? JsonValue.Obj)?.value ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        return CardIdentity(
            nameSlug = entity.nameSlug,
            gameID = entity.gameID,
            displayName = entity.displayName,
            cardType = entity.cardType,
            superType = entity.superType,
            domains = if (entity.domainsCsv.isBlank()) emptyList() else entity.domainsCsv.split(","),
            tags = if (entity.tagsCsv.isBlank()) emptyList() else entity.tagsCsv.split(","),
            energyCost = entity.energyCost,
            mightCost = entity.mightCost,
            attributes = attributes,
        )
    }

    // ── CardPrinting ────────────────────────────────────────────────────

    fun toEntity(printing: CardPrinting): CardPrintingEntity {
        return CardPrintingEntity(
            productID = printing.productID,
            nameSlug = printing.nameSlug,
            printingSlug = printing.printingSlug,
            displayName = printing.displayName,
            expansionID = printing.expansionID,
            expansionSlug = printing.expansionSlug,
            printNumber = printing.printNumber,
            variant = printing.variant,
            rarity = printing.rarity,
            finishesCsv = printing.finishes.joinToString(","),
            languagesCsv = printing.languages.joinToString(","),
            imageURL = printing.imageURL,
            imageBackURL = printing.imageBackURL,
            attributesJson = json.encodeToString(JsonValue.serializer(), JsonValue.Obj(printing.attributes)),
        )
    }

    fun toDomain(entity: CardPrintingEntity): CardPrinting {
        val attributes = try {
            val value = json.decodeFromString(JsonValue.serializer(), entity.attributesJson)
            (value as? JsonValue.Obj)?.value ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        return CardPrinting(
            productID = entity.productID,
            nameSlug = entity.nameSlug,
            printingSlug = entity.printingSlug,
            displayName = entity.displayName,
            expansionID = entity.expansionID,
            expansionSlug = entity.expansionSlug,
            printNumber = entity.printNumber,
            variant = entity.variant,
            rarity = entity.rarity,
            finishes = if (entity.finishesCsv.isBlank()) emptyList() else entity.finishesCsv.split(","),
            languages = if (entity.languagesCsv.isBlank()) emptyList() else entity.languagesCsv.split(","),
            imageURL = entity.imageURL,
            imageBackURL = entity.imageBackURL,
            attributes = attributes,
        )
    }

    // ── InventoryLine ───────────────────────────────────────────────────

    fun toEntity(line: InventoryLine): InventoryLineEntity {
        return InventoryLineEntity(
            id = line.id,
            customId = line.customId,
            productId = line.productId,
            finish = line.finish,
            condition = line.condition,
            language = line.language,
            quantity = line.quantity,
            // Use the exact location name from the API as identity (no normalization)
            locationName = line.location,
            tagsCsv = line.tags.joinToString(","),
            comment = line.comment,
            notes = line.notes,
            forSale = line.forSale,
            updatedAt = line.updatedAt,
        )
    }

    fun toDomain(entity: InventoryLineEntity): InventoryLine {
        return InventoryLine(
            id = entity.id,
            customId = entity.customId,
            productId = entity.productId,
            finish = entity.finish,
            condition = entity.condition,
            language = entity.language,
            quantity = entity.quantity,
            location = entity.locationName,
            tags = if (entity.tagsCsv.isBlank()) emptyList() else entity.tagsCsv.split(","),
            comment = entity.comment,
            notes = entity.notes,
            forSale = entity.forSale,
            updatedAt = entity.updatedAt,
        )
    }

    // ── InventoryLocation ──────────────────────────────────────────────

    fun toEntity(location: InventoryLocation): InventoryLocationEntity {
        return InventoryLocationEntity(
            name = location.name,
            displayName = location.name,
            color = location.color,
            icon = location.icon,
        )
    }

    fun toDomain(entity: InventoryLocationEntity): InventoryLocation {
        return InventoryLocation(
            name = entity.name,
            color = entity.color,
            icon = entity.icon,
        )
    }
}
