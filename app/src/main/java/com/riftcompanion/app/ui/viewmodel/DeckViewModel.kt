package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.db.CardIdentityDao
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
    val zone: DeckZone,
    val nameSlug: String,
    val displayName: String,
    val quantity: Int,
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

    fun loadDecks() {
        viewModelScope.launch {
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
            val display = entries.map { entry ->
                DeckEntryDisplay(
                    zone = DeckZone.fromString(entry.zone) ?: DeckZone.main,
                    nameSlug = entry.nameSlug,
                    displayName = identities[entry.nameSlug]?.displayName ?: entry.nameSlug,
                    quantity = entry.quantity,
                )
            }
            _deckDetailState.value = DeckDetailUiState(
                deck = deck?.let {
                    DeckSummary(it.id, it.name, entries.sumOf { e -> e.quantity }, it.updatedAt)
                },
                entries = display.groupBy { it.zone }.flatMap { (zone, items) ->
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
                    val slugByName = identities.associateBy { it.displayName.lowercase() }
                    val entries = doc.entries.map { entry ->
                        val slug = slugByName[entry.displayName.lowercase()]?.nameSlug
                            ?: entry.displayName.lowercase()
                                .replace(" ", "-")
                                .replace(",", "")
                                .replace("'", "")
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

                // Group by nameSlug and sum quantities
                val entries = lines.groupBy { it.id }
                    .map { (id, group) ->
                        val line = group.first()
                        val totalQty = group.sumOf { it.quantity }
                        DeckEntryEntity(
                            deckId = deckId,
                            zone = DeckZone.main.name,
                            nameSlug = line.id,
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
     */
    fun executeBuild() {
        viewModelScope.launch {
            val preview = _buildState.value.preview ?: return@launch
            _buildState.value = _buildState.value.copy(isLoading = true)
            try {
                // Create deck location if new
                if (preview.isNewLocation) {
                    val normalized = preview.deckLocationName.lowercase().trim()
                    locationPolicyDao.upsert(LocationPolicyEntity(
                        normalizedName = normalized,
                        displayName = preview.deckLocationName,
                        color = null,
                        icon = null,
                        kind = "deck",
                        countsAsAvailable = false,
                        hidden = false,
                    ))
                    // Also insert into inventory_locations
                    inventoryLocationDao.insertAll(listOf(
                        InventoryLocationEntity(
                            normalizedName = normalized,
                            displayName = preview.deckLocationName,
                            color = null,
                            icon = null,
                        ),
                    ))
                }

                // Move cards: for each movement, reduce source line quantity
                // and create/update line in deck location
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
                        val newLineId = "${movement.nameSlug}_${preview.deckLocationName}_${System.currentTimeMillis()}"
                        inventoryLineDao.insertAll(listOf(
                            InventoryLineEntity(
                                id = newLineId,
                                customId = line.customId,
                                productId = line.productId,
                                finish = line.finish,
                                condition = line.condition,
                                language = line.language,
                                quantity = take,
                                locationName = preview.deckLocationName.lowercase().trim(),
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

                // Link location to deck
                val deck = deckDao.getDeck(preview.deckId)
                if (deck != null) {
                    deckDao.insertDeck(deck.copy(
                        linkedLocationName = preview.deckLocationName.lowercase().trim(),
                        updatedAt = System.currentTimeMillis(),
                    ))
                }

                _buildState.value = DeckBuildUiState(isBuilt = true, isLoading = false)
            } catch (e: Exception) {
                _buildState.value = DeckBuildUiState(error = e.message, isLoading = false)
            }
        }
    }

    fun clearBuildState() {
        _buildState.value = DeckBuildUiState()
    }
}
