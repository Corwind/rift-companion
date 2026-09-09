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
                            tags = entity.tagsCsv.split(",").filter { it.isNotBlank() },
                        )
                    }
                    val issues = DeckRulesEngine.validate(entryData, identityInfos)
                    val isLegal = issues.none { it.severity == ValidationSeverity.error }

                    DeckSummary(
                        id = deck.id,
                        name = deck.name,
                        cardCount = cardCount,
                        updatedAt = deck.updatedAt,
                        isBuilt = isBuilt,
                        isLegal = isLegal,
                        legalityIssues = issues.filter { it.severity == ValidationSeverity.error }.map { it.message },
                        linkedLocationName = deck.linkedLocationName,
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
            val storageLocations = locationPolicyDao.getStorageLocations().map { it.normalizedName }.toSet()
            val deckLocations = locationPolicyDao.getByKind("deck").map { it.normalizedName }.toSet()

            // For each entry, compute availability
            val display = entries.map { entry ->
                val identity = identities[entry.nameSlug]
                val printings = allPrintings[entry.nameSlug] ?: emptyList()
                val imageURL = printings.firstOrNull { !it.imageURL.isNullOrEmpty() }?.imageURL
                    ?: printings.firstOrNull()?.imageURL
                // Find all inventory lines for this card
                val allLines = inventoryLineDao.getBySlug(entry.nameSlug)
                val inStorage = allLines.filter { it.locationName in storageLocations }.sumOf { it.quantity }
                val inDecks = allLines.filter { it.locationName in deckLocations && it.locationName != deck?.linkedLocationName }.sumOf { it.quantity }
                val total = allLines.sumOf { it.quantity }
                val missing = maxOf(0, entry.quantity - inStorage)

                DeckEntryDisplay(
                    entryId = entry.id,
                    zone = DeckZone.fromString(entry.zone) ?: DeckZone.main,
                    nameSlug = entry.nameSlug,
                    displayName = identity?.displayName ?: entry.nameSlug,
                    quantity = entry.quantity,
                    preferredImageURL = imageURL,
                    cardType = identity?.cardType,
                    expansion = printings.firstOrNull()?.expansionSlug,
                    rarity = printings.firstOrNull()?.rarity,
                    domains = identity?.tagsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    availableInStorage = inStorage,
                    inOtherDecks = inDecks,
                    totalOwned = total,
                    isMissing = missing > 0,
                    missingCount = missing,
                )
            }
            _deckDetailState.value = DeckDetailUiState(
                deck = deck?.let {
                    DeckSummary(it.id, it.name, entries.sumOf { e -> e.quantity }, it.updatedAt)
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
     * Create a new empty deck definition.
     */
    fun createEmptyDeck(deckName: String) {
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
                _importState.value = ImportUiState(
                    success = DeckSummary(deckId, deckName.ifBlank { "New Deck" }, 0, now),
                )
                loadDecks()
            } catch (e: Exception) {
                _importState.value = ImportUiState(error = e.message)
            }
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
                val storageLocationNames = storageLocations.map { it.normalizedName }.toSet()

                // For each deck entry, find available cards in storage
                val movements = mutableListOf<CardMovement>()
                val missing = mutableListOf<MissingCard>()

                for (entry in entries) {
                    val displayName = identities[entry.nameSlug]?.displayName ?: entry.nameSlug
                    val needed = entry.quantity

                    // Find all inventory lines for this card in storage locations
                    val lines = inventoryLineDao.getBySlug(entry.nameSlug)
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

                    if (remaining > 0) {
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
                        normalizedName = deckLocationNormalized,
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
                            normalizedName = deckLocationNormalized,
                            displayName = preview.deckLocationName,
                            color = null,
                            icon = null,
                        ),
                    ))
                }

                // Move cards and record source tracking
                val updatedEntries = mutableListOf<DeckEntryEntity>()
                for (movement in preview.movements) {
                    val sourceLines = inventoryLineDao.getBySlug(movement.nameSlug)
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
                    val movement = preview.movements.find { it.nameSlug == entry.nameSlug }
                    updatedEntries.add(entry.copy(
                        isBuilt = true,
                        sourceLocationName = movement?.fromLocation,
                    ))
                }
                deckDao.deleteEntriesForDeck(preview.deckId)
                deckDao.insertEntries(updatedEntries)

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

    fun clearBuildState() {
        _buildState.value = DeckBuildUiState()
    }

    // ── Deck editing ───────────────────────────────────────────────────

    /**
     * Add a card to a deck in the specified zone. If the card already exists
     * in that zone, increases the quantity.
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
            loadDeckDetail(deckId)
            loadDecks()
        }
    }

    /**
     * Remove a card from a deck (reduce quantity or remove entirely).
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
            loadDeckDetail(deckId)
            loadDecks()
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
