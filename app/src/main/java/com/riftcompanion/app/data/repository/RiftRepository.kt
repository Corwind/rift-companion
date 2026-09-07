package com.riftcompanion.app.data.repository

import com.riftcompanion.app.data.api.CardNexusClient
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.EntityConverter
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.data.db.SyncMetadataDao
import kotlinx.coroutines.flow.first
import com.riftcompanion.app.domain.model.CardAvailability
import com.riftcompanion.app.domain.model.CardIdentity
import com.riftcompanion.app.domain.model.CatalogueCardSummary
import com.riftcompanion.app.domain.model.CataloguePrintingMetadata
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.InventoryLine
import com.riftcompanion.app.domain.model.InventoryLocation
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.domain.model.LocationQuantity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
class RiftRepository @Inject constructor(
    private val cardNexusClient: CardNexusClient,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
    private val syncMetadataDao: SyncMetadataDao,
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
                            tags = first.attributes["tags"]?.let { extractStringList(it) } ?: emptyList(),
                            energyCost = first.attributes["energyCost"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Num)?.value?.toInt() },
                            mightCost = first.attributes["mightCost"]?.let { (it as? com.riftcompanion.app.domain.model.JsonValue.Num)?.value?.toInt() },
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

            inventoryLineDao.replaceAll(lines.map { EntityConverter.toEntity(it) })
            inventoryLocationDao.replaceAll(locations.map { EntityConverter.toEntity(it) })

            // 5. Auto-create location policies for new locations
            val existingPolicies = locationPolicyDao.getAll().first()
            val existingNames = existingPolicies.map { it.normalizedName }.toSet()
            locations.forEach { loc ->
                if (loc.normalizedName !in existingNames && loc.normalizedName != "__unlocated__") {
                    locationPolicyDao.upsert(
                        com.riftcompanion.app.data.db.LocationPolicyEntity(
                            normalizedName = loc.normalizedName,
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

    // ── Catalogue reads ────────────────────────────────────────────────

    fun catalogueCardsFlow(): Flow<List<CatalogueCardSummary>> {
        return combine(
            cardIdentityDao.getAll(),
            cardPrintingDao.getAll(),
            inventoryLineDao.getAll(),
            inventoryLocationDao.getAll(),
            locationPolicyDao.getAll(),
        ) { identities, printings, lines, locations, policies ->
            buildCatalogueSummaries(identities, printings, lines, locations, policies)
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
            buildInventorySummaries(identities, printings, lines, locations, policies)
        }
    }

    // ── Location reads/writes ──────────────────────────────────────────

    fun locationPoliciesFlow(): Flow<List<LocationPolicy>> {
        return locationPolicyDao.getAll().map { entities ->
            entities.map { e ->
                LocationPolicy(
                    normalizedName = e.normalizedName,
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
                normalizedName = policy.normalizedName,
                displayName = policy.displayName,
                color = policy.color,
                icon = policy.icon,
                kind = policy.kind.storageValue,
                countsAsAvailable = policy.countsAsAvailable,
                hidden = policy.hidden,
            ),
        )
    }

    suspend fun deleteLocationPolicy(normalizedName: String) = withContext(Dispatchers.IO) {
        locationPolicyDao.delete(normalizedName)
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

    // ── Summary builders ───────────────────────────────────────────────

    private fun buildCatalogueSummaries(
        identities: List<com.riftcompanion.app.data.db.CardIdentityEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        locations: List<com.riftcompanion.app.data.db.InventoryLocationEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
    ): List<CatalogueCardSummary> {
        val identityMap = identities.associateBy { it.nameSlug }
        val printingsByName = printings.groupBy { it.nameSlug }
        val availabilityMap = buildAvailabilityMap(lines, policies, printings)
        val locationDisplayMap = locations.associateBy { it.normalizedName }

        return identities.map { entity ->
            val identity = EntityConverter.toDomain(entity)
            val cardPrintings = printingsByName[entity.nameSlug] ?: emptyList()
            val preferred = cardPrintings.firstOrNull { it.imageURL != null } ?: cardPrintings.firstOrNull()
            val availability = availabilityMap[entity.nameSlug] ?: CardAvailability()

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
            )
        }
    }

    private fun buildInventorySummaries(
        identities: List<com.riftcompanion.app.data.db.CardIdentityEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        locations: List<com.riftcompanion.app.data.db.InventoryLocationEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
    ): List<InventoryCardSummary> {
        val identityMap = identities.associateBy { it.nameSlug }
        val printingsByProduct = printings.associateBy { it.productID }
        val printingsByName = printings.groupBy { it.nameSlug }
        val policyMap = policies.associateBy { it.normalizedName }
        val locationDisplayMap = locations.associateBy { it.normalizedName }

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
                .groupBy { com.riftcompanion.app.domain.model.InventoryLocation.normalize(it.locationName) }
                .map { (normName, locLines) ->
                    val policy = policyMap[normName]
                    val locEntity = locationDisplayMap[normName]
                    LocationQuantity(
                        normalizedLocationName = normName,
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
            )
        }.filterNotNull()
    }

    private fun buildAvailabilityMap(
        lines: List<com.riftcompanion.app.data.db.InventoryLineEntity>,
        policies: List<com.riftcompanion.app.data.db.LocationPolicyEntity>,
        printings: List<com.riftcompanion.app.data.db.CardPrintingEntity>,
    ): Map<String, CardAvailability> {
        val policyMap = policies.associateBy { it.normalizedName }
        val productToNameSlug = printings.associate { it.productID to it.nameSlug }
        return lines.groupBy { productToNameSlug[it.productId] ?: "" }
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, productLines) ->
                val total = productLines.sumOf { it.quantity }
                val available = productLines
                    .filter { line ->
                        val normName = com.riftcompanion.app.domain.model.InventoryLocation.normalize(line.locationName)
                        val policy = policyMap[normName]
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
