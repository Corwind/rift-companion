package com.riftcompanion.app.data.api

import com.riftcompanion.app.data.api.dto.CatalogueFeedMetadataDTO
import com.riftcompanion.app.data.api.dto.CatalogueProductDTO
import com.riftcompanion.app.data.api.dto.InventoryBulkUpdateItemDTO
import com.riftcompanion.app.data.api.dto.InventoryBulkUpdateRequestDTO
import com.riftcompanion.app.data.api.dto.InventoryBulkUpdateResponseDTO
import com.riftcompanion.app.data.api.dto.InventoryLineDTO
import com.riftcompanion.app.data.api.dto.InventoryLocationDTO
import com.riftcompanion.app.data.api.dto.QuantityAdjustmentDTO
import com.riftcompanion.app.domain.model.CardPrinting
import com.riftcompanion.app.domain.model.CatalogueFeedMetadata
import com.riftcompanion.app.domain.model.InventoryBulkMoveRequest
import com.riftcompanion.app.domain.model.InventoryBulkMoveResponse
import com.riftcompanion.app.domain.model.InventoryLine
import com.riftcompanion.app.domain.model.InventoryLocation
import com.riftcompanion.app.domain.model.JsonValue
import com.riftcompanion.app.domain.model.toJsonValue

object DtoMapper {

    fun toDomain(dto: InventoryLineDTO): InventoryLine {
        return InventoryLine(
            id = dto.id,
            customId = dto.customId,
            productId = dto.productId,
            finish = dto.finish,
            condition = dto.condition,
            language = dto.language,
            quantity = dto.quantity,
            graded = dto.graded?.toJsonValue(),
            location = dto.location,
            tags = dto.tags,
            comment = dto.comment,
            notes = dto.notes,
            forSale = dto.forSale,
            listing = dto.listing?.toJsonValue(),
            updatedAt = dto.updatedAt,
        )
    }

    fun toDomain(dto: InventoryLocationDTO): InventoryLocation {
        return InventoryLocation(
            name = dto.name,
            color = dto.color,
            icon = dto.icon,
        )
    }

    fun toDomain(dto: CatalogueFeedMetadataDTO): CatalogueFeedMetadata {
        return CatalogueFeedMetadata(
            feedType = dto.feedType,
            url = dto.url,
            checksum = dto.checksum,
            recordCount = dto.recordCount,
            encoding = dto.encoding,
            generatedAt = dto.generatedAt,
        )
    }

    fun toDomain(dto: CatalogueProductDTO): CardPrinting? {
        if (dto.productType != "card") return null
        return CardPrinting(
            productID = dto.id,
            nameSlug = dto.nameSlug,
            printingSlug = dto.slug,
            displayName = dto.name,
            expansionID = dto.expansionId,
            expansionSlug = dto.expansionSlug,
            printNumber = dto.printNumber,
            variant = dto.variant,
            rarity = dto.rarity,
            finishes = dto.finishes,
            languages = dto.languages,
            imageURL = dto.imageUrl,
            imageBackURL = dto.imageBackUrl,
            attributes = dto.attributes.mapValues { it.value.toJsonValue() },
        )
    }

    fun toBulkUpdateDTO(request: InventoryBulkMoveRequest): InventoryBulkUpdateRequestDTO {
        return InventoryBulkUpdateRequestDTO(
            items = request.moves.map { move ->
                InventoryBulkUpdateItemDTO(
                    inventoryId = move.inventoryID,
                    quantity = move.quantityAdjustment?.let { QuantityAdjustmentDTO(it) },
                    location = move.destinationLocationName,
                    count = move.count,
                )
            }
        )
    }

    fun toDomain(dto: InventoryBulkUpdateResponseDTO): InventoryBulkMoveResponse {
        return InventoryBulkMoveResponse(
            updated = dto.updated,
            failed = dto.failed,
        )
    }
}
