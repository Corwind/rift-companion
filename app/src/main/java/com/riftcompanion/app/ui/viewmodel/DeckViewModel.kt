package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.DeckDao
import com.riftcompanion.app.data.db.DeckEntity
import com.riftcompanion.app.data.db.DeckEntryEntity
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.deck.RiftDeckParser
import com.riftcompanion.app.data.deck.TextDeckParser
import com.riftcompanion.app.domain.model.DeckZone
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

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckDao: DeckDao,
    private val cardIdentityDao: CardIdentityDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
) : ViewModel() {

    private val _deckListState = MutableStateFlow(DeckListUiState(isLoading = true))
    val deckListState: StateFlow<DeckListUiState> = _deckListState.asStateFlow()

    private val _deckDetailState = MutableStateFlow(DeckDetailUiState(isLoading = true))
    val deckDetailState: StateFlow<DeckDetailUiState> = _deckDetailState.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun loadDecks() {
        viewModelScope.launch {
            deckDao.getAllDecks().collect { decks ->
                val summaries = decks.map { deck ->
                    DeckSummary(
                        id = deck.id,
                        name = deck.name,
                        cardCount = deckDao.getEntryCount(deck.id),
                        updatedAt = deck.updatedAt,
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
}
