package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.repository.RiftRepository
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
)

@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: RiftRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _viewMode = MutableStateFlow(CardViewMode.GRID)

    val uiState: StateFlow<CatalogueUiState> = combine(
        repository.catalogueCardsFlow(),
        _searchQuery,
        _viewMode,
    ) { cards, search, viewMode ->
        val filtered = (if (search.isBlank()) cards else cards.filter { card ->
            card.identity.appSearchText.contains(search, ignoreCase = true) ||
                card.expansionSlugs.any { it.contains(search, ignoreCase = true) } ||
                card.rarities.any { it.contains(search, ignoreCase = true) }
        }).sortedBy { it.identity.displayName.lowercase() }
        CatalogueUiState(
            cards = filtered,
            searchQuery = search,
            viewMode = viewMode,
            isLoading = false,
            error = null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogueUiState(isLoading = true))

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setViewMode(mode: CardViewMode) { _viewMode.value = mode }
}
