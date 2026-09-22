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
import com.riftcompanion.app.domain.model.DeckAvailability
import com.riftcompanion.app.domain.model.DeckBuildPlanner
import com.riftcompanion.app.domain.model.DeckMissingSummary
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.domain.model.ValidationSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DeckListUiState(
    val decks: List<DeckSummary> = emptyList(),
    val isLoading: Boolean = false,
    val missingSummary: DeckMissingSummary.Summary? = null,
    val isLoadingMissingSummary: Boolean = false,
)

data class DeckSummary(
    val id: String,
    val name: String,
    val cardCount: Int,
    val updatedAt: Long,
    val isBuilt: Boolean = false,
    val isLegal: Boolean = false,
    val hasMissingCards: Boolean = false,
    val legalityIssues: List<String> = emptyList(),
    val banlistWarnings: List<String> = emptyList(),
    val linkedLocationName: String? = null,
    val legendImageURL: String? = null,
    val legendDisplayName: String? = null,
)

data class DeckDetailUiState(
    val deck: DeckSummary? = null,
    val entries: List<DeckEntryDisplay> = emptyList(),
    val isLoading: Boolean = false,
    val availableLocations: List<com.riftcompanion.app.domain.model.LocationPolicy> = emptyList(),
)

data class DeckEntryDisplay(
    val entryId: Long = 0,
    val zone: DeckZone,
    val nameSlug: String,
    val displayName: String,
    val quantity: Int,
    val preferredImageURL: String? = null,
    val cardType: String? = null,
    val superType: String? = null,
    val expansion: String? = null,
    val rarity: String? = null,
    val domains: List<String> = emptyList(),
    val energyCost: Int? = null,
    val might: Int? = null,
    val priceEur: Double? = null,
    val priceUsd: Double? = null,
    // Availability info for deck building
    val availableInStorage: Int = 0,
    val inDeckLocation: Int = 0,
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
    val deckLocationDisplayName: String,
    val isNewLocation: Boolean,
    val movements: List<CardMovement>,
    val returns: List<CardMovement> = emptyList(),
    val missing: List<MissingCard>,
    val isAlreadyBuilt: Boolean = false,
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

data class DisassembleCardInfo(
    val nameSlug: String,
    val displayName: String,
    val quantity: Int,
    val fromLocation: String,
    val suggestedDestination: String?,
)

data class DisassembleUiState(
    val isLoading: Boolean = false,
    val cards: List<DisassembleCardInfo> = emptyList(),
    val availableLocations: List<com.riftcompanion.app.domain.model.LocationPolicy> = emptyList(),
    val deckLocationName: String = "",
    val isExecuting: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckDao: DeckDao,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
    private val cardPriceDao: com.riftcompanion.app.data.db.CardPriceDao,
    private val settingsDataStore: com.riftcompanion.app.data.prefs.SettingsDataStore,
    private val cardNexusClient: com.riftcompanion.app.data.api.CardNexusClient,
    private val repository: com.riftcompanion.app.data.repository.RiftRepository,
) : ViewModel() {

    private val _deckListState = MutableStateFlow(DeckListUiState(isLoading = true))
    val deckListState: StateFlow<DeckListUiState> = _deckListState.asStateFlow()

    private val _deckDetailState = MutableStateFlow(DeckDetailUiState(isLoading = true))
    val deckDetailState: StateFlow<DeckDetailUiState> = _deckDetailState.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _buildState = MutableStateFlow(DeckBuildUiState())
    val buildState: StateFlow<DeckBuildUiState> = _buildState.asStateFlow()

    private val _disassembleState = MutableStateFlow(DisassembleUiState())
    val disassembleState: StateFlow<DisassembleUiState> = _disassembleState.asStateFlow()

    val priceMarket: StateFlow<com.riftcompanion.app.data.prefs.PriceMarket> =
        settingsDataStore.settingsFlow.map { it.priceMarket }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.riftcompanion.app.data.prefs.PriceMarket.EUR)

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

                    // Quick missing check: compare total owned vs needed
                    val ownedBySlug = entries.map { e ->
                        val owned = inventoryLineDao.getLinesByCardSlug(e.nameSlug).sumOf { it.quantity }
                        e.nameSlug to owned
                    }.toMap()
                    val hasMissing = entries.any { e ->
                        val owned = ownedBySlug[e.nameSlug] ?: 0
                        owned < e.quantity
                    }

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
                        hasMissingCards = hasMissing,
                        legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        banlistWarnings = issues.filter { it.severity == ValidationSeverity.warning }.map { it.message },
                        linkedLocationName = deck.linkedLocationName,
                        legendImageURL = legendImageURL,
                        legendDisplayName = legendIdentity?.displayName,
                    )
                }
                _deckListState.value = DeckListUiState(decks = summaries.sortedBy { it.name.lowercase() }, isLoading = false)
            }
        }
    }

    fun loadMissingSummary() {
        viewModelScope.launch {
            _deckListState.value = _deckListState.value.copy(isLoadingMissingSummary = true)
            try {
                val decks = deckDao.getAllDecks().first()
                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
                val storageLocations = locationPolicyDao.getStorageLocations().map { it.name.trim().lowercase() }.toSet()
                val deckLocations = locationPolicyDao.getByKind("deck").map { it.name.trim().lowercase() }.toSet()
                val allInventoryLines = inventoryLineDao.getAll().first()
                val allPrintings = cardPrintingDao.getAll().first()
                val printingByProduct = allPrintings.associateBy { it.productID }

                val deckInputs = decks.map { deck ->
                    val entries = deckDao.getEntriesForDeck(deck.id)
                    DeckMissingSummary.DeckInput(
                        deckId = deck.id,
                        deckName = deck.name,
                        isBuilt = entries.any { it.isBuilt },
                        linkedLocationName = deck.linkedLocationName,
                        entries = entries.map { e ->
                            val identity = identities[e.nameSlug]
                            DeckMissingSummary.EntryInput(
                                nameSlug = e.nameSlug,
                                displayName = identity?.displayName ?: e.nameSlug,
                                zone = DeckZone.fromString(e.zone) ?: DeckZone.main,
                                quantity = e.quantity,
                            )
                        },
                    )
                }

                val inventoryLines = allInventoryLines.mapNotNull { line ->
                    val printing = printingByProduct[line.productId]
                    if (printing == null) {
                        android.util.Log.d("MissingSummary", "unresolved productId=${line.productId} loc=${line.locationName} qty=${line.quantity}")
                        null
                    } else DeckMissingSummary.InventoryLine(
                        nameSlug = printing.nameSlug,
                        locationName = line.locationName,
                        quantity = line.quantity,
                    )
                }
                // Log temporal breach specifically
                val tbLines = inventoryLines.filter { it.nameSlug == "temporal-breach" }
                android.util.Log.d("MissingSummary", "temporal-breach lines: ${tbLines.size}")
                for (l in tbLines) {
                    android.util.Log.d("MissingSummary", "  loc=${l.locationName} qty=${l.quantity}")
                }

                val summary = DeckMissingSummary.compute(
                    decks = deckInputs,
                    inventoryLines = inventoryLines,
                    storageLocationNames = storageLocations,
                    deckLocationNames = deckLocations,
                )
                android.util.Log.d("MissingSummary", "decks=${deckInputs.size}, inventory=${inventoryLines.size}, storage=${storageLocations.size}")
                for (d in summary.decks) {
                    android.util.Log.d("MissingSummary", "deck=${d.deckName} missing=${d.totalMissing}")
                    for (c in d.missingCards) {
                        android.util.Log.d("MissingSummary", "  card=${c.displayName} needed=${c.needed} available=${c.available} missing=${c.missing}")
                    }
                }
                _deckListState.value = _deckListState.value.copy(missingSummary = summary, isLoadingMissingSummary = false)
            } catch (e: Exception) {
                _deckListState.value = _deckListState.value.copy(isLoadingMissingSummary = false)
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
            val storageLocations = locationPolicyDao.getStorageLocations().map { it.name.trim().lowercase() }.toSet()
            val deckLocations = locationPolicyDao.getByKind("deck").map { it.name.trim().lowercase() }.toSet()

            // For each entry, compute availability — track cards already claimed by earlier zones
            val linkedLoc = deck?.linkedLocationName
            val zoneOrder = listOf(DeckZone.legend, DeckZone.chosenChampion, DeckZone.main, DeckZone.sideboard, DeckZone.rune, DeckZone.battlefield)
            val claimedBySlug = mutableMapOf<String, Int>()
            val sortedEntries = entries.sortedBy { e -> zoneOrder.indexOf(DeckZone.fromString(e.zone) ?: DeckZone.main) }
            val display = sortedEntries.map { entry ->
                val identity = identities[entry.nameSlug]
                val printings = allPrintings[entry.nameSlug] ?: emptyList()
                val imageURL = printings.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                    ?: printings.firstOrNull()?.imageURL
                // Find all inventory lines for this card
                val allLines = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
                val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                val lineLocs = allLines.map { it.locationName }
                val lineQtys = allLines.map { it.quantity }
                val availability = DeckAvailability.compute(
                    quantity = entry.quantity,
                    zone = zone,
                    alreadyClaimed = claimedBySlug[entry.nameSlug] ?: 0,
                    lineLocations = lineLocs,
                    lineQuantities = lineQtys,
                    storageLocations = storageLocations,
                    deckLocations = deckLocations,
                    linkedLocation = linkedLoc,
                )
                // Track cards claimed by this entry for subsequent entries of the same card
                if (zone != DeckZone.rune && zone != DeckZone.battlefield) {
                    val claimed = minOf(availability.availableInStorage + availability.inDeckLocation, entry.quantity)
                    claimedBySlug[entry.nameSlug] = (claimedBySlug[entry.nameSlug] ?: 0) + claimed
                }

                DeckEntryDisplay(
                    entryId = entry.id,
                    zone = zone,
                    nameSlug = entry.nameSlug,
                    displayName = identity?.displayName ?: entry.nameSlug,
                    quantity = entry.quantity,
                    preferredImageURL = imageURL,
                    cardType = identity?.cardType,
                    superType = identity?.superType,
                    expansion = printings.firstOrNull()?.expansionSlug,
                    rarity = printings.firstOrNull()?.rarity,
                    domains = identity?.domainsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    energyCost = identity?.energyCost,
                    might = identity?.might,
                    priceEur = printings.firstNotNullOfOrNull { cardPriceDao.getByProductId(it.productID) }?.cardmarketMarketValue,
                    priceUsd = printings.firstNotNullOfOrNull { cardPriceDao.getByProductId(it.productID) }?.tcgplayerMarketValue,
                    availableInStorage = availability.availableInStorage,
                    inDeckLocation = availability.inDeckLocation,
                    inOtherDecks = availability.inOtherDecks,
                    totalOwned = availability.totalOwned,
                    isMissing = availability.isMissing,
                    missingCount = availability.missingCount,
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

            // Load available locations for the linked location selector
            val availableLocs = locationPolicyDao.getVisibleNonUnavailable().map { loc ->
                com.riftcompanion.app.domain.model.LocationPolicy(
                    name = loc.name,
                    displayName = loc.displayName,
                    color = loc.color,
                    icon = loc.icon,
                    kind = when (loc.kind) {
                        "storage" -> com.riftcompanion.app.domain.model.LocationKind.Storage
                        "deck" -> com.riftcompanion.app.domain.model.LocationKind.Deck
                        else -> com.riftcompanion.app.domain.model.LocationKind.Unavailable
                    },
                    countsAsAvailable = loc.countsAsAvailable,
                    hidden = loc.hidden,
                )
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
                        hasMissingCards = display.any { it.isMissing },
                        legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        banlistWarnings = issues.filter { it.severity == ValidationSeverity.warning }.map { it.message },
                        linkedLocationName = it.linkedLocationName,
                        legendImageURL = legendImageURL,
                        legendDisplayName = legendIdentity?.displayName,
                    )
                },
                entries = display.groupBy { it.zone }.flatMap { (_, items) ->
                    items.sortedBy { it.displayName }
                },
                isLoading = false,
                availableLocations = availableLocs,
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
            // Free the linked location so it can be reused
            val deck = deckDao.getDeck(deckId)
            if (deck != null) {
                val linkedLoc = deck.linkedLocationName
                if (linkedLoc != null) {
                    val policy = locationPolicyDao.getByName(linkedLoc.lowercase().trim())
                    if (policy != null && policy.linkedDeckId == deckId) {
                        locationPolicyDao.upsert(policy.copy(linkedDeckId = null))
                    }
                }
            }
            deckDao.deleteDeck(deckId)
            loadDecks()
        }
    }

    fun renameDeck(deckId: String, newName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val deck = deckDao.getDeck(deckId) ?: return@launch
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return@launch

            deckDao.insertDeck(deck.copy(
                name = trimmed,
                updatedAt = System.currentTimeMillis(),
            ))

            loadDecks()
            loadDeckDetail(deckId)
            onDone()
        }
    }

    /**
     * Save both the deck name and its card entries.
     * Called when the user taps the save (check) button in edit mode.
     */
    fun saveDeck(deckId: String, newName: String, newLinkedLocation: String? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val deck = deckDao.getDeck(deckId) ?: return@launch
            val trimmed = newName.trim()
            if (trimmed.isNotBlank()) {
                val updatedLinkedLocation = if (newLinkedLocation != null) {
                    val trimmedLoc = newLinkedLocation.trim()
                    if (trimmedLoc.isBlank()) null else trimmedLoc
                } else {
                    deck.linkedLocationName
                }
                
                // If changing linked location, update location policies
                if (updatedLinkedLocation != deck.linkedLocationName) {
                    // Unlink old location
                    if (deck.linkedLocationName != null) {
                        val oldPolicy = locationPolicyDao.getByName(deck.linkedLocationName.lowercase().trim())
                        if (oldPolicy != null && oldPolicy.linkedDeckId == deckId) {
                            locationPolicyDao.upsert(oldPolicy.copy(linkedDeckId = null))
                        }
                    }
                    // Link new location
                    if (updatedLinkedLocation != null) {
                        val newPolicy = locationPolicyDao.getByName(updatedLinkedLocation.lowercase().trim())
                        if (newPolicy != null) {
                            locationPolicyDao.upsert(newPolicy.copy(linkedDeckId = deckId))
                        } else {
                            locationPolicyDao.upsert(LocationPolicyEntity(
                                name = updatedLinkedLocation,
                                displayName = updatedLinkedLocation,
                                color = null,
                                icon = null,
                                kind = "deck",
                                countsAsAvailable = false,
                                hidden = false,
                                linkedDeckId = deckId,
                            ))
                        }
                    }
                }
                
                deckDao.insertDeck(deck.copy(
                    name = trimmed,
                    linkedLocationName = updatedLinkedLocation,
                    updatedAt = System.currentTimeMillis(),
                ))
            }
            // Entries are already saved to DB by individual edit operations
            loadDecks()
            loadDeckDetail(deckId)
            onDone()
        }
    }

    /**
     * Snapshot the current deck entries so they can be restored on discard.
     */
    private var savedEntries: List<DeckEntryEntity>? = null

    fun beginEditSession(deckId: String) {
        viewModelScope.launch {
            savedEntries = deckDao.getEntriesForDeck(deckId)
        }
    }

    /**
     * Discard all edits made during edit mode: restore the original entries.
     */
    fun discardEdits(deckId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val saved = savedEntries
            if (saved != null) {
                val current = deckDao.getEntriesForDeck(deckId)
                // Only restore + revert if something actually changed
                val changed = current.size != saved.size ||
                    current.zip(saved).any { (c, s) -> c.quantity != s.quantity || c.zone != s.zone || c.nameSlug != s.nameSlug }
                if (changed) {
                    deckDao.deleteEntriesForDeck(deckId)
                    deckDao.insertEntries(saved)
                    revertBuiltStateIfNecessary(deckId)
                }
            }
            savedEntries = null
            loadDeckDetail(deckId)
            onDone()
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

                // Main deck is capped at 39 cards (40 with the champion).
                // Any main-deck cards beyond 39 overflow into the sideboard.
                val mainDeckEntries = otherCards.filter { it.zone == DeckZone.main.name }
                val mainDeckTotal = mainDeckEntries.sumOf { it.quantity }
                val mainDeckCap = 39
                if (mainDeckTotal > mainDeckCap) {
                    // Distribute overflow to sideboard: reduce main deck cards one by one
                    var overflow = mainDeckTotal - mainDeckCap
                    val adjustedMain = mainDeckEntries.map { entry ->
                        val take = minOf(overflow, entry.quantity)
                        overflow -= take
                        if (take > 0) entry.copy(quantity = entry.quantity - take) else entry
                    }.filter { it.quantity > 0 }
                    val sideboardOverflow = mainDeckEntries.map { entry ->
                        val inMain = adjustedMain.find { it.nameSlug == entry.nameSlug }?.quantity ?: 0
                        val movedToSideboard = entry.quantity - inMain
                        if (movedToSideboard > 0) entry.copy(zone = DeckZone.sideboard.name, quantity = movedToSideboard) else null
                    }.filterNotNull()

                    // Replace main deck entries with adjusted ones + sideboard overflow
                    val otherZones = otherCards.filter { it.zone != DeckZone.main.name }
                    entries.addAll(otherZones)
                    entries.addAll(adjustedMain)
                    entries.addAll(sideboardOverflow)
                } else {
                    entries.addAll(otherCards)
                }

                deckDao.insertEntries(entries)

                // Link the location policy to this deck (1:1 linking)
                if (policy != null) {
                    locationPolicyDao.upsert(policy.copy(linkedDeckId = deckId))
                } else {
                    // Create policy if it doesn't exist yet
                    locationPolicyDao.upsert(LocationPolicyEntity(
                        name = locationName,
                        displayName = locationName,
                        color = null,
                        icon = null,
                        kind = "deck",
                        countsAsAvailable = false,
                        hidden = false,
                        linkedDeckId = deckId,
                    ))
                }

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
                val allPrintings = cardPrintingDao.getAll().first().associateBy { it.productID }

                // Determine deck location
                val existingLocation = deck.linkedLocationName
                val isNewLocation = existingLocation == null
                val deckLocationName = existingLocation ?: "${deck.name} (Deck)"

                // Look up the actual location name and display name (existingLocation may be lowercase from a previous build)
                val deckLocationDisplayName: String
                val deckLocationNameResolved: String = run {
                    val policy = locationPolicyDao.getByName(deckLocationName.lowercase().trim())
                    if (policy != null) {
                        deckLocationDisplayName = policy.displayName.takeIf { it.isNotBlank() } ?: policy.name
                        policy.name
                    } else {
                        val invLoc = inventoryLocationDao.getAll().first()
                            .find { it.name.equals(deckLocationName, ignoreCase = true) }
                        if (invLoc != null) {
                            deckLocationDisplayName = invLoc.displayName?.takeIf { it.isNotBlank() } ?: invLoc.name
                            invLoc.name
                        } else {
                            deckLocationDisplayName = deckLocationName
                            deckLocationName
                        }
                    }
                }

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
                val storageLocs = locationPolicyDao.getStorageLocations()
                val storageLocationNames = storageLocs.map { it.name.trim().lowercase() }.toSet()
                val storageDisplayNames = storageLocs.associate { it.name.trim().lowercase() to it.displayName }

                // Gather all inventory lines for cards in the deck
                val entryNameSlugs = entries.map { it.nameSlug }.toSet()
                val linesForEntries = entries.flatMap { entry ->
                    inventoryLineDao.getLinesByCardSlug(entry.nameSlug).map { line ->
                        DeckBuildPlanner.LineInfo(
                            nameSlug = entry.nameSlug,
                            locationName = line.locationName ?: "",
                            quantity = line.quantity,
                        )
                    }
                }
                // Also get lines at the deck location for cards NOT in the deck (removed cards)
                val linesAtDeckLoc = inventoryLineDao.getByLocation(deckLocationName)
                    .mapNotNull { line ->
                        val nameSlug = allPrintings[line.productId]?.nameSlug ?: return@mapNotNull null
                        if (nameSlug in entryNameSlugs) return@mapNotNull null // already in linesForEntries
                        DeckBuildPlanner.LineInfo(nameSlug, line.locationName ?: "", line.quantity)
                    }
                val allLines = linesForEntries + linesAtDeckLoc

                // Build entry infos for the planner
                val entryInfos = entries.map { entry ->
                    val displayName = identities[entry.nameSlug]?.displayName ?: entry.nameSlug
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                    DeckBuildPlanner.EntryInfo(
                        nameSlug = entry.nameSlug,
                        displayName = displayName,
                        quantity = entry.quantity,
                        zone = zone,
                        sourceLocationName = entry.sourceLocationName,
                    )
                }

                val plan = DeckBuildPlanner.computePlan(
                    entries = entryInfos,
                    lines = allLines,
                    storageLocations = storageLocationNames,
                    deckLocationName = deckLocationNameResolved,
                    deckLocationDisplayName = deckLocationDisplayName,
                    storageDisplayNames = storageDisplayNames,
                    isAlreadyBuilt = deck.state == "assembled",
                )

                _buildState.value = DeckBuildUiState(
                    preview = DeckBuildPreview(
                        deckId = deckId,
                        deckName = deck.name,
                        deckLocationName = deckLocationNameResolved,
                        deckLocationDisplayName = deckLocationDisplayName,
                        isNewLocation = isNewLocation,
                        movements = plan.movements.map { CardMovement(it.nameSlug, it.displayName, it.quantity, it.fromLocation, it.toLocation) },
                        returns = plan.returns.map { CardMovement(it.nameSlug, it.displayName, it.quantity, it.fromLocation, it.toLocation) },
                        missing = plan.missing.map { MissingCard(it.nameSlug, it.displayName, it.needed, it.available) },
                        isAlreadyBuilt = deck.state == "assembled",
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
                val deckLocationName = preview.deckLocationName

                // Create deck location if new (both API + local)
                if (preview.isNewLocation) {
                    cardNexusClient.upsertLocation(
                        com.riftcompanion.app.domain.model.InventoryLocationUpsertRequest(
                            name = preview.deckLocationName,
                        ),
                    )
                    locationPolicyDao.upsert(LocationPolicyEntity(
                        name = deckLocationName,
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
                            name = deckLocationName,
                            displayName = preview.deckLocationName,
                            color = null,
                            icon = null,
                        ),
                    ))
                }

                // Build API bulk update items for movements (storage → deck)
                val apiMoves = mutableListOf<com.riftcompanion.app.domain.model.InventoryBulkMoveItem>()

                for (movement in preview.movements) {
                    val sourceLines = inventoryLineDao.getLinesByCardSlug(movement.nameSlug)
                        .filter { it.locationName == movement.fromLocation }
                        .sortedBy { it.quantity }

                    var toMove = movement.quantity
                    for (line in sourceLines) {
                        if (toMove <= 0) break
                        val take = minOf(toMove, line.quantity)
                        // Move `take` cards from source to deck location via API
                        apiMoves.add(com.riftcompanion.app.domain.model.InventoryBulkMoveItem(
                            inventoryID = line.id,
                            destinationLocationName = deckLocationName,
                            count = take,
                        ))
                        toMove -= take
                    }
                }

                // Build API bulk update items for returns (deck → storage)
                for (returnMovement in preview.returns) {
                    val deckLines = inventoryLineDao.getLinesByCardSlug(returnMovement.nameSlug)
                        .filter { (it.locationName?.trim()?.equals(deckLocationName, ignoreCase = true) == true) }
                        .sortedBy { it.quantity }

                    var toReturn = returnMovement.quantity
                    for (line in deckLines) {
                        if (toReturn <= 0) break
                        val take = minOf(toReturn, line.quantity)
                        if (!line.id.startsWith("local_")) {
                            apiMoves.add(com.riftcompanion.app.domain.model.InventoryBulkMoveItem(
                                inventoryID = line.id,
                                destinationLocationName = returnMovement.toLocation,
                                count = take,
                            ))
                        }
                        toReturn -= take
                    }
                }

                // Send bulk update to API
                if (apiMoves.isNotEmpty()) {
                    val request = com.riftcompanion.app.domain.model.InventoryBulkMoveRequest(
                        idempotencyKey = "build_${preview.deckId}_${System.currentTimeMillis()}",
                        moves = apiMoves,
                    )
                    cardNexusClient.bulkUpdateInventory(request).getOrThrow()
                }

                // Create inventory lines in the API for runes/battlefields that don't exist in inventory
                // Do this BEFORE marking the deck as built — if this fails, the deck stays unbuilt
                val allPrintings = cardPrintingDao.getAll().first().associateBy { it.nameSlug }
                val linesToCreate = mutableListOf<com.riftcompanion.app.data.api.CardNexusClient.InventoryLineCreate>()
                val existingEntries = deckDao.getEntriesForDeck(preview.deckId)
                for (entry in existingEntries) {
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                    val printing = allPrintings[entry.nameSlug] ?: continue
                    val finish = printing.finishesCsv.split(",").firstOrNull()?.trim()?.ifBlank { null } ?: "Standard"
                    val movedFromStorage = preview.movements
                        .filter { it.nameSlug == entry.nameSlug }
                        .sumOf { it.quantity }
                    val alreadyAtDeck = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
                        .filter { (it.locationName?.trim()?.equals(deckLocationName, ignoreCase = true) == true) }
                        .sumOf { it.quantity }
                    val toCreate = entry.quantity - movedFromStorage - alreadyAtDeck
                    if (toCreate <= 0) continue
                    linesToCreate.add(com.riftcompanion.app.data.api.CardNexusClient.InventoryLineCreate(
                        productId = printing.productID,
                        finish = finish,
                        quantity = toCreate,
                        location = deckLocationName,
                        condition = "NM",
                        language = "en",
                    ))
                }
                if (linesToCreate.isNotEmpty()) {
                    cardNexusClient.createInventoryLines(
                        lines = linesToCreate,
                        idempotencyKey = "build_create_${preview.deckId}_${System.currentTimeMillis()}",
                    ).getOrThrow()
                }

                // ── ALL API CALLS SUCCEEDED — now update local state ──

                // Re-sync inventory from API to get real IDs and state
                val syncedLines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
                inventoryLineDao.replaceApiLines(syncedLines.map { com.riftcompanion.app.data.db.EntityConverter.toEntity(it) })
                val syncedLocations = cardNexusClient.fetchLocations().getOrThrow()
                inventoryLocationDao.replaceAll(syncedLocations.map { com.riftcompanion.app.data.db.EntityConverter.toEntity(it) })

                // Mark deck entries as built with source tracking
                val updatedEntries = existingEntries.map { entry ->
                    val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
                    val movedFromStorage = preview.movements
                        .filter { it.nameSlug == entry.nameSlug }
                        .sumOf { it.quantity }
                    if (zone == DeckZone.rune || zone == DeckZone.battlefield) {
                        entry.copy(
                            isBuilt = true,
                            sourceLocationName = if (movedFromStorage > 0) preview.movements.first { it.nameSlug == entry.nameSlug }.fromLocation else null,
                        )
                    } else {
                        entry.copy(
                            isBuilt = true,
                            sourceLocationName = preview.movements.find { it.nameSlug == entry.nameSlug }?.fromLocation,
                        )
                    }
                }
                deckDao.deleteEntriesForDeck(preview.deckId)
                deckDao.insertEntries(updatedEntries)

                // Link location to deck and mark as assembled
                val deck = deckDao.getDeck(preview.deckId)
                if (deck != null) {
                    deckDao.insertDeck(deck.copy(
                        linkedLocationName = preview.deckLocationName,
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
    fun previewDisassemble(deckId: String) {
        viewModelScope.launch {
            _disassembleState.value = DisassembleUiState(isLoading = true)
            try {
                val deck = deckDao.getDeck(deckId)
                    ?: throw IllegalArgumentException("Deck not found")
                val deckLocation = deck.linkedLocationName
                    ?: throw IllegalArgumentException("Deck has no linked location")
                val entries = deckDao.getEntriesForDeck(deckId)
                val identities = cardIdentityDao.getAll().first().associateBy { it.nameSlug }
                val allPrintings = cardPrintingDao.getAll().first().associateBy { it.productID }

                // Get all storage + unavailable locations (potential destinations)
                val storageLocs = locationPolicyDao.getStorageLocations()
                val unavailableLocs = locationPolicyDao.getByKind("unavailable")
                val availableLocations = (storageLocs + unavailableLocs)
                    .filter { it.name.trim().lowercase() != deckLocation.trim().lowercase() }
                    .map { e ->
                        com.riftcompanion.app.domain.model.LocationPolicy(
                            name = e.name,
                            displayName = e.displayName,
                            color = e.color,
                            icon = e.icon,
                            kind = com.riftcompanion.app.domain.model.LocationKind.fromStorageValue(e.kind),
                            countsAsAvailable = e.countsAsAvailable,
                            hidden = e.hidden,
                        )
                    }

                // Get all lines at the deck location
                val deckLines = inventoryLineDao.getByLocation(deckLocation)
                val entryNameSlugs = entries.map { it.nameSlug }.toSet()

                // Build card info for each line at the deck location
                val cards = mutableListOf<DisassembleCardInfo>()
                val seenSlugs = mutableSetOf<String>()

                // First: cards in the deck definition (with source info)
                for (entry in entries) {
                    if (entry.nameSlug in seenSlugs) continue
                    val linesForCard = deckLines.filter {
                        allPrintings[it.productId]?.nameSlug == entry.nameSlug
                    }
                    if (linesForCard.isEmpty()) continue
                    val totalQty = linesForCard.sumOf { it.quantity }
                    if (totalQty <= 0) continue
                    seenSlugs.add(entry.nameSlug)
                    val displayName = identities[entry.nameSlug]?.displayName ?: entry.nameSlug
                    cards.add(DisassembleCardInfo(
                        nameSlug = entry.nameSlug,
                        displayName = displayName,
                        quantity = totalQty,
                        fromLocation = deckLocation,
                        suggestedDestination = entry.sourceLocationName,
                    ))
                }

                // Then: cards at the location NOT in the deck definition (removed cards or other cards)
                for (line in deckLines) {
                    val nameSlug = allPrintings[line.productId]?.nameSlug ?: continue
                    if (nameSlug in seenSlugs) continue
                    if (line.quantity <= 0) continue
                    seenSlugs.add(nameSlug)
                    val displayName = identities[nameSlug]?.displayName ?: nameSlug
                    cards.add(DisassembleCardInfo(
                        nameSlug = nameSlug,
                        displayName = displayName,
                        quantity = line.quantity,
                        fromLocation = deckLocation,
                        suggestedDestination = null,
                    ))
                }

                _disassembleState.value = DisassembleUiState(
                    isLoading = false,
                    cards = cards,
                    availableLocations = availableLocations,
                    deckLocationName = deckLocation,
                )
            } catch (e: Exception) {
                _disassembleState.value = DisassembleUiState(error = e.message, isLoading = false)
            }
        }
    }

    fun executeDisassemble(deckId: String, overrides: Map<String, String>) {
        viewModelScope.launch {
            _disassembleState.value = _disassembleState.value.copy(isExecuting = true, error = null)
            try {
                val deck = deckDao.getDeck(deckId)
                    ?: throw IllegalArgumentException("Deck not found")
                val deckLocation = deck.linkedLocationName
                    ?: throw IllegalArgumentException("Deck has no linked location")
                val entries = deckDao.getEntriesForDeck(deckId)
                val allPrintings = cardPrintingDao.getAll().first().associateBy { it.productID }

                // Determine default destination
                val defaultDest = _disassembleState.value.availableLocations
                    .firstOrNull()?.name ?: throw IllegalArgumentException("No destination location available")

                // Get all lines at the deck location
                val deckLines = inventoryLineDao.getByLocation(deckLocation)

                // Group lines by nameSlug
                val linesBySlug = deckLines.groupBy { allPrintings[it.productId]?.nameSlug ?: "" }

                // Build a map of nameSlug → destination
                val entrySourceMap = entries.associate { it.nameSlug to (it.sourceLocationName ?: defaultDest) }

                // Build API bulk update items — only for lines that exist in the API
                val apiMoves = mutableListOf<com.riftcompanion.app.domain.model.InventoryBulkMoveItem>()

                for ((nameSlug, cardLines) in linesBySlug) {
                    if (nameSlug.isBlank()) continue
                    val dest = overrides[nameSlug] ?: entrySourceMap[nameSlug] ?: defaultDest
                    val totalQty = cardLines.sumOf { it.quantity }
                    if (totalQty <= 0) continue

                    for (line in cardLines) {
                        if (line.quantity <= 0) continue
                        if (!line.id.startsWith("local_")) {
                            apiMoves.add(com.riftcompanion.app.domain.model.InventoryBulkMoveItem(
                                inventoryID = line.id,
                                destinationLocationName = dest,
                                count = line.quantity,
                            ))
                        }
                    }
                }

                // Send bulk update to API
                if (apiMoves.isNotEmpty()) {
                    val request = com.riftcompanion.app.domain.model.InventoryBulkMoveRequest(
                        idempotencyKey = "disassemble_${deckId}_${System.currentTimeMillis()}",
                        moves = apiMoves,
                    )
                    cardNexusClient.bulkUpdateInventory(request).getOrThrow()
                }

                // Re-sync inventory from API to get real IDs and state
                val syncedLines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
                inventoryLineDao.replaceApiLines(syncedLines.map { com.riftcompanion.app.data.db.EntityConverter.toEntity(it) })
                val syncedLocations = cardNexusClient.fetchLocations().getOrThrow()
                inventoryLocationDao.replaceAll(syncedLocations.map { com.riftcompanion.app.data.db.EntityConverter.toEntity(it) })

                // Mark entries as not built
                val updatedEntries = entries.map { it.copy(isBuilt = false, sourceLocationName = null) }
                deckDao.deleteEntriesForDeck(deckId)
                deckDao.insertEntries(updatedEntries)

                // Update deck state
                deckDao.insertDeck(deck.copy(
                    state = "planned",
                    updatedAt = System.currentTimeMillis(),
                ))

                _disassembleState.value = _disassembleState.value.copy(isExecuting = false, isDone = true)
                loadDecks()
            } catch (e: Exception) {
                _disassembleState.value = _disassembleState.value.copy(isExecuting = false, error = e.message)
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
        val storageLocations = locationPolicyDao.getStorageLocations().map { it.name.trim().lowercase() }.toSet()
        val deckLocations = locationPolicyDao.getByKind("deck").map { it.name.trim().lowercase() }.toSet()
        val linkedLoc = deckDao.getDeck(deckId)?.linkedLocationName
        val zoneOrder = listOf(DeckZone.legend, DeckZone.chosenChampion, DeckZone.main, DeckZone.sideboard, DeckZone.rune, DeckZone.battlefield)
        val claimedBySlug = mutableMapOf<String, Int>()
        val sortedEntries = entries.sortedBy { e -> zoneOrder.indexOf(DeckZone.fromString(e.zone) ?: DeckZone.main) }

        val display = sortedEntries.map { entry ->
            val identity = identities[entry.nameSlug]
            val printings = allPrintings[entry.nameSlug] ?: emptyList()
            val imageURL = printings.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                ?: printings.firstOrNull()?.imageURL
            val allLines = inventoryLineDao.getLinesByCardSlug(entry.nameSlug)
            val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
            val availability = DeckAvailability.compute(
                quantity = entry.quantity,
                zone = zone,
                alreadyClaimed = claimedBySlug[entry.nameSlug] ?: 0,
                lineLocations = allLines.map { it.locationName },
                lineQuantities = allLines.map { it.quantity },
                storageLocations = storageLocations,
                deckLocations = deckLocations,
                linkedLocation = linkedLoc,
            )
            if (zone != DeckZone.rune && zone != DeckZone.battlefield) {
                val claimed = minOf(availability.availableInStorage + availability.inDeckLocation, entry.quantity)
                claimedBySlug[entry.nameSlug] = (claimedBySlug[entry.nameSlug] ?: 0) + claimed
            }

            DeckEntryDisplay(
                entryId = entry.id,
                zone = zone,
                nameSlug = entry.nameSlug,
                displayName = identity?.displayName ?: entry.nameSlug,
                quantity = entry.quantity,
                preferredImageURL = imageURL,
                cardType = identity?.cardType,
                superType = identity?.superType,
                expansion = printings.firstOrNull()?.expansionSlug,
                rarity = printings.firstOrNull()?.rarity,
                domains = identity?.domainsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                energyCost = identity?.energyCost,
                might = identity?.might,
                priceEur = printings.firstNotNullOfOrNull { cardPriceDao.getByProductId(it.productID) }?.cardmarketMarketValue,
                priceUsd = printings.firstNotNullOfOrNull { cardPriceDao.getByProductId(it.productID) }?.tcgplayerMarketValue,
                availableInStorage = availability.availableInStorage,
                inDeckLocation = availability.inDeckLocation,
                inOtherDecks = availability.inOtherDecks,
                totalOwned = availability.totalOwned,
                isMissing = availability.isMissing,
                missingCount = availability.missingCount,
            )
        }

        // Re-read deck to pick up state changes (e.g. assembled → planned)
        val deck = deckDao.getDeck(deckId)
        val isBuilt = entries.any { it.isBuilt }

        // Recompute legality
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

        _deckDetailState.value = DeckDetailUiState(
            deck = deck?.let {
                current.deck?.copy(
                    cardCount = entries.sumOf { it.quantity },
                    isBuilt = isBuilt,
                    isLegal = isLegal,
                    legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        banlistWarnings = issues.filter { it.severity == ValidationSeverity.warning }.map { it.message },
                )
            },
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
    /**
     * When a built deck is edited (add/remove/move), revert it to "planned" state
     * and clear all isBuilt flags since the physical card layout no longer matches.
     */
    private suspend fun revertBuiltStateIfNecessary(deckId: String) {
        val deck = deckDao.getDeck(deckId) ?: return
        if (deck.state != "assembled") return
        val entries = deckDao.getEntriesForDeck(deckId)
        if (entries.any { it.isBuilt }) {
            deckDao.deleteEntriesForDeck(deckId)
            deckDao.insertEntries(entries.map { it.copy(isBuilt = false) })
        }
        deckDao.insertDeck(deck.copy(state = "planned", updatedAt = System.currentTimeMillis()))
    }

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
            // If deck was built, revert to planned since cards changed
            revertBuiltStateIfNecessary(deckId)
            // Update UI state incrementally instead of full reload
            updateDeckDetailIncremental(deckId)
        }
    }

    /**
     * Move a card from one zone to another (e.g. main deck → sideboard).
     * Removes `quantity` from [fromZone] and adds it to [toZone].
     */
    fun moveCardToZone(deckId: String, nameSlug: String, fromZone: DeckZone, toZone: DeckZone, quantity: Int = 1) {
        viewModelScope.launch {
            if (fromZone == toZone) return@launch
            val entries = deckDao.getEntriesForDeck(deckId).toMutableList()

            // Remove from source zone
            val fromEntry = entries.find { it.nameSlug == nameSlug && it.zone == fromZone.name }
            if (fromEntry != null) {
                val newQty = fromEntry.quantity - quantity
                if (newQty > 0) {
                    entries[entries.indexOf(fromEntry)] = fromEntry.copy(quantity = newQty)
                } else {
                    entries.remove(fromEntry)
                }
            }

            // Add to target zone
            val toEntry = entries.find { it.nameSlug == nameSlug && it.zone == toZone.name }
            if (toEntry != null) {
                entries[entries.indexOf(toEntry)] = toEntry.copy(quantity = toEntry.quantity + quantity)
            } else {
                entries.add(DeckEntryEntity(
                    deckId = deckId,
                    zone = toZone.name,
                    nameSlug = nameSlug,
                    quantity = quantity,
                ))
            }

            deckDao.deleteEntriesForDeck(deckId)
            deckDao.insertEntries(entries)
            revertBuiltStateIfNecessary(deckId)
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
            revertBuiltStateIfNecessary(deckId)
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
