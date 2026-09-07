package com.riftcompanion.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.prefs.SettingsData
import com.riftcompanion.app.data.prefs.SettingsDataStore
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.data.repository.SyncResult
import com.riftcompanion.app.network.NetworkUtils
import com.riftcompanion.app.security.CredentialStore
import com.riftcompanion.app.ui.theme.AppAccentPalette
import com.riftcompanion.app.ui.theme.AppAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val appearance: AppAppearance = AppAppearance.System,
    val accent: AppAccentPalette = AppAccentPalette.RiftBlue,
    val secondaryAccent: AppAccentPalette? = null,
    val backgroundTransparency: Float = 0f,
    val biometricEnabled: Boolean = false,
    val setupComplete: Boolean = false,
    val hasApiKey: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val syncError: String? = null,
    val dataWarningAcked: Boolean = false,
    val isMeteredConnection: Boolean = false,
    val showDataWarning: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val credentialStore: CredentialStore,
    private val repository: RiftRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settingsFlow: StateFlow<SettingsData> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsData())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { data ->
                _uiState.value = _uiState.value.copy(
                    appearance = data.appearance,
                    accent = data.accent,
                    secondaryAccent = data.secondaryAccent,
                    backgroundTransparency = data.backgroundTransparency,
                    biometricEnabled = data.biometricEnabled,
                    setupComplete = data.setupComplete,
                    hasApiKey = credentialStore.hasApiKey(),
                    dataWarningAcked = data.dataWarningAcked,
                )
            }
        }
        viewModelScope.launch {
            repository.getLastSyncTimestamp()?.let {
                _uiState.value = _uiState.value.copy(lastSyncTimestamp = it)
            }
        }
    }

    fun setAppearance(value: AppAppearance) {
        viewModelScope.launch { settingsDataStore.setAppearance(value) }
    }

    fun setAccent(value: AppAccentPalette) {
        viewModelScope.launch { settingsDataStore.setAccent(value) }
    }

    fun setSecondaryAccent(value: AppAccentPalette?) {
        viewModelScope.launch { settingsDataStore.setSecondaryAccent(value) }
    }

    fun setBackgroundTransparency(value: Float) {
        viewModelScope.launch { settingsDataStore.setBackgroundTransparency(value) }
    }

    fun setBiometricEnabled(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setBiometricEnabled(value) }
    }

    fun setSetupComplete(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setSetupComplete(value) }
    }

    fun setDataWarningAcked(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setDataWarningAcked(value) }
    }

    fun saveApiKey(key: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            credentialStore.saveApiKey(key)
            val result = repository.synchronize(forceCatalogue = true)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(hasApiKey = true, syncError = null)
                    onResult(true, null)
                },
                onFailure = { error ->
                    credentialStore.deleteApiKey()
                    _uiState.value = _uiState.value.copy(hasApiKey = false)
                    onResult(false, error.message)
                },
            )
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            credentialStore.deleteApiKey()
            _uiState.value = _uiState.value.copy(hasApiKey = false)
        }
    }

    /**
     * Initiates a sync. If on a metered connection and the user has not
     * permanently acknowledged the data warning, shows a warning dialog.
     * Otherwise proceeds directly.
     */
    fun synchronize() {
        if (_uiState.value.isSyncing) return

        val isMetered = NetworkUtils.isMeteredConnection(context)
        _uiState.value = _uiState.value.copy(isMeteredConnection = isMetered)

        if (isMetered && !_uiState.value.dataWarningAcked) {
            _uiState.value = _uiState.value.copy(showDataWarning = true)
            return
        }

        performSync()
    }

    /**
     * Called after the user accepts the data warning dialog.
     * @param dontShowAgain if true, permanently acknowledges the warning.
     */
    fun acceptDataWarning(dontShowAgain: Boolean) {
        _uiState.value = _uiState.value.copy(showDataWarning = false)
        if (dontShowAgain) {
            viewModelScope.launch { settingsDataStore.setDataWarningAcked(true) }
        }
        performSync()
    }

    fun dismissDataWarning() {
        _uiState.value = _uiState.value.copy(showDataWarning = false)
    }

    private fun performSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncMessage = "Connecting to CardNexus…",
                syncError = null,
            )
            val result = repository.synchronize(forceCatalogue = false)
            result.fold(
                onSuccess = { syncResult ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = "Sync complete: ${syncResult.inventoryLines} inventory lines, ${syncResult.locations} locations",
                        lastSyncTimestamp = syncResult.completedAt,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncError = error.message,
                        syncMessage = null,
                    )
                },
            )
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null, syncError = null)
    }
}
