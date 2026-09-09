package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: RiftRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedLocation = MutableStateFlow<String?>(null)
    private val _domainFilters = MutableStateFlow<Set<String>>(emptySet())
    private val _viewMode = MutableStateFlow(CardViewMode.GRID)

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
    ) { (cards, locations), (search, location, domains, viewMode) ->
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
