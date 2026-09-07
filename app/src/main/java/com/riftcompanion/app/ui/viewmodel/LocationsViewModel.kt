package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationsUiState(
    val locations: List<LocationPolicy> = emptyList(),
    val cardCountsByLocation: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

data class LocationEditState(
    val isEditing: Boolean = false,
    val location: LocationPolicy? = null,
    val name: String = "",
    val color: String? = null,
    val icon: String? = null,
    val kind: LocationKind = LocationKind.Storage,
    val hidden: Boolean = false,
    val cardCount: Int = 0,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: RiftRepository,
) : ViewModel() {

    private val _editState = MutableStateFlow(LocationEditState())
    val editState: StateFlow<LocationEditState> = _editState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _isDeleting = MutableStateFlow(false)

    val uiState: StateFlow<LocationsUiState> = combine(
        repository.locationPoliciesFlow(),
        repository.inventoryCardsFlow(),
        _message,
    ) { policies, inventoryCards, message ->
        val cardCounts = inventoryCards.flatMap { it.locations }
            .groupBy { it.normalizedLocationName }
            .mapValues { (_, locs) -> locs.sumOf { it.quantity } }

        LocationsUiState(
            locations = policies.sortedBy { it.displayName },
            cardCountsByLocation = cardCounts,
            isLoading = false,
            error = null,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationsUiState(isLoading = true))

    fun startEdit(location: LocationPolicy, cardCount: Int) {
        _editState.value = LocationEditState(
            isEditing = true,
            location = location,
            name = location.displayName,
            color = location.color,
            icon = location.icon,
            kind = location.kind,
            hidden = location.hidden,
            cardCount = cardCount,
        )
    }

    fun updateName(name: String) {
        _editState.value = _editState.value.copy(name = name)
    }

    fun updateColor(color: String?) {
        _editState.value = _editState.value.copy(color = color)
    }

    fun updateIcon(icon: String?) {
        _editState.value = _editState.value.copy(icon = icon)
    }

    fun updateKind(kind: LocationKind) {
        // When marking as unavailable, auto-hide from inventory by default
        val newHidden = if (kind == LocationKind.Unavailable) true else _editState.value.hidden
        _editState.value = _editState.value.copy(kind = kind, hidden = newHidden)
    }

    fun updateHidden(hidden: Boolean) {
        _editState.value = _editState.value.copy(hidden = hidden)
    }

    fun showDeleteConfirm() {
        _editState.value = _editState.value.copy(showDeleteConfirm = true)
    }

    fun hideDeleteConfirm() {
        _editState.value = _editState.value.copy(showDeleteConfirm = false)
    }

    fun cancelEdit() {
        _editState.value = LocationEditState()
    }

    fun saveEdit() {
        val state = _editState.value
        val location = state.location ?: return
        if (state.name.isBlank()) return

        viewModelScope.launch {
            _editState.value = state.copy(isSaving = true)
            val result = repository.updateLocation(
                currentName = location.displayName,
                newName = state.name,
                color = state.color,
                icon = state.icon,
            )
            result.fold(
                onSuccess = { updated ->
                    // Update local policy
                    repository.updateLocationPolicy(
                        LocationPolicy(
                            normalizedName = updated.normalizedName,
                            displayName = updated.name,
                            color = updated.color,
                            icon = updated.icon,
                            kind = state.kind,
                            countsAsAvailable = state.kind == LocationKind.Storage,
                            hidden = state.hidden,
                        ),
                    )
                    _message.value = "Updated '${updated.name}' in CardNexus."
                    _editState.value = LocationEditState()
                },
                onFailure = { error ->
                    _editState.value = state.copy(isSaving = false)
                    _message.value = "Location update failed: ${error.message}"
                },
            )
        }
    }

    fun deleteLocation() {
        val state = _editState.value
        val location = state.location ?: return

        viewModelScope.launch {
            _editState.value = state.copy(isDeleting = true, showDeleteConfirm = false)
            val result = repository.deleteLocation(location.displayName)
            result.fold(
                onSuccess = {
                    repository.deleteLocationPolicy(location.normalizedName)
                    _message.value = "Deleted '${location.displayName}' from CardNexus."
                    _editState.value = LocationEditState()
                },
                onFailure = { error ->
                    _editState.value = state.copy(isDeleting = false)
                    _message.value = "Location deletion failed: ${error.message}"
                },
            )
        }
    }

    fun createLocation(name: String, color: String?, icon: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = repository.createLocation(name, color, icon)
            result.fold(
                onSuccess = { created ->
                    // Auto-create a local policy
                    repository.updateLocationPolicy(
                        LocationPolicy(
                            normalizedName = created.normalizedName,
                            displayName = created.name,
                            color = created.color,
                            icon = created.icon,
                            kind = LocationKind.Storage,
                            countsAsAvailable = true,
                            hidden = false,
                        ),
                    )
                    _message.value = "Created '${created.name}' in CardNexus."
                },
                onFailure = { error ->
                    _message.value = "Location creation failed: ${error.message}"
                },
            )
        }
    }

    fun toggleHidden(location: LocationPolicy) {
        // Don't allow unhiding an unavailable location — unavailable = always hidden
        if (location.kind == LocationKind.Unavailable && location.hidden) {
            _message.value = "Unavailable locations are always hidden from inventory."
            return
        }
        viewModelScope.launch {
            repository.updateLocationPolicy(location.copy(hidden = !location.hidden))
            _message.value = if (location.hidden) "Location '${location.displayName}' is now visible."
            else "Location '${location.displayName}' is now hidden from inventory."
        }
    }

    fun clearMessage() { _message.value = null }
}
