package com.riftcompanion.app.data.repository

import com.riftcompanion.app.data.api.BanlistFetcher
import com.riftcompanion.app.data.api.CardNexusClient
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.EntityConverter
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.data.db.SyncMetadataDao
import kotlinx.coroutines.flow.first
import com.riftcompanion.app.domain.model.BanlistEntry
import com.riftcompanion.app.domain.model.BanlistEntryType
import com.riftcompanion.app.domain.model.CardAvailability
import com.riftcompanion.app.domain.model.CardIdentity
import com.riftcompanion.app.domain.model.CatalogueCardSummary
import com.riftcompanion.app.domain.model.CataloguePrintingMetadata
import com.riftcompanion.app.domain.model.CollectionValue
import com.riftcompanion.app.domain.model.CardValueSummary
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.InventoryBulkMoveItem
import com.riftcompanion.app.domain.model.InventoryBulkMoveRequest
import com.riftcompanion.app.domain.model.InventoryLine
import com.riftcompanion.app.domain.model.InventoryLocation
import com.riftcompanion.app.domain.model.InventoryLocationQuantityEdit
import com.riftcompanion.app.domain.model.InventoryQuantityEditResult
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.domain.model.LocationQuantity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all data. Reads come from Room (Flow-based, reactive).
 * Writes happen during user-initiated sync: fetch from CardNexus → persist to Room.
 * No background polling, no WorkManager, no periodic sync — zero battery drain
 * when the app is not in the foreground.
 */
@Singleton
private data class CatalogueFlowData(
    val identities: List<com.riftcompanion.app.data.db.CardIdentityEntity>,
    val printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
    val lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
    val locations: List<com.riftcompanion.app.data.db.InventoryLocationEntity>,
    val policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
)

