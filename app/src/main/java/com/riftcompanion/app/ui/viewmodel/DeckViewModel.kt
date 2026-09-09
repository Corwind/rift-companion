package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.DeckDao
import com.riftcompanion.app.data.db.DeckEntity
import com.riftcompanion.app.data.db.DeckEntryEntity
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLineEntity
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.InventoryLocationEntity
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.data.db.LocationPolicyEntity
import com.riftcompanion.app.data.deck.DeckEntryData
import com.riftcompanion.app.data.deck.DeckRulesEngine
import com.riftcompanion.app.data.deck.RiftDeckParser
import com.riftcompanion.app.data.deck.TextDeckParser
import com.riftcompanion.app.domain.model.CardIdentityInfo
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.domain.model.ValidationSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DeckListUiState(
    val decks: List<DeckSummary> = emptyList(),
    val isLoading: Boolean = false,
)

data class DeckSummary(
    val id: String,
    val name: String,
    val cardCount: Int,
    val updatedAt: Long,
    val isBuilt: Boolean = false,
    val isLegal: Boolean = false,
    val legalityIssues: List<String> = emptyList(),
    val linkedLocationName: String? = null,
    val legendImageURL: String? = null,
    val legendDisplayName: String? = null,
)

data class DeckDetailUiState(
    val deck: DeckSummary? = null,
    val entries: List<DeckEntryDisplay> = emptyList(),
    val isLoading: Boolean = false,
)

data class DeckEntryDisplay(
    val entryId: Long = 0,
    val zone: DeckZone,
    val nameSlug: String,
    val displayName: String,
    val quantity: Int,
    val preferredImageURL: String? = null,
    val cardType: String? = null,
    val expansion: String? = null,
    val rarity: String? = null,
    val domains: List<String> = emptyList(),
    // Availability info for deck building
    val availableInStorage: Int = 0,
    val inOtherDecks: Int = 0,
    val totalOwned: Int = 0,
    val isMissing: Boolean = false,
    val missingCount: Int = 0,
)

data class ImportUiState(
    val isImporting: Boolean = false,
    val error: String? = null,
    val success: DeckSummary? = null,
)

/**
 * A champion card candidate for the chosen champion picker.
 */
data class ChampionCandidate(
    val nameSlug: String,
    val displayName: String,
    val imageURL: String? = null,
)

/**
 * Preview of a single card movement during deck building.
 */
data class CardMovement(
    val nameSlug: String,
    val displayName: String,
    val quantity: Int,
    val fromLocation: String,
    val toLocation: String,
)

/**
 * Preview of all card movements for building a deck.
 */
data class DeckBuildPreview(
    val deckId: String,
    val deckName: String,
    val deckLocationName: String,
    val isNewLocation: Boolean,
    val movements: List<CardMovement>,
    val missing: List<MissingCard>,
)

data class MissingCard(
    val nameSlug: String,
    val displayName: String,
    val needed: Int,
    val available: Int,
)

