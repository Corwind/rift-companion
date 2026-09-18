package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.prefs.PriceMarket
import com.riftcompanion.app.data.prefs.SettingsData
import com.riftcompanion.app.data.prefs.SettingsDataStore
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.BanlistEntry
import com.riftcompanion.app.domain.model.CatalogueCardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CatalogueUiState(
    val cards: List<CatalogueCardSummary> = emptyList(),
    val searchQuery: String = "",
    val viewMode: CardViewMode = CardViewMode.GRID,
    val isLoading: Boolean = false,
    val error: String? = null,
    val bannedCards: Set<String> = emptySet(),
    val priceMarket: PriceMarket = PriceMarket.EUR,
)

private data class CatalogueIntermediate(
    val cards: List<CatalogueCardSummary>,
    val search: String,
    val viewMode: CardViewMode,
    val banlist: List<BanlistEntry>,
)

@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: RiftRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _viewMode = MutableStateFlow(CardViewMode.GRID)

    val uiState: StateFlow<CatalogueUiState> = combine(
        repository.catalogueCardsFlow(),
        _searchQuery,
        _viewMode,
        repository.banlistFlow(),
    ) { cards, search, viewMode, banlist ->
        CatalogueIntermediate(cards, search, viewMode, banlist)
    }.combine(settingsDataStore.settingsFlow) { intermediate, settings ->
        val bannedNames = intermediate.banlist
            .filter { it.cardType == com.riftcompanion.app.domain.model.BanlistEntryType.CARD }
            .map { it.cardName.lowercase() }
            .toSet()
        val filtered = (if (intermediate.search.isBlank()) intermediate.cards else intermediate.cards.filter { card ->
            card.identity.appSearchText.contains(intermediate.search, ignoreCase = true) ||
                card.expansionSlugs.any { it.contains(intermediate.search, ignoreCase = true) } ||
                card.rarities.any { it.contains(intermediate.search, ignoreCase = true) }
        }).sortedBy { it.identity.displayName.lowercase() }
        CatalogueUiState(
            cards = filtered,
            searchQuery = intermediate.search,
            viewMode = intermediate.viewMode,
            isLoading = false,
            error = null,
            bannedCards = bannedNames,
            priceMarket = settings.priceMarket,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogueUiState(isLoading = true))

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setViewMode(mode: CardViewMode) { _viewMode.value = mode }
}