class RiftRepository @Inject constructor(
    private val cardNexusClient: CardNexusClient,
    private val banlistFetcher: BanlistFetcher,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val cardPriceDao: com.riftcompanion.app.data.db.CardPriceDao,
) {

    // ── Sync ────────────────────────────────────────────────────────────

    suspend fun synchronize(forceCatalogue: Boolean = false): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Fetch catalogue metadata
            val metadata = cardNexusClient.fetchCatalogueMetadata().getOrThrow()
            val storedChecksum = syncMetadataDao.get("catalogue_checksum")

            if (forceCatalogue || storedChecksum != metadata.checksum) {
                // 2. Download and parse catalogue
                val printings = cardNexusClient.downloadCatalogue(metadata.url, metadata.encoding).getOrThrow().toList()

                // 3. Extract unique identities from printings
                val identities = printings
                    .groupBy { it.nameSlug }
                    .mapValues { (nameSlug, prints) ->
                        val first = prints.first()
                        CardIdentity(
                            nameSlug = nameSlug,
                            gameID = "riftbound",
                            displayName = first.displayName,
                            cardType = first.attributes["cardType"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Str)?.value },
                            superType = first.attributes["superType"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Str)?.value },
                            domains = first.attributes["domains"]?.let { extractStringList(it) } ?: emptyList(),
                            tags = first.attributes["cardTags"]?.let { extractStringList(it) } ?: emptyList(),
                            energyCost = first.attributes["energyCost"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Num)?.value?.toInt() },
                            might = first.attributes["mightCost"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Num)?.value?.toInt() },
                            attributes = first.attributes,
                        )
                    }
                    .values
                    .toList()

                cardIdentityDao.replaceAll(identities.map { EntityConverter.toEntity(it) })
                cardPrintingDao.replaceAll(printings.map { EntityConverter.toEntity(it) })
                syncMetadataDao.set("catalogue_checksum", metadata.checksum)
            }

            // 4. Fetch inventory and locations
            val lines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
            val locations = cardNexusClient.fetchLocations().getOrThrow()

            inventoryLineDao.replaceApiLines(lines.map { EntityConverter.toEntity(it) })
            inventoryLocationDao.replaceAll(locations.map { EntityConverter.toEntity(it) })

            // 5. Auto-create location policies for new locations
            val existingPolicies = locationPolicyDao.getAll().first()
            val existingNames = existingPolicies.map { it.name }.toSet()
            locations.forEach { loc ->
                if (loc.name !in existingNames) {
                    locationPolicyDao.upsert(
                        com.riftcompanion.app.data.db.LocationPolicyEntity(
                            name = loc.name,
                            displayName = loc.name,
                            color = loc.color,
                            icon = loc.icon,
                            kind = LocationKind.Storage.storageValue,
                            countsAsAvailable = true,
                            hidden = false,
                        ),
                    )
                }
            }

            // Banlist is now static — no DB storage needed

            // 6. Fetch and store price feed (best-effort, don't fail sync on price errors)
            try {
                val priceMetadata = cardNexusClient.fetchPriceFeedMetadata().getOrThrow()
                val storedPriceChecksum = syncMetadataDao.get("prices_checksum")
                if (storedPriceChecksum != priceMetadata.checksum) {
                    val printingMap = cardPrintingDao.getAll().first().associateBy { it.productID }
                    val priceEntities = mutableListOf<com.riftcompanion.app.data.db.CardPriceEntity>()
                    cardNexusClient.downloadPriceFeed(priceMetadata.url, priceMetadata.encoding).getOrThrow().forEach { priceDto ->
                        val printing = printingMap[priceDto.productId] ?: return@forEach
                        priceDto.pricesByFinish.forEach { (finish, prices) ->
                            priceEntities.add(com.riftcompanion.app.data.db.CardPriceEntity(
                                productID = priceDto.productId,
                                nameSlug = printing.nameSlug,
                                finish = finish,
                                cardmarketLow = prices.cardmarket?.low,
                                cardmarketMid = prices.cardmarket?.mid,
                                cardmarketHigh = prices.cardmarket?.high,
                                cardmarketMarketValue = prices.cardmarket?.marketValue,
                                cardmarketChange24h = prices.cardmarket?.change24h,
                                cardmarketChange7d = prices.cardmarket?.change7d,
                                cardmarketChange30d = prices.cardmarket?.change30d,
                                tcgplayerLow = prices.tcgplayer?.low,
                                tcgplayerMid = prices.tcgplayer?.mid,
                                tcgplayerHigh = prices.tcgplayer?.high,
                                tcgplayerMarketValue = prices.tcgplayer?.marketValue,
                                tcgplayerChange24h = prices.tcgplayer?.change24h,
                                tcgplayerChange7d = prices.tcgplayer?.change7d,
                                tcgplayerChange30d = prices.tcgplayer?.change30d,
                                cardnexusLow = prices.cardnexus?.low?.amount,
                                cardnexusListingCount = prices.cardnexus?.listingCount,
                                updatedAt = System.currentTimeMillis(),
                            ))
                        }
                    }
                    cardPriceDao.replaceAll(priceEntities)
                    syncMetadataDao.set("prices_checksum", priceMetadata.checksum)
                }
            } catch (e: Exception) {
                // Price feed is best-effort — don't fail the entire sync
            }

            val completedAt = System.currentTimeMillis()
            syncMetadataDao.set("last_sync", completedAt.toString())

            SyncResult(
                catalogueImported = forceCatalogue || storedChecksum != metadata.checksum,
                inventoryLines = lines.size,
                locations = locations.size,
                completedAt = completedAt,
            )
        }
    }

    suspend fun getLastSyncTimestamp(): Long? = withContext(Dispatchers.IO) {
        syncMetadataDao.get("last_sync")?.toLongOrNull()
    }

    // ── Collection value ───────────────────────────────────────────────

    fun collectionValueFlow(): Flow<CollectionValue> {
        return combine(
            inventoryLineDao.getAll(),
            cardPriceDao.getAll(),
            cardPrintingDao.getAll(),
            cardIdentityDao.getAll(),
        ) { lines, prices, printings, identities ->
            val identityMap = identities.associateBy { it.nameSlug }
            val printingMap = printings.associateBy { it.productID }
            val priceMap = prices.associateBy { it.productID }

            val perCard = mutableListOf<CardValueSummary>()
            var totalEur = 0.0
            var totalUsd = 0.0
            var pricedCount = 0
            var unpricedCount = 0

            // Group inventory lines by nameSlug and sum quantities
            val qtyBySlug = lines.groupBy { printingMap[it.productId]?.nameSlug ?: "" }
                .mapValues { (_, l) -> l.sumOf { it.quantity } }
                .filter { it.key.isNotBlank() }

            for ((nameSlug, qty) in qtyBySlug) {
                val identity = identityMap[nameSlug]
                val displayName = identity?.displayName ?: nameSlug

                // Find the best price for this card (prefer the first printing's price)
                val cardPrintings = printings.filter { it.nameSlug == nameSlug }
                val price = cardPrintings.firstNotNullOfOrNull { printingMap[it.productID]?.let { priceMap[it.productID] } }

                if (price != null && (price.cardmarketMarketValue != null || price.tcgplayerMarketValue != null)) {
                    val eur = price.cardmarketMarketValue?.times(qty)
                    val usd = price.tcgplayerMarketValue?.times(qty)
                    if (eur != null) totalEur += eur
                    if (usd != null) totalUsd += usd
                    pricedCount++
                    perCard.add(CardValueSummary(
                        nameSlug = nameSlug,
                        displayName = displayName,
                        quantity = qty,
                        marketValueEur = price.cardmarketMarketValue?.times(qty),
                        marketValueUsd = price.tcgplayerMarketValue?.times(qty),
                        change7d = price.cardmarketChange7d,
                    ))
                } else {
                    unpricedCount++
                    perCard.add(CardValueSummary(
                        nameSlug = nameSlug,
                        displayName = displayName,
                        quantity = qty,
                        marketValueEur = null,
                        marketValueUsd = null,
                        change7d = null,
                    ))
                }
            }

            CollectionValue(
                totalValueEur = totalEur,
                totalValueUsd = totalUsd,
                pricedCardCount = pricedCount,
                unpricedCardCount = unpricedCount,
                perCard = perCard.sortedByDescending { it.marketValueEur ?: 0.0 },
            )
        }
    }

    // ── Catalogue reads ────────────────────────────────────────────────

    fun catalogueCardsFlow(): Flow<List<CatalogueCardSummary>> {
        return combine(
            cardIdentityDao.getAll(),
            cardPrintingDao.getAll(),
            inventoryLineDao.getAll(),
            inventoryLocationDao.getAll(),
            locationPolicyDao.getAll(),
        ) { identities, printings, lines, locations, policies ->
            CatalogueFlowData(identities, printings, lines, locations, policies)
        }.combine(cardPriceDao.getAll()) { data, prices ->
            buildCatalogueSummaries(data.identities, data.printings, data.lines, data.locations, data.policies, prices)
        }
    }

    // ── Inventory reads ────────────────────────────────────────────────

    fun inventoryCardsFlow(): Flow<List<InventoryCardSummary>> {
        return combine(
            cardIdentityDao.getAll(),
            cardPrintingDao.getAll(),
            inventoryLineDao.getAll(),
            inventoryLocationDao.getAll(),
            locationPolicyDao.getAll(),
        ) { identities, printings, lines, locations, policies ->
            CatalogueFlowData(identities, printings, lines, locations, policies)
        }.combine(cardPriceDao.getAll()) { data, prices ->
            buildInventorySummaries(data.identities, data.printings, data.lines, data.locations, data.policies, prices)
        }
    }

    // ── Location reads/writes ──────────────────────────────────────────

    fun locationPoliciesFlow(): Flow<List<LocationPolicy>> {
        return locationPolicyDao.getAll().map { entities ->
            entities.map { e ->
                LocationPolicy(
                    name = e.name,
                    displayName = e.displayName,
                    color = e.color,
                    icon = e.icon,
                    kind = LocationKind.fromStorageValue(e.kind),
                    countsAsAvailable = e.countsAsAvailable,
                    hidden = e.hidden,
                )
            }
        }
    }

    suspend fun updateLocationPolicy(policy: LocationPolicy) = withContext(Dispatchers.IO) {
        locationPolicyDao.upsert(
            com.riftcompanion.app.data.db.LocationPolicyEntity(
                name = policy.name,
                displayName = policy.displayName,
                color = policy.color,
                icon = policy.icon,
                kind = policy.kind.storageValue,
                countsAsAvailable = policy.countsAsAvailable,
                hidden = policy.hidden,
            ),
        )
    }

    suspend fun deleteLocationPolicy(name: String) = withContext(Dispatchers.IO) {
        locationPolicyDao.delete(name)
    }

    // ── Credential verification ──────────────────────────────────────────

    /**
     * Verifies the stored API key by fetching locations (inventory:read).
     * Mirrors RiftBuilder's verifyCredential: saving verifies read only;
     * CardNexus will report a missing inventory:write scope when a physical
     * move is attempted.
     */
    suspend fun verifyCredential(): Result<Unit> = cardNexusClient.verifyCredential()

    // ── Banlist ────────────────────────────────────────────────────────

    fun banlistFlow(): Flow<List<BanlistEntry>> = flowOf(banlistFetcher.getStaticBanlist())

    suspend fun getBannedCardNames(): Set<String> = banlistFetcher.getStaticBanlist().filter { it.cardType == BanlistEntryType.CARD }.map { it.cardName }.toSet()
    suspend fun getBannedBattlefieldNames(): Set<String> = banlistFetcher.getStaticBanlist().filter { it.cardType == BanlistEntryType.BATTLEFIELD }.map { it.cardName }.toSet()

    suspend fun fetchBanlistNow(): Result<List<BanlistEntry>> = withContext(Dispatchers.IO) {
        Result.success(banlistFetcher.getStaticBanlist())
    }

    // ── Location API mutations ─────────────────────────────────────────

    suspend fun createLocation(name: String, color: String?, icon: String?): Result<InventoryLocation> =
        cardNexusClient.upsertLocation(
            com.riftcompanion.app.domain.model.InventoryLocationUpsertRequest(name, color, icon),
        )

    suspend fun updateLocation(
        currentName: String, newName: String, color: String?, icon: String?,
    ): Result<InventoryLocation> = cardNexusClient.updateLocation(
        com.riftcompanion.app.domain.model.InventoryLocationUpdateRequest(currentName, newName, color, icon),
    )

    suspend fun deleteLocation(name: String): Result<Unit> = cardNexusClient.deleteLocation(name)

    // ── Inventory quantity editing ───────────────────────────────────────

    suspend fun saveInventoryLocationQuantities(
        edits: List<InventoryLocationQuantityEdit>,
    ): Result<InventoryQuantityEditResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (edits.isEmpty()) throw Exception("No inventory quantity changes.")

            // Refresh inventory from API to get the latest state
            val lines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
            val locations = cardNexusClient.fetchLocations().getOrThrow()
            val printings = cardPrintingDao.getAll().first()

            val printingsByProduct = printings.associateBy { it.productID }
            val printingNameByProductID = printings.associate { it.productID to it.nameSlug }
            val destinationNames = locations.associate { it.name to it.name }

            val linesByName = lines.groupBy { printingNameByProductID[it.productId] ?: "" }

            data class MutableLine(
                val inventoryID: String,
                var locationKey: String,
                var quantity: Int,
            )

            data class Deficit(
                val locationKey: String,
                val displayName: String,
                var remaining: Int,
            )

            var allUpdates = mutableListOf<InventoryBulkMoveItem>()
            var allDeletions = mutableListOf<Pair<String, Int>>() // inventoryID to quantity
            var addedQuantity = 0
            var removedQuantity = 0
            var movedQuantity = 0

            for (edit in edits.sortedBy { it.nameSlug }) {
                for ((_, qty) in edit.quantitiesByLocation) {
                    if (qty < 0) throw Exception("Invalid quantity for ${edit.nameSlug}")
                }

                val cardLines = linesByName[edit.nameSlug]
                    ?.filter { it.quantity > 0 }
                    ?.sortedBy { it.id }
                    ?: throw Exception("No physical inventory lines found for '${edit.nameSlug}'.")

                var mutableLines = cardLines.map {
                    MutableLine(
                        inventoryID = it.id,
                        locationKey = it.location ?: "Unlocated",
                        quantity = it.quantity,
                    )
                }

                var currentByLocation = mutableMapOf<String, Int>()
                for (line in mutableLines) {
                    currentByLocation[line.locationKey] = (currentByLocation[line.locationKey] ?: 0) + line.quantity
                }

                val requestedByLocation = edit.quantitiesByLocation

                var deficits = requestedByLocation.mapNotNull { (locKey, requested) ->
                    val deficit = requested - (currentByLocation[locKey] ?: 0)
                    if (deficit <= 0) null
                    else if (locKey == "Unlocated") throw Exception("Cannot add cards to Unlocated.")
                    else if (locKey !in destinationNames) throw Exception("Unknown destination '$locKey'.")
                    else Deficit(locKey, locKey, deficit)
                }.sortedBy { it.locationKey }

                var surplusByLocation = currentByLocation.mapNotNull { (locKey, current) ->
                    val surplus = current - (requestedByLocation[locKey] ?: 0)
                    if (surplus > 0) locKey to surplus else null
                }.toMap().toMutableMap()

                val currentTotal = currentByLocation.values.sum()
                val requestedTotal = requestedByLocation.values.sum()
                var additionsRemaining = maxOf(0, requestedTotal - currentTotal)

                // Additions: increase existing lines at deficit locations, or use carrier lines
                for (i in deficits.indices) {
                    if (additionsRemaining <= 0 || deficits[i].remaining <= 0) continue
                    val quantity = minOf(additionsRemaining, deficits[i].remaining)
                    val targetIdx = mutableLines.indexOfFirst { it.quantity > 0 && it.locationKey == deficits[i].locationKey }
                    if (targetIdx >= 0) {
                        allUpdates.add(InventoryBulkMoveItem(inventoryID = mutableLines[targetIdx].inventoryID, quantityAdjustment = quantity))
                        mutableLines[targetIdx].quantity += quantity
                    } else {
                        val carrierIdx = mutableLines.indexOfFirst { it.quantity > 0 }
                        if (carrierIdx >= 0) {
                            allUpdates.add(InventoryBulkMoveItem(inventoryID = mutableLines[carrierIdx].inventoryID, quantityAdjustment = quantity))
                            mutableLines[carrierIdx].quantity += quantity
                            allUpdates.add(InventoryBulkMoveItem(inventoryID = mutableLines[carrierIdx].inventoryID, destinationLocationName = deficits[i].displayName, count = quantity))
                            mutableLines[carrierIdx].quantity -= quantity
                        } else {
                            throw Exception("Could not produce a complete plan for '${edit.nameSlug}'.")
                        }
                    }
                    addedQuantity += quantity
                    additionsRemaining -= quantity
                    deficits[i].remaining -= quantity
                }

                if (additionsRemaining != 0) {
                    throw Exception("Could not produce a complete plan for '${edit.nameSlug}'.")
                }

                // Moves: redistribute surplus to deficits
                for (lineIdx in mutableLines.indices) {
                    val sourceKey = mutableLines[lineIdx].locationKey
                    var availableSurplus = minOf(mutableLines[lineIdx].quantity, surplusByLocation[sourceKey] ?: 0)
                    if (availableSurplus <= 0) continue

                    for (di in deficits.indices) {
                        if (availableSurplus <= 0 || deficits[di].remaining <= 0) continue
                        val quantity = minOf(availableSurplus, deficits[di].remaining)
                        allUpdates.add(InventoryBulkMoveItem(inventoryID = mutableLines[lineIdx].inventoryID, destinationLocationName = deficits[di].displayName, count = quantity))
                        movedQuantity += quantity
                        availableSurplus -= quantity
                        surplusByLocation[sourceKey] = (surplusByLocation[sourceKey] ?: 0) - quantity
                        deficits[di].remaining -= quantity
                        if (quantity == mutableLines[lineIdx].quantity) {
                            mutableLines[lineIdx].locationKey = deficits[di].locationKey
                        } else {
                            mutableLines[lineIdx].quantity -= quantity
                        }
                    }
                }

                // Deletions / reductions for remaining surplus
                for (sourceKey in surplusByLocation.keys.sorted()) {
                    var remaining = surplusByLocation[sourceKey] ?: 0
                    if (remaining <= 0) continue
                    for (lineIdx in mutableLines.indices) {
                        if (remaining <= 0 || mutableLines[lineIdx].quantity <= 0 || mutableLines[lineIdx].locationKey != sourceKey) continue
                        val quantity = minOf(remaining, mutableLines[lineIdx].quantity)
                        if (quantity == mutableLines[lineIdx].quantity) {
                            allDeletions.add(mutableLines[lineIdx].inventoryID to quantity)
                            mutableLines[lineIdx].quantity = 0
                        } else {
                            allUpdates.add(InventoryBulkMoveItem(inventoryID = mutableLines[lineIdx].inventoryID, quantityAdjustment = -quantity))
                            mutableLines[lineIdx].quantity -= quantity
                        }
                        removedQuantity += quantity
                        remaining -= quantity
                    }
                    surplusByLocation[sourceKey] = remaining
                }

                if (deficits.any { it.remaining != 0 } || surplusByLocation.values.any { it != 0 }) {
                    throw Exception("Could not produce a complete plan for '${edit.nameSlug}'.")
                }
            }

            if (allUpdates.isEmpty() && allDeletions.isEmpty()) {
                throw Exception("No inventory quantity changes.")
            }

            // Batch updates (max 200 per batch, no duplicate inventory IDs per batch)
            var completedUpdates = 0
            var completedDeletions = 0
            val planId = java.util.UUID.randomUUID().toString()

            var pending = allUpdates
            var batchIndex = 0
            while (pending.isNotEmpty()) {
                val usedIDs = mutableSetOf<String>()
                val batch = mutableListOf<InventoryBulkMoveItem>()
                val deferred = mutableListOf<InventoryBulkMoveItem>()
                for (item in pending) {
                    if (batch.size < 200 && usedIDs.add(item.inventoryID)) {
                        batch.add(item)
                    } else {
                        deferred.add(item)
                    }
                }
                if (batch.isNotEmpty()) {
                    val response = cardNexusClient.bulkUpdateInventory(
                        InventoryBulkMoveRequest(
                            idempotencyKey = "riftcompanion-inventory-edit-$planId-$batchIndex",
                            moves = batch,
                        )
                    ).getOrThrow()
                    completedUpdates += response.updated
                }
                pending = deferred
                batchIndex++
            }

            for ((inventoryID, _) in allDeletions) {
                cardNexusClient.deleteInventoryLine(inventoryID).getOrThrow()
                completedDeletions++
            }

            // Refresh local DB after writes
            val refreshedLines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
            val refreshedLocations = cardNexusClient.fetchLocations().getOrThrow()
            inventoryLineDao.replaceApiLines(refreshedLines.map { EntityConverter.toEntity(it) })
            inventoryLocationDao.replaceAll(refreshedLocations.map { EntityConverter.toEntity(it) })

            InventoryQuantityEditResult(
                editedCardCount = edits.size,
                bulkUpdateCount = completedUpdates,
                deletedLineCount = completedDeletions,
                addedQuantity = addedQuantity,
                removedQuantity = removedQuantity,
                movedQuantity = movedQuantity,
                synchronizationWarning = null,
            )
        }
    }

    // ── Summary builders ───────────────────────────────────────────────

    private fun buildCatalogueSummaries(
        identities: List<com.riftcompanion.app.data.db.CardIdentityEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        locations: List<com.riftcompanion.app.data.db.InventoryLocationEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
        prices: List<com.riftcompanion.app.data.db.CardPriceEntity>,
    ): List<CatalogueCardSummary> {
        val identityMap = identities.associateBy { it.nameSlug }
        val printingsByName = printings.groupBy { it.nameSlug }
        val availabilityMap = buildAvailabilityMap(lines, policies, printings)
        val locationDisplayMap = locations.associateBy { it.name }
        val priceMap = prices.associateBy { it.productID }

        return identities.map { entity ->
            val identity = EntityConverter.toDomain(entity)
            val cardPrintings = printingsByName[entity.nameSlug] ?: emptyList()
            val preferred = cardPrintings.firstOrNull { it.imageURL != null } ?: cardPrintings.firstOrNull()
            val availability = availabilityMap[entity.nameSlug] ?: CardAvailability()
            val price = cardPrintings.firstNotNullOfOrNull { priceMap[it.productID] }

            CatalogueCardSummary(
                identity = identity,
                preferredPrinting = preferred?.let {
                    CataloguePrintingMetadata(
                        productID = it.productID,
                        printingSlug = it.printingSlug,
                        expansionSlug = it.expansionSlug,
                        printNumber = it.printNumber,
                        rarity = it.rarity,
                        imageURL = it.imageURL,
                    )
                },
                printingCount = cardPrintings.size,
                expansionSlugs = cardPrintings.mapNotNull { it.expansionSlug }.distinct(),
                rarities = cardPrintings.mapNotNull { it.rarity }.distinct(),
                totalOwned = availability.totalOwned,
                priceEur = price?.cardmarketMarketValue,
                priceUsd = price?.tcgplayerMarketValue,
                priceChange7d = price?.cardmarketChange7d,
            )
        }
    }

    private fun buildInventorySummaries(
        identities: List<com.riftcompanion.app.data.db.CardIdentityEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        locations: List<com.riftcompanion.app.data.db.InventoryLocationEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
        prices: List<com.riftcompanion.app.data.db.CardPriceEntity>,
    ): List<InventoryCardSummary> {
        val identityMap = identities.associateBy { it.nameSlug }
        val printingsByProduct = printings.associateBy { it.productID }
        val printingsByName = printings.groupBy { it.nameSlug }
        val policyMap = policies.associateBy { it.name }
        val locationDisplayMap = locations.associateBy { it.name }
        val priceMap = prices.associateBy { it.productID }

        // Group inventory lines by nameSlug (via productID → printing → nameSlug)
        val linesByName = lines.groupBy { line ->
            printingsByProduct[line.productId]?.nameSlug
        }.filterKeys { it != null }

        return linesByName.map { (nameSlug, cardLines) ->
            val nameSlugSafe = nameSlug ?: return@map null
            val identityEntity = identityMap[nameSlugSafe] ?: return@map null
            val identity = EntityConverter.toDomain(identityEntity)
            val cardPrintings = printingsByName[nameSlugSafe] ?: emptyList()
            val preferred = cardPrintings.firstOrNull { it.imageURL != null } ?: cardPrintings.firstOrNull()

            // Build availability
            val totalOwned = cardLines.sumOf { it.quantity }
            val locationQuantities = cardLines.filter { it.quantity > 0 }
                .groupBy { it.locationName ?: "Unlocated" }
                .map { (locName, locLines) ->
                    val policy = policyMap[locName]
                    val locEntity = locationDisplayMap[locName]
                    LocationQuantity(
                        locationName = locName,
                        displayName = locEntity?.displayName ?: policy?.displayName ?: locLines.firstOrNull()?.locationName ?: "Unlocated",
                        color = policy?.color ?: locEntity?.color,
                        icon = policy?.icon ?: locEntity?.icon,
                        kind = policy?.kind ?: "storage",
                        quantity = locLines.sumOf { it.quantity },
                        isAvailable = policy?.countsAsAvailable ?: true,
                    )
                }

            val availableInStorage = locationQuantities
                .filter { it.kind == "storage" && it.isAvailable }
                .sumOf { it.quantity }
            val otherwiseUnavailable = locationQuantities
                .filter { !it.isAvailable }
                .sumOf { it.quantity }

            val firstLine = cardLines.first()
            val price = cardPrintings.firstNotNullOfOrNull { priceMap[it.productID] }
            InventoryCardSummary(
                identity = identity,
                preferredImageURL = preferred?.imageURL,
                availability = CardAvailability(
                    totalOwned = totalOwned,
                    availableInStorage = availableInStorage,
                    otherwiseUnavailable = otherwiseUnavailable,
                ),
                locations = locationQuantities,
                expansion = preferred?.expansionSlug,
                rarity = preferred?.rarity,
                finish = firstLine.finish,
                language = firstLine.language,
                priceEur = price?.cardmarketMarketValue,
                priceUsd = price?.tcgplayerMarketValue,
                priceChange7d = price?.cardmarketChange7d,
            )
        }.filterNotNull()
    }

    private fun buildAvailabilityMap(
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
    ): Map<String, CardAvailability> {
        val policyMap = policies.associateBy { it.name }
        val productToNameSlug = printings.associate { it.productID to it.nameSlug }
        return lines.groupBy { productToNameSlug[it.productId] ?: "" }
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, productLines) ->
                val total = productLines.sumOf { it.quantity }
                val available = productLines
                    .filter { line ->
                        val policy = policyMap[line.locationName]
                        policy?.countsAsAvailable ?: true
                    }
                    .sumOf { it.quantity }
                CardAvailability(
                    totalOwned = total,
                    availableInStorage = available,
                )
            }
    }

    private fun extractStringList(value: com.riftcompanion.app.domain.model.JsonValue): List<String> {
        return when (value) {
            is com.riftcompanion.app.domain.model.JsonValue.Arr -> value.value.mapNotNull {
                (it as? com.riftcompanion.app.domain.model.JsonValue.Str)?.value
            }
            is com.riftcompanion.app.domain.model.JsonValue.Str -> listOf(value.value)
            else -> emptyList()
        }
    }
}

data class SyncResult(
    val catalogueImported: Boolean,
    val inventoryLines: Int,
    val locations: Int,
    val completedAt: Long,
)