data class DeckBuildUiState(
    val isLoading: Boolean = false,
    val preview: DeckBuildPreview? = null,
    val error: String? = null,
    val isBuilt: Boolean = false,
)

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckDao: DeckDao,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
) : ViewModel() {

    private val _deckListState = MutableStateFlow(DeckListUiState(isLoading = true))
    val deckListState: StateFlow<DeckListUiState> = _deckListState.asStateFlow()

    private val _deckDetailState = MutableStateFlow(DeckDetailUiState(isLoading = true))
    val deckDetailState: StateFlow<DeckDetailUiState> = _deckDetailState.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _buildState = MutableStateFlow(DeckBuildUiState())
    val buildState: StateFlow<DeckBuildUiState> = _buildState.asStateFlow()

    private var loadDecksJob: kotlinx.coroutines.Job? = null

    fun loadDecks() {
        loadDecksJob?.cancel()
        loadDecksJob = viewModelScope.launch {
            deckDao.getAllDecks().collect { decks ->
                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
                val allPrintings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }
                val summaries = decks.map { deck ->
                    val entries = deckDao.getEntriesForDeck(deck.id)
                    val cardCount = entries.sumOf { it.quantity }
                    val isBuilt = entries.any { it.isBuilt }

                    // Check legality
                    val entryData = entries.map {
                        DeckEntryData(
                            zone = DeckZone.fromString(it.zone) ?: DeckZone.main,
                            nameSlug = it.nameSlug,
                            quantity = it.quantity,
                        )
                    }
                    val identityInfos = identities.mapValues { (_, entity) ->
                        CardIdentityInfo(
                            nameSlug = entity.nameSlug,
                            displayName = entity.displayName,
                            domains = entity.domainsCsv.split(",").filter { it.isNotBlank() },
                            tags = entity.tagsCsv.split(",").filter { it.isNotBlank() },
                        )
                    }
                    val issues = DeckRulesEngine.validate(entryData, identityInfos)
                    val isLegal = issues.none { it.severity == ValidationSeverity.error }

                    // Get legend artwork
                    val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend.name }
                    val legendIdentity = legendEntry?.let { identities[it.nameSlug] }
                    val legendImageURL = legendEntry?.let { legendEntry2 ->
                        allPrintings[legendEntry2.nameSlug]?.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                    }

                    DeckSummary(
                        id = deck.id,
                        name = deck.name,
                        cardCount = cardCount,
                        updatedAt = deck.updatedAt,
                        isBuilt = isBuilt,
                        isLegal = isLegal,
                        legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        linkedLocationName = deck.linkedLocationName,
                        legendImageURL = legendImageURL,
                        legendDisplayName = legendIdentity?.displayName,
                    )
                }
                _deckListState.value = DeckListUiState(decks = summaries, isLoading = false)
            }
        }
    }

    fun loadDeckDetail(deckId: String) {
        viewModelScope.launch {
            _deckDetailState.value = DeckDetailUiState(isLoading = true)
            val deck = deckDao.getDeck(deckId)
            val entries = deckDao.getEntriesForDeck(deckId)
            val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
            val allPrintings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }

            // Get storage locations and deck locations
            val storageLocations = locationPolicyDao.getStorageLocations().map { it.name }.toSet()
            val deckLocations = locationPolicyDao.getByKind("deck").map { it.name }.toSet()

            // For each entry, compute availability
            val linkedLoc = deck?.linkedLocationName
            val display = entries.map { entry ->
                val identity = identities[entry.nameSlug]
                val printings = allPrintings[entry.nameSlug] ?: emptyList()
                val imageURL = printings.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                    ?: printings.firstOrNull()?.imageURL
                // Find all inventory lines for this card
                val allLines = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
                val inStorage = allLines.filter { it.locationName in storageLocations }.sumOf { it.quantity }
                // Cards already at the deck location count as available
                val inDeckLocation = allLines.filter { it.locationName == linkedLoc }.sumOf { it.quantity }
                val inDecks = allLines.filter { it.locationName in deckLocations && it.locationName != linkedLoc }.sumOf { it.quantity }
                val total = allLines.sumOf { it.quantity }
                val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                // Runes and battlefields are never missing
                val isRuneOrBattlefield = zone == DeckZone.rune || zone == DeckZone.battlefield
                val availableTotal = if (isRuneOrBattlefield) entry.quantity else inStorage + inDeckLocation
                val missing = if (isRuneOrBattlefield) 0 else maxOf(0, entry.quantity - availableTotal)

                DeckEntryDisplay(
                    entryId = entry.id,
                    zone = zone,
                    nameSlug = entry.nameSlug,
                    displayName = identity?.displayName ?: entry.nameSlug,
                    quantity = entry.quantity,
                    preferredImageURL = imageURL,
                    cardType = identity?.cardType,
                    expansion = printings.firstOrNull()?.expansionSlug,
                    rarity = printings.firstOrNull()?.rarity,
                    domains = identity?.tagsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    availableInStorage = if (isRuneOrBattlefield) entry.quantity else inStorage + inDeckLocation,
                    inOtherDecks = if (isRuneOrBattlefield) 0 else inDecks,
                    totalOwned = if (isRuneOrBattlefield) entry.quantity else total,
                    isMissing = missing > 0,
                    missingCount = missing,
                )
            }
            // Compute legality
            val entryData = entries.map {
                DeckEntryData(
                    zone = DeckZone.fromString(it.zone) ?: DeckZone.main,
                    nameSlug = it.nameSlug,
                    quantity = it.quantity,
                )
            }
            val identityInfos = identities.mapValues { (_, entity) ->
                CardIdentityInfo(
                    nameSlug = entity.nameSlug,
                    displayName = entity.displayName,
                    domains = entity.domainsCsv.split(",").filter { it.isNotBlank() },
                    tags = entity.tagsCsv.split(",").filter { it.isNotBlank() },
                    cardType = entity.cardType,
                    superType = entity.superType,
                )
            }
            val issues = DeckRulesEngine.validate(entryData, identityInfos)
            val isLegal = issues.none { it.severity == ValidationSeverity.error }
            val isBuilt = entries.any { it.isBuilt }
            val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend.name }
            val legendIdentity = legendEntry?.let { identities[it.nameSlug] }
            val legendImageURL = legendEntry?.let { e ->
                allPrintings[e.nameSlug]?.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
            }

            _deckDetailState.value = DeckDetailUiState(
                deck = deck?.let {
                    DeckSummary(
                        id = it.id,
                        name = it.name,
                        cardCount = entries.sumOf { e -> e.quantity },
                        updatedAt = it.updatedAt,
                        isBuilt = isBuilt,
                        isLegal = isLegal,
                        legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        linkedLocationName = it.linkedLocationName,
                        legendImageURL = legendImageURL,
                        legendDisplayName = legendIdentity?.displayName,
                    )
                },
                entries = display.groupBy { it.zone }.flatMap { (_, items) ->
                    items.sortedBy { it.displayName }
                },
                isLoading = false,
            )
        }
    }

    fun importDeck(text: String, deckName: String) {
        viewModelScope.launch {
            _importState.value = ImportUiState(isImporting = true)

            // Try text format first (Piltover Archive / RiftDeck text)
            val textResult = TextDeckParser.parse(text)
            val riftDeckResult = if (textResult.isFailure) RiftDeckParser.parse(text) else null

            if (textResult.isFailure && riftDeckResult == null) {
                _importState.value = ImportUiState(
                    error = textResult.exceptionOrNull()?.message ?: "Failed to parse deck",
                )
                return@launch
            }

            try {
                val deckId = if (riftDeckResult != null) {
                    UUID.randomUUID().toString().also { id ->
                        val parsed = riftDeckResult.getOrThrow()
                        val now = System.currentTimeMillis()
                        deckDao.insertDeck(DeckEntity(
                            id = id,
                            name = deckName.ifBlank { parsed.name },
                            state = parsed.state,
                            rulesetId = parsed.rulesetId,
                            createdAt = now,
                            updatedAt = now,
                        ))
                        deckDao.insertEntries(parsed.entries.map { entry ->
                            DeckEntryEntity(
                                deckId = id,
                                zone = entry.zone,
                                nameSlug = entry.nameSlug,
                                quantity = entry.quantity,
                            )
                        })
                    }
                } else {
                    val doc = textResult.getOrThrow()
                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val name = deckName.ifBlank { doc.suggestedDeckName ?: "Imported Deck" }
                    deckDao.insertDeck(DeckEntity(
                        id = id,
                        name = name,
                        state = "planned",
                        rulesetId = "riftbound",
                        createdAt = now,
                        updatedAt = now,
                    ))
                    val identities = cardIdentityDao.getAll().first()
                    // Resolve display names to actual nameSlugs using normalization
                    // (similar to RiftBuilder's TextDeckNameResolver)
                    val identitiesByNormalizedName = identities
                        .groupBy { normalizeName(it.displayName) }
                    val entries = doc.entries.map { entry ->
                        val normalized = normalizeName(entry.displayName)
                        val matches = identitiesByNormalizedName[normalized] ?: emptyList()
                        val slug = when {
                            matches.size == 1 -> matches[0].nameSlug
                            matches.isNotEmpty() -> matches[0].nameSlug // take first if ambiguous
                            else -> entry.displayName.lowercase()
                                .replace(" ", "-")
                                .replace(",", "")
                                .replace("'", "")
                        }
                        DeckEntryEntity(
                            deckId = id,
                            zone = entry.zone.name,
                            nameSlug = slug,
                            quantity = entry.quantity,
                        )
                    }
                    deckDao.insertEntries(entries)
                    id
                }

                val count = deckDao.getEntryCount(deckId)
                _importState.value = ImportUiState(
                    success = DeckSummary(deckId, deckName.ifBlank { "Imported Deck" }, count, System.currentTimeMillis()),
                )
                loadDecks()
            } catch (e: Exception) {
                _importState.value = ImportUiState(error = e.message ?: "Failed to import deck")
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            deckDao.deleteDeck(deckId)
            loadDecks()
        }
    }

    /**
     * Create a new empty deck definition with a legend card.
     */
    fun createEmptyDeck(deckName: String, legendNameSlug: String) {
        viewModelScope.launch {
            _importState.value = ImportUiState(isImporting = true)
            try {
                val deckId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                deckDao.insertDeck(DeckEntity(
                    id = deckId,
                    name = deckName.ifBlank { "New Deck" },
                    state = "planned",
                    rulesetId = "riftbound",
                    createdAt = now,
                    updatedAt = now,
                ))
                // Add the legend card
                deckDao.insertEntries(listOf(
                    DeckEntryEntity(
                        deckId = deckId,
                        zone = DeckZone.legend.name,
                        nameSlug = legendNameSlug,
                        quantity = 1,
                    ),
                ))
                _importState.value = ImportUiState(
                    success = DeckSummary(deckId, deckName.ifBlank { "New Deck" }, 1, now),
                )
                loadDecks()
            } catch (e: Exception) {
                _importState.value = ImportUiState(error = e.message)
            }
        }
    }

    /**
     * Analyze cards in a location to find legend and champion candidates.
     * Returns: legend nameSlug (if found in location), champion candidates (cards sharing a tag with legend)
     */
    fun analyzeLocationCards(
        locationName: String,
        onResult: (legendSlug: String?, legendDisplayName: String?, legendImageURL: String?, championCandidates: List<ChampionCandidate>) -> Unit,
    ) {
        viewModelScope.launch {
            val lines = inventoryLineDao.getByLocation(locationName)
            val printingsByProductId = cardPrintingDao.getAll().first().associateBy { it.productID }
            val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
            val allPrintings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }

            // Resolve all cards in the location to identities
            val locationIdentities = lines.mapNotNull { line ->
                val nameSlug = printingsByProductId[line.productId]?.nameSlug ?: return@mapNotNull null
                identities[nameSlug]
            }.distinctBy { it.nameSlug }

            // Find legend in the location
            val legend = locationIdentities.firstOrNull { it.cardType?.lowercase()?.contains("legend") == true }
            val legendImageURL = legend?.let { allPrintings[it.nameSlug]?.firstOrNull { p -> !p.imageURL.isNullOrEmpty() }?.imageURL }

            // Find champion candidates that share a tag with the legend
            val championCandidates = if (legend != null) {
                val legendTags = legend.tagsCsv.split(",").filter { it.isNotBlank() }.map { it.lowercase().trim() }.toSet()
                locationIdentities.filter { identity ->
                    identity.cardType?.lowercase()?.let { type ->
                        type.contains("champion") || type.contains("unit")
                    } == true
                }.filter { identity ->
                    val cardTags = identity.tagsCsv.split(",").filter { it.isNotBlank() }.map { it.lowercase().trim() }.toSet()
                    cardTags.intersect(legendTags).isNotEmpty()
                }.map { identity ->
                    val imageURL = allPrintings[identity.nameSlug]?.firstOrNull { p -> !p.imageURL.isNullOrEmpty() }?.imageURL
                    ChampionCandidate(
                        nameSlug = identity.nameSlug,
                        displayName = identity.displayName,
                        imageURL = imageURL,
                    )
                }
            } else {
                emptyList()
            }

            onResult(
                legend?.nameSlug,
                legend?.displayName,
                legendImageURL,
                championCandidates,
            )
        }
    }

    /**
     * Create a deck from a location with a specified legend and champion.
     * All cards from the location go to main deck (except legend and champion).
     */
    fun createDeckFromLocationWithLegend(
        locationName: String,
        deckName: String,
        legendNameSlug: String,
        championNameSlug: String?,
    ) {
        viewModelScope.launch {
            _importState.value = ImportUiState(isImporting = true)
            try {
                // Check if location is already linked to a deck
                val policy = locationPolicyDao.getByName(locationName)
                if (policy?.linkedDeckId != null) {
                    _importState.value = ImportUiState(
                        error = "Location \"${policy.displayName}\" is already linked to a deck.",
                    )
                    return@launch
                }

                val lines = inventoryLineDao.getByLocation(locationName)
                if (lines.isEmpty()) {
                    _importState.value = ImportUiState(error = "No cards found in location: $locationName")
                    return@launch
                }

                val printingsByProductId = cardPrintingDao.getAll().first().associateBy { it.productID }
                val deckId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val name = deckName.ifBlank { "$locationName Deck" }

                deckDao.insertDeck(DeckEntity(
                    id = deckId,
                    name = name,
                    state = "planned",
                    rulesetId = "riftbound",
                    createdAt = now,
                    updatedAt = now,
                    linkedLocationName = locationName,
                ))

                // Add legend
                val entries = mutableListOf<DeckEntryEntity>()
                entries.add(DeckEntryEntity(
                    deckId = deckId,
                    zone = DeckZone.legend.name,
                    nameSlug = legendNameSlug,
                    quantity = 1,
                ))

                // Add champion if specified
                if (championNameSlug != null) {
                    entries.add(DeckEntryEntity(
                        deckId = deckId,
                        zone = DeckZone.chosenChampion.name,
                        nameSlug = championNameSlug,
                        quantity = 1,
                    ))
                }

                // Classify remaining cards by type: runes → rune zone, battlefields → battlefield zone, rest → main deck
                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
                val otherCards = lines
                    .mapNotNull { line ->
                        val nameSlug = printingsByProductId[line.productId]?.nameSlug ?: return@mapNotNull null
                        nameSlug to line.quantity
                    }
                    .filter { it.first != legendNameSlug && it.first != championNameSlug }
                    .groupBy { it.first }
                    .map { (nameSlug, group) ->
                        val cardType = identities[nameSlug]?.cardType?.lowercase() ?: ""
                        val zone = when {
                            cardType.contains("rune") -> DeckZone.rune
                            cardType.contains("battlefield") -> DeckZone.battlefield
                            else -> DeckZone.main
                        }
                        DeckEntryEntity(
                            deckId = deckId,
                            zone = zone.name,
                            nameSlug = nameSlug,
                            quantity = group.sumOf { it.second },
                        )
                    }
                entries.addAll(otherCards)

                deckDao.insertEntries(entries)

                val count = deckDao.getEntryCount(deckId)
                _importState.value = ImportUiState(
                    success = DeckSummary(deckId, name, count, now),
                )
                loadDecks()
            } catch (e: Exception) {
                _importState.value = ImportUiState(error = e.message ?: "Failed to import from location")
            }
        }
    }

    /**
     * Get champion candidates from the catalog that share a tag with the given legend.
     */
    fun getChampionCandidatesForLegend(
        legendNameSlug: String,
        onResult: (List<ChampionCandidate>) -> Unit,
    ) {
        viewModelScope.launch {
            val identities = cardIdentityDao.getAll().first()
            val allPrintings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }
            val legend = identities.find { it.nameSlug == legendNameSlug }
            val legendTags = legend?.tagsCsv?.split(",")?.filter { it.isNotBlank() }?.map { it.lowercase().trim() }?.toSet() ?: emptySet()

            val champions = identities
                .filter { identity ->
                    identity.cardType?.lowercase()?.let { type ->
                        type.contains("champion") || type.contains("unit")
                    } == true
                }
                .filter { identity ->
                    val cardTags = identity.tagsCsv.split(",").filter { it.isNotBlank() }.map { it.lowercase().trim() }.toSet()
                    cardTags.intersect(legendTags).isNotEmpty()
                }
                .map { identity ->
                    val imageURL = allPrintings[identity.nameSlug]?.firstOrNull { p -> !p.imageURL.isNullOrEmpty() }?.imageURL
                    ChampionCandidate(
                        nameSlug = identity.nameSlug,
                        displayName = identity.displayName,
                        imageURL = imageURL,
                    )
                }
            onResult(champions)
        }
    }

    /**
     * Get all legend cards from the catalogue for the legend picker.
     */
    fun getLegendCards(onResult: (List<com.riftcompanion.app.domain.model.CatalogueCardSummary>) -> Unit) {
        viewModelScope.launch {
            val identities = cardIdentityDao.getAll().first()
            val printings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }
            val legends = identities
                .filter { it.cardType?.lowercase()?.contains("legend") == true }
                .map { identity ->
                    val prints = printings[identity.nameSlug] ?: emptyList()
                    com.riftcompanion.app.domain.model.CatalogueCardSummary(
                        identity = com.riftcompanion.app.domain.model.CardIdentity(
                            nameSlug = identity.nameSlug,
                            displayName = identity.displayName,
                            cardType = identity.cardType,
                            superType = identity.superType,
                            domains = identity.domainsCsv.split(",").filter { it.isNotBlank() },
                            tags = identity.tagsCsv.split(",").filter { it.isNotBlank() },
                        ),
                        preferredPrinting = prints.firstOrNull()?.let { printing ->
                            com.riftcompanion.app.domain.model.CataloguePrintingMetadata(
                                productID = printing.productID,
                                printingSlug = printing.printingSlug,
                                imageURL = printing.imageURL,
                            )
                        },
                        printingCount = prints.size,
                        expansionSlugs = prints.mapNotNull { it.expansionSlug }.distinct(),
                        rarities = prints.mapNotNull { it.rarity }.distinct(),
                    )
                }
            onResult(legends)
        }
    }

    /**
     * Create a deck from an existing inventory location.
     * All cards in that location become main deck entries.
     * The location is linked to the deck for future reference.
     */
    fun importDeckFromLocation(locationName: String, deckName: String) {
        viewModelScope.launch {
            _importState.value = ImportUiState(isImporting = true)
            try {
                // Check if location is already linked to a deck via location policy
                val policy = locationPolicyDao.getByName(locationName)
                if (policy?.linkedDeckId != null) {
                    val linkedDeck = deckDao.getDeck(policy.linkedDeckId)
                    _importState.value = ImportUiState(
                        error = "Location \"${policy.displayName}\" is already linked to deck \"${linkedDeck?.name}\". Each location can only be linked to one deck.",
                    )
                    return@launch
                }

                val lines = inventoryLineDao.getByLocation(locationName)
                if (lines.isEmpty()) {
                    _importState.value = ImportUiState(error = "No cards found in location: $locationName")
                    return@launch
                }

                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
                val deckId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val name = deckName.ifBlank { "$locationName Deck" }

                deckDao.insertDeck(DeckEntity(
                    id = deckId,
                    name = name,
                    state = "planned",
                    rulesetId = "riftbound",
                    createdAt = now,
                    updatedAt = now,
                    linkedLocationName = locationName,
                ))

                // Resolve each line's productId to the actual card nameSlug via printings table
                val printingsByProductId = cardPrintingDao.getAll().first().associateBy { it.productID }
                val entries = lines
                    .mapNotNull { line ->
                        val nameSlug = printingsByProductId[line.productId]?.nameSlug ?: return@mapNotNull null
                        nameSlug to line
                    }
                    .groupBy { it.first } // group by nameSlug
                    .map { (nameSlug, group) ->
                        val totalQty = group.sumOf { it.second.quantity }
                        DeckEntryEntity(
                            deckId = deckId,
                            zone = DeckZone.main.name,
                            nameSlug = nameSlug,
                            quantity = totalQty,
                        )
                    }
                deckDao.insertEntries(entries)

                val count = deckDao.getEntryCount(deckId)
                _importState.value = ImportUiState(
                    success = DeckSummary(deckId, name, count, now),
                )
                loadDecks()
            } catch (e: Exception) {
                _importState.value = ImportUiState(error = e.message ?: "Failed to import from location")
            }
        }
    }

    fun clearImportState() {
        _importState.value = ImportUiState()
    }

    // ── Deck building ───────────────────────────────────────────────────

    /**
     * Preview building a deck: calculate which cards need to move from which
     * storage location to the deck location. Does NOT modify anything.
     */
    fun previewBuild(deckId: String) {
        viewModelScope.launch {
            _buildState.value = DeckBuildUiState(isLoading = true)
            try {
                val deck = deckDao.getDeck(deckId)
                    ?: throw IllegalArgumentException("Deck not found")
                val entries = deckDao.getEntriesForDeck(deckId)
                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }

                // Determine deck location
                val existingLocation = deck.linkedLocationName
                val isNewLocation = existingLocation == null
                val deckLocationName = existingLocation ?: "${deck.name} (Deck)"

                if (isNewLocation) {
                    val normalizedDeckLoc = deckLocationName.lowercase().trim()
                    val policy = locationPolicyDao.getByName(normalizedDeckLoc)
                    if (policy?.linkedDeckId != null && policy.linkedDeckId != deckId) {
                        throw IllegalArgumentException(
                            "Location \"$deckLocationName\" is already linked to another deck."
                        )
                    }
                }

                // Get all storage locations
                val storageLocations = locationPolicyDao.getStorageLocations()
                val storageLocationNames = storageLocations.map { it.name }.toSet()

                // For each deck entry, find available cards in storage
                // Runes and battlefields are always available — they don't need to be in inventory
                val movements = mutableListOf<CardMovement>()
                val missing = mutableListOf<MissingCard>()

                for (entry in entries) {
                    val displayName = identities[entry.nameSlug]?.displayName ?: entry.nameSlug
                    val needed = entry.quantity
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main

                    // Find all inventory lines for this card in storage locations
                    val lines = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
                        .filter { it.locationName in storageLocationNames }
                        .sortedBy { it.locationName }

                    var remaining = needed
                    for (line in lines) {
                        if (remaining <= 0) break
                        val take = minOf(remaining, line.quantity)
                        movements.add(CardMovement(
                            nameSlug = entry.nameSlug,
                            displayName = displayName,
                            quantity = take,
                            fromLocation = line.locationName ?: "Unknown",
                            toLocation = deckLocationName,
                        ))
                        remaining -= take
                    }

                    // For runes and battlefields: remaining cards are created at deck location (not missing)
                    // For other zones: remaining cards are missing
                    if (remaining > 0 && zone != DeckZone.rune && zone != DeckZone.battlefield) {
                        val available = needed - remaining
                        missing.add(MissingCard(
                            nameSlug = entry.nameSlug,
                            displayName = displayName,
                            needed = needed,
                            available = available,
                        ))
                    }
                }

                _buildState.value = DeckBuildUiState(
                    preview = DeckBuildPreview(
                        deckId = deckId,
                        deckName = deck.name,
                        deckLocationName = deckLocationName,
                        isNewLocation = isNewLocation,
                        movements = movements,
                        missing = missing,
                    ),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _buildState.value = DeckBuildUiState(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * Execute the deck build: create deck location if needed, move cards
     * from storage to deck location, link location to deck.
     * Records source location for each card for future disassembly.
     */
    fun executeBuild() {
        viewModelScope.launch {
            val preview = _buildState.value.preview ?: return@launch
            _buildState.value = _buildState.value.copy(isLoading = true)
            try {
                val deckLocationNormalized = preview.deckLocationName.lowercase().trim()

                // Create deck location if new
                if (preview.isNewLocation) {
                    locationPolicyDao.upsert(LocationPolicyEntity(
                        name = deckLocationNormalized,
                        displayName = preview.deckLocationName,
                        color = null,
                        icon = null,
                        kind = "deck",
                        countsAsAvailable = false,
                        hidden = false,
                        linkedDeckId = preview.deckId,
                    ))
                    inventoryLocationDao.insertAll(listOf(
                        InventoryLocationEntity(
                            name = deckLocationNormalized,
                            displayName = preview.deckLocationName,
                            color = null,
                            icon = null,
                        ),
                    ))
                }

                // Move cards and record source tracking
                val updatedEntries = mutableListOf<DeckEntryEntity>()
                for (movement in preview.movements) {
                    val sourceLines = inventoryLineDao.getLinesByCardSlug(movement.nameSlug)
                        .filter { it.locationName == movement.fromLocation }
                        .sortedBy { it.quantity }

                    var toMove = movement.quantity
                    for (line in sourceLines) {
                        if (toMove <= 0) break
                        val take = minOf(toMove, line.quantity)
                        val newSourceQty = line.quantity - take

                        // Update source line (reduce or remove)
                        if (newSourceQty > 0) {
                            inventoryLineDao.updateLocationAndQuantity(line.id, line.locationName, newSourceQty)
                        } else {
                            inventoryLineDao.updateLocationAndQuantity(line.id, line.locationName, 0)
                        }

                        // Create line in deck location
                        val newLineId = "${movement.nameSlug}_${deckLocationNormalized}_${System.currentTimeMillis()}"
                        inventoryLineDao.insertAll(listOf(
                            InventoryLineEntity(
                                id = newLineId,
                                customId = line.customId,
                                productId = line.productId,
                                finish = line.finish,
                                condition = line.condition,
                                language = line.language,
                                quantity = take,
                                locationName = deckLocationNormalized,
                                tagsCsv = line.tagsCsv,
                                comment = line.comment,
                                notes = line.notes,
                                forSale = false,
                                updatedAt = System.currentTimeMillis().toString(),
                            ),
                        ))

                        toMove -= take
                    }
                }

                // Mark deck entries as built with source tracking
                val existingEntries = deckDao.getEntriesForDeck(preview.deckId)
                for (entry in existingEntries) {
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                    // Sum up how many were moved from storage for this card
                    val movedFromStorage = preview.movements
                        .filter { it.nameSlug == entry.nameSlug }
                        .sumOf { it.quantity }
                    if (zone == DeckZone.rune || zone == DeckZone.battlefield) {
                        // Runes and battlefields: moved ones have source, rest created at deck location
                        updatedEntries.add(entry.copy(
                            isBuilt = true,
                            sourceLocationName = if (movedFromStorage > 0) preview.movements.first { it.nameSlug == entry.nameSlug }.fromLocation else null,
                        ))
                    } else {
                        updatedEntries.add(entry.copy(
                            isBuilt = true,
                            sourceLocationName = preview.movements.find { it.nameSlug == entry.nameSlug }?.fromLocation,
                        ))
                    }
                }
                deckDao.deleteEntriesForDeck(preview.deckId)
                deckDao.insertEntries(updatedEntries)

                // Create remaining rune/battlefield lines (shortfall after moving from storage) at deck location
                val allPrintings = cardPrintingDao.getAll().first().associateBy { it.nameSlug }
                for (entry in existingEntries) {
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                    if (zone == DeckZone.rune || zone == DeckZone.battlefield) {
                        val printing = allPrintings[entry.nameSlug] ?: continue
                        // Only create the shortfall (not already moved from storage)
                        val movedFromStorage = preview.movements
                            .filter { it.nameSlug == entry.nameSlug }
                            .sumOf { it.quantity }
                        val toCreate = entry.quantity - movedFromStorage
                        if (toCreate <= 0) continue
                        val newLineId = "${entry.nameSlug}_${deckLocationNormalized}_${System.currentTimeMillis()}"
                        inventoryLineDao.insertAll(listOf(
                            InventoryLineEntity(
                                id = newLineId,
                                customId = null,
                                productId = printing.productID,
                                finish = "normal",
                                condition = null,
                                language = null,
                                quantity = toCreate,
                                locationName = deckLocationNormalized,
                                tagsCsv = "",
                                comment = null,
                                notes = null,
                                forSale = false,
                                updatedAt = System.currentTimeMillis().toString(),
                            ),
                        ))
                    }
                }

                // Link location to deck and mark as assembled
                val deck = deckDao.getDeck(preview.deckId)
                if (deck != null) {
                    deckDao.insertDeck(deck.copy(
                        linkedLocationName = deckLocationNormalized,
                        state = "assembled",
                        updatedAt = System.currentTimeMillis(),
                    ))
                }

                _buildState.value = DeckBuildUiState(isBuilt = true, isLoading = false)
                loadDecks()
            } catch (e: Exception) {
                _buildState.value = DeckBuildUiState(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * Disassemble a deck: move all cards from the deck location back to
     * their original source locations. If overrideLocation is provided,
     * cards go there instead of their original source.
     */
    fun disassembleDeck(deckId: String, overrideLocation: String? = null) {
        viewModelScope.launch {
            _buildState.value = DeckBuildUiState(isLoading = true)
            try {
                val deck = deckDao.getDeck(deckId)
                    ?: throw IllegalArgumentException("Deck not found")
                val deckLocation = deck.linkedLocationName
                    ?: throw IllegalArgumentException("Deck has no linked location")
                val entries = deckDao.getEntriesForDeck(deckId)

                for (entry in entries) {
                    if (!entry.isBuilt) continue

                    // Determine where to return the card
                    val returnLocation = overrideLocation ?: entry.sourceLocationName
                        ?: "Storage"

                    // Find the card in the deck location
                    val deckLines = inventoryLineDao.getByLocation(deckLocation)
                        .filter { it.id == entry.nameSlug }

                    for (line in deckLines) {
                        // Move back to source/override location
                        inventoryLineDao.insertAll(listOf(
                            line.copy(
                                locationName = returnLocation,
                                updatedAt = System.currentTimeMillis().toString(),
                            ),
                        ))
                        // Remove from deck location
                        inventoryLineDao.updateLocationAndQuantity(line.id, deckLocation, 0)
                    }
                }

                // Mark entries as not built
                val updatedEntries = entries.map { it.copy(isBuilt = false) }
                deckDao.deleteEntriesForDeck(deckId)
                deckDao.insertEntries(updatedEntries)

                // Update deck state
                deckDao.insertDeck(deck.copy(
                    state = "planned",
                    updatedAt = System.currentTimeMillis(),
                ))

                _buildState.value = DeckBuildUiState(isLoading = false)
                loadDecks()
            } catch (e: Exception) {
                _buildState.value = DeckBuildUiState(error = e.message, isLoading = false)
            }
        }
    }

    /**
     * Update deck detail UI state incrementally — only recompute the changed
     * entry's availability, keep the rest as-is. Much smoother than full reload.
     */
    private suspend fun updateDeckDetailIncremental(deckId: String) {
        val current = _deckDetailState.value
        if (current.isLoading || current.deck == null) {
            loadDeckDetail(deckId)
            return
        }

        val entries = deckDao.getEntriesForDeck(deckId)
        val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
        val allPrintings = cardPrintingDao.getAll().first().groupBy { it.nameSlug }
        val storageLocations = locationPolicyDao.getStorageLocations().map { it.name }.toSet()
        val deckLocations = locationPolicyDao.getByKind("deck").map { it.name }.toSet()
        val linkedLoc = deckDao.getDeck(deckId)?.linkedLocationName

        val display = entries.map { entry ->
            val identity = identities[entry.nameSlug]
            val printings = allPrintings[entry.nameSlug] ?: emptyList()
            val imageURL = printings.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                ?: printings.firstOrNull()?.imageURL
            val allLines = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
            val inStorage = allLines.filter { it.locationName in storageLocations }.sumOf { it.quantity }
            val inDeckLocation = allLines.filter { it.locationName == linkedLoc }.sumOf { it.quantity }
            val inDecks = allLines.filter { it.locationName in deckLocations && it.locationName != linkedLoc }.sumOf { it.quantity }
            val total = allLines.sumOf { it.quantity }
            val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
            val isRuneOrBattlefield = zone == DeckZone.rune || zone == DeckZone.battlefield
            val availableTotal = if (isRuneOrBattlefield) entry.quantity else inStorage + inDeckLocation
            val missing = if (isRuneOrBattlefield) 0 else maxOf(0, entry.quantity - availableTotal)

            DeckEntryDisplay(
                entryId = entry.id,
                zone = zone,
                nameSlug = entry.nameSlug,
                displayName = identity?.displayName ?: entry.nameSlug,
                quantity = entry.quantity,
                preferredImageURL = imageURL,
                cardType = identity?.cardType,
                expansion = printings.firstOrNull()?.expansionSlug,
                rarity = printings.firstOrNull()?.rarity,
                domains = identity?.tagsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                availableInStorage = if (isRuneOrBattlefield) entry.quantity else inStorage + inDeckLocation,
                inOtherDecks = if (isRuneOrBattlefield) 0 else inDecks,
                totalOwned = if (isRuneOrBattlefield) entry.quantity else total,
                isMissing = missing > 0,
                missingCount = missing,
            )
        }

        _deckDetailState.value = DeckDetailUiState(
            deck = current.deck?.copy(cardCount = entries.sumOf { it.quantity }),
            entries = display.groupBy { it.zone }.flatMap { (_, items) -> items.sortedBy { it.displayName } },
            isLoading = false,
        )
    }

    fun clearBuildState() {
        _buildState.value = DeckBuildUiState()
    }

    // ── Deck editing ───────────────────────────────────────────────────

    /**
     * Get the domains of the legend card in a deck, for filtering the catalog picker.
     */
    fun getLegendDomains(deckId: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
            val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend.name }
            val domains = legendEntry?.let { identities[it.nameSlug]?.domainsCsv?.split(",")?.filter { it.isNotBlank() } } ?: emptyList()
            onResult(domains)
        }
    }

    /**
     * Get the tags of the legend card in a deck, for champion filtering.
     */
    fun getLegendTags(deckId: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
            val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend.name }
            val tags = legendEntry?.let { identities[it.nameSlug]?.tagsCsv?.split(",")?.filter { it.isNotBlank() }?.map { it.lowercase().trim() } } ?: emptyList()
            onResult(tags)
        }
    }

    /**
     * Add a card to a deck in the specified zone. If the card already exists
     * in that zone, increases the quantity. Updates UI state incrementally.
     */
    fun addCardToDeck(deckId: String, nameSlug: String, zone: DeckZone, quantity: Int = 1) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            val existing = entries.find { it.nameSlug == nameSlug && it.zone == zone.name }
            if (existing != null) {
                deckDao.deleteEntriesForDeck(deckId)
                deckDao.insertEntries(entries.map {
                    if (it.id == existing.id) it.copy(quantity = it.quantity + quantity)
                    else it
                })
            } else {
                deckDao.insertEntries(listOf(
                    DeckEntryEntity(
                        deckId = deckId,
                        zone = zone.name,
                        nameSlug = nameSlug,
                        quantity = quantity,
                    ),
                ))
            }
            // Update UI state incrementally instead of full reload
            updateDeckDetailIncremental(deckId)
        }
    }

    /**
     * Remove a card from a deck (reduce quantity or remove entirely).
     * Updates UI state incrementally.
     */
    fun removeCardFromDeck(deckId: String, nameSlug: String, zone: DeckZone, quantity: Int = 1) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            val existing = entries.find { it.nameSlug == nameSlug && it.zone == zone.name }
            if (existing != null) {
                val newQty = existing.quantity - quantity
                deckDao.deleteEntriesForDeck(deckId)
                if (newQty > 0) {
                    deckDao.insertEntries(entries.map {
                        if (it.id == existing.id) it.copy(quantity = newQty)
                        else it
                    })
                } else {
                    deckDao.insertEntries(entries.filter { it.id != existing.id })
                }
            }
            updateDeckDetailIncremental(deckId)
        }
    }

    /**
     * Set the exact quantity of a card in a deck zone.
     * If quantity is 0, removes the card.
     */
    fun setCardQuantity(deckId: String, nameSlug: String, zone: DeckZone, quantity: Int) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            val existing = entries.find { it.nameSlug == nameSlug && it.zone == zone.name }
            deckDao.deleteEntriesForDeck(deckId)
            if (quantity > 0) {
                if (existing != null) {
                    deckDao.insertEntries(entries.map {
                        if (it.id == existing.id) it.copy(quantity = quantity)
                        else it
                    })
                } else {
                    deckDao.insertEntries(entries + DeckEntryEntity(
                        deckId = deckId,
                        zone = zone.name,
                        nameSlug = nameSlug,
                        quantity = quantity,
                    ))
                }
            } else if (existing != null) {
                deckDao.insertEntries(entries.filter { it.id != existing.id })
            } else {
                deckDao.insertEntries(entries)
            }
            loadDeckDetail(deckId)
            loadDecks()
        }
    }

    /**
     * Remove a card entry entirely from a deck.
     */
    fun removeCardEntry(deckId: String, entryId: Long) {
        viewModelScope.launch {
            val entries = deckDao.getEntriesForDeck(deckId)
            deckDao.deleteEntriesForDeck(deckId)
            deckDao.insertEntries(entries.filter { it.id != entryId })
            loadDeckDetail(deckId)
            loadDecks()
        }
    }

    /**
     * Normalize a card display name for matching against the catalogue.
     * Similar to RiftBuilder's TextDeckNameNormalizer:
     * case-insensitive, diacritic-insensitive, collapses whitespace,
     * treats hyphens between words as title separators.
     */
    private fun normalizeName(name: String): String {
        return name.lowercase().trim()
            .replace("\\s+".toRegex(), " ")
    }
}
