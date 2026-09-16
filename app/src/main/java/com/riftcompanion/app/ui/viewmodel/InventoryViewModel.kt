package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.InventoryLocationQuantityEdit
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardViewMode { LIST, GRID }

data class InventoryUiState(
    val cards: List<InventoryCardSummary> = emptyList(),
    val locations: List<LocationPolicy> = emptyList(),
    val searchQuery: String = "",
    val selectedLocation: String? = null,
    val domainFilters: Set<String> = emptySet(),
    val viewMode: CardViewMode = CardViewMode.GRID,
    val isLoading: Boolean = false,
    val error: String? = null,
    val totalCards: Int = 0,
    val availableCards: Int = 0,
    val filteredCount: Int = 0,
    val cardCountsByLocation: Map<String, Int> = emptyMap(),
    val allLocationsCount: Int = 0,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: String? = null,
)

data class InventoryQuantityDraftKey(
    val cardID: String,
    val locationKey: String,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: RiftRepository,
) : ViewModel() {

    private var cachedBannedCards: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            repository.banlistFlow().collect { entries ->
                cachedBannedCards = entries
                    .filter { it.cardType == com.riftcompanion.app.domain.model.BanlistEntryType.CARD }
                    .map { it.cardName.lowercase() }
                    .toSet()
                android.util.Log.d("InventoryVM", "Banned cards loaded: $cachedBannedCards")
            }
        }
    }

    fun isCardBanned(cardName: String): Boolean {
        val banned = cardName.lowercase() in cachedBannedCards
        if (banned) android.util.Log.d("InventoryVM", "Card '$cardName' IS banned")
        return banned
    }

    private val _searchQuery = MutableStateFlow("")
    private val _selectedLocation = MutableStateFlow<String?>(null)
    private val _domainFilters = MutableStateFlow<Set<String>>(emptySet())
    private val _viewMode = MutableStateFlow(CardViewMode.GRID)
    private val _isEditing = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _saveError = MutableStateFlow<String?>(null)
    private val _saveSuccess = MutableStateFlow<String?>(null)

    val drafts = MutableStateFlow<Map<InventoryQuantityDraftKey, Int>>(emptyMap())

    // Combine inventory + locations into a pair first, then combine with UI state flows
    private val cardsAndLocations = combine(
        repository.inventoryCardsFlow(),
        repository.locationPoliciesFlow(),
    ) { cards, locations -> cards to locations }

    val uiState: StateFlow<InventoryUiState> = combine(
        cardsAndLocations,
        combine(_searchQuery, _selectedLocation, _domainFilters, _viewMode) { sq, sl, df, vm ->
            Quad(sq, sl, df, vm)
        },
        combine(_isEditing, _isSaving, _saveError, _saveSuccess) { editing, saving, err, ok ->
            EditState(editing, saving, err, ok)
        },
    ) { (cards, locations), (search, location, domains, viewMode), editState ->
        val visibleLocations = locations.filter {
            !it.hidden && it.kind != LocationKind.Unavailable
        }

        val filtered = cards.filter { card ->
            val matchesLocation = location?.let { loc ->
                card.locations.any { it.locationName == loc && it.quantity > 0 }
            } ?: true

            val matchesDomains = domains.isEmpty() ||
                card.identity.appVisibleDomains.any { domains.contains(it) }

            val matchesSearch = search.isBlank() ||
                card.identity.appSearchText.contains(search, ignoreCase = true) ||
                (card.expansion?.contains(search, ignoreCase = true) == true) ||
                (card.rarity?.contains(search, ignoreCase = true) == true)

            matchesLocation && matchesDomains && matchesSearch
        }.sortedBy { it.identity.displayName.lowercase() }

        val totalCards = cards.sumOf { it.availability.totalOwned }
        val availableCards = cards.sumOf { it.availability.availableInStorage }

        // Card count per visible location, respecting current search + domain filters
        // (so the filter sheet shows how many cards each location would return)
        val preFilterForCounts = cards.filter { card ->
            val matchesDomains = domains.isEmpty() ||
                card.identity.appVisibleDomains.any { domains.contains(it) }
            val matchesSearch = search.isBlank() ||
                card.identity.appSearchText.contains(search, ignoreCase = true) ||
                (card.expansion?.contains(search, ignoreCase = true) == true) ||
                (card.rarity?.contains(search, ignoreCase = true) == true)
            matchesDomains && matchesSearch
        }
        val countsByLocation = visibleLocations.associate { loc ->
            loc.name to preFilterForCounts.filter { card ->
                card.locations.any { it.locationName == loc.name && it.quantity > 0 }
            }.sumOf { card -> card.locations.filter { it.locationName == loc.name }.sumOf { it.quantity } }
        }
        val allLocationsCount = preFilterForCounts.sumOf { it.availability.totalOwned }

        // The count shown in the title: total quantity at the selected location,
        // or total across all locations if none selected
        val displayedCount = if (location != null) {
            filtered.sumOf { card -> card.locations.filter { it.locationName == location }.sumOf { it.quantity } }
        } else {
            filtered.sumOf { it.availability.totalOwned }
        }

        InventoryUiState(
            cards = filtered,
            locations = visibleLocations,
            searchQuery = search,
            selectedLocation = location,
            domainFilters = domains,
            viewMode = viewMode,
            isLoading = false,
            error = null,
            totalCards = totalCards,
            availableCards = availableCards,
            filteredCount = displayedCount,
            cardCountsByLocation = countsByLocation,
            allLocationsCount = allLocationsCount,
            isEditing = editState.editing,
            isSaving = editState.saving,
            saveError = editState.error,
            saveSuccess = editState.success,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryUiState(isLoading = true))

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSelectedLocation(location: String?) { _selectedLocation.value = location }
    fun toggleDomainFilter(domain: String) {
        _domainFilters.value = _domainFilters.value.toMutableSet().apply {
            if (contains(domain)) remove(domain) else add(domain)
        }
    }
    fun clearDomainFilters() { _domainFilters.value = emptySet() }
    fun setViewMode(mode: CardViewMode) { _viewMode.value = mode }

    fun startEditing() {
        _isEditing.value = true
        _saveError.value = null
        _saveSuccess.value = null
    }

    fun cancelEditing() {
        _isEditing.value = false
        drafts.value = emptyMap()
        _saveError.value = null
    }

    fun setDraftQuantity(cardID: String, locationKey: String, quantity: Int) {
        val key = InventoryQuantityDraftKey(cardID, locationKey)
        val original = originalQuantity(cardID, locationKey)
        val clamped = maxOf(0, quantity)
        val newDrafts = drafts.value.toMutableMap()
        if (clamped == original) {
            newDrafts.remove(key)
        } else {
            newDrafts[key] = clamped
        }
        drafts.value = newDrafts
    }

    fun getDraftQuantity(cardID: String, locationKey: String): Int {
        val key = InventoryQuantityDraftKey(cardID, locationKey)
        return drafts.value[key] ?: originalQuantity(cardID, locationKey)
    }

    fun revertDrafts() {
        drafts.value = emptyMap()
    }

    val hasChanges: Boolean
        get() = drafts.value.isNotEmpty()

    fun changedCardCount(): Int {
        return drafts.value.keys.map { it.cardID }.toSet().size
    }

    fun saveChanges() {
        if (!hasChanges) return
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            _saveSuccess.value = null

            val allCards = repository.inventoryCardsFlow().first()
            val edits = allCards.mapNotNull { card ->
                val cardDrafts = drafts.value.filter { it.key.cardID == card.id }
                if (cardDrafts.isEmpty()) return@mapNotNull null

                val quantitiesByLocation = mutableMapOf<String, Int>()
                // Start with original quantities for all locations
                for (loc in card.locations) {
                    quantitiesByLocation[loc.locationName] = loc.quantity
                }
                // Apply drafts
                for ((key, qty) in cardDrafts) {
                    quantitiesByLocation[key.locationKey] = qty
                }
                InventoryLocationQuantityEdit(
                    nameSlug = card.id,
                    quantitiesByLocation = quantitiesByLocation,
                )
            }

            val result = repository.saveInventoryLocationQuantities(edits)
            _isSaving.value = false

            if (result.isSuccess) {
                val res = result.getOrThrow()
                val parts = mutableListOf<String>()
                if (res.addedQuantity > 0) parts.add("added ${res.addedQuantity}")
                if (res.removedQuantity > 0) parts.add("removed ${res.removedQuantity}")
                if (res.movedQuantity > 0) parts.add("moved ${res.movedQuantity}")
                val summary = if (parts.isEmpty()) "updated quantities" else parts.joinToString(", ")
                var message = "Saved ${res.editedCardCount} card edit${if (res.editedCardCount == 1) "" else "s"}: $summary."
                if (res.deletedLineCount > 0) {
                    message += " Removed ${res.deletedLineCount} empty inventory line${if (res.deletedLineCount == 1) "" else "s"}."
                }
                if (res.synchronizationWarning != null) {
                    message += " ${res.synchronizationWarning}"
                }
                _saveSuccess.value = message
                drafts.value = emptyMap()
                _isEditing.value = false
            } else {
                _saveError.value = "Inventory update failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
        }
    }

    private fun originalQuantity(cardID: String, locationKey: String): Int {
        // This is read from the current uiState cards, which are reactive
        // We need to access the cards from the repository flow
        // Since drafts are compared against the current state, we use a snapshot
        // The uiState already has the filtered cards, but we need ALL cards for original quantities
        // We'll use the cards from the repository flow via a cached snapshot
        return cachedCards.firstOrNull { it.id == cardID }
            ?.locations?.filter { it.locationName == locationKey }
            ?.sumOf { it.quantity } ?: 0
    }

    private var cachedCards: List<InventoryCardSummary> = emptyList()

    init {
        viewModelScope.launch {
            repository.inventoryCardsFlow().collect { cards ->
                cachedCards = cards
            }
        }
    }

    val availableDomains: StateFlow<List<String>> = combine(
        repository.inventoryCardsFlow(),
        repository.catalogueCardsFlow(),
        _domainFilters,
    ) { invCards, catCards, _ ->
        (invCards.flatMap { it.identity.appVisibleDomains } +
            catCards.flatMap { it.identity.appVisibleDomains })
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class EditState(
    val editing: Boolean,
    val saving: Boolean,
    val error: String?,
    val success: String?,
)
