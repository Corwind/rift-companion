package com.riftcompanion.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riftcompanion.app.ui.theme.AppAccentPalette
import com.riftcompanion.app.ui.theme.AppAppearance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rift_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val APPEARANCE_KEY = stringPreferencesKey("appearance")
        val ACCENT_KEY = stringPreferencesKey("accent")
        val SECONDARY_ACCENT_KEY = stringPreferencesKey("secondary_accent")
        val TRANSPARENCY_KEY = stringPreferencesKey("background_transparency")
        val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        val SETUP_COMPLETE_KEY = booleanPreferencesKey("setup_complete")
        val DATA_WARNING_ACK_KEY = booleanPreferencesKey("data_warning_acked")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val PRICE_MARKET_KEY = stringPreferencesKey("price_currency")
        val SYNC_TO_PA_KEY = booleanPreferencesKey("sync_to_piltover_archive")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { prefs ->
        SettingsData(
            appearance = prefs[APPEARANCE_KEY]?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() } ?: AppAppearance.System,
            accent = prefs[ACCENT_KEY]?.let { runCatching { AppAccentPalette.valueOf(it) }.getOrNull() } ?: AppAccentPalette.RiftBlue,
            secondaryAccent = prefs[SECONDARY_ACCENT_KEY]?.let { runCatching { AppAccentPalette.valueOf(it) }.getOrNull() },
            backgroundTransparency = prefs[TRANSPARENCY_KEY]?.toFloatOrNull() ?: 0f,
            biometricEnabled = prefs[BIOMETRIC_ENABLED_KEY] ?: false,
            setupComplete = prefs[SETUP_COMPLETE_KEY] ?: false,
            dataWarningAcked = prefs[DATA_WARNING_ACK_KEY] ?: false,
            geminiApiKey = prefs[GEMINI_API_KEY],
            priceMarket = prefs[PRICE_MARKET_KEY]?.let { runCatching { PriceMarket.valueOf(it) }.getOrNull() } ?: PriceMarket.EUR,
            syncToPiltoverArchive = prefs[SYNC_TO_PA_KEY] ?: false,
        )
    }

    suspend fun setAppearance(value: AppAppearance) {
        context.dataStore.edit { it[APPEARANCE_KEY] = value.name }
    }

    suspend fun setAccent(value: AppAccentPalette) {
        context.dataStore.edit { it[ACCENT_KEY] = value.name }
    }

    suspend fun setSecondaryAccent(value: AppAccentPalette?) {
        context.dataStore.edit { prefs ->
            if (value != null) prefs[SECONDARY_ACCENT_KEY] = value.name
            else prefs.remove(SECONDARY_ACCENT_KEY)
        }
    }

    suspend fun setBackgroundTransparency(value: Float) {
        context.dataStore.edit { it[TRANSPARENCY_KEY] = value.toString() }
    }

    suspend fun setBiometricEnabled(value: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED_KEY] = value }
    }

    suspend fun setSetupComplete(value: Boolean) {
        context.dataStore.edit { it[SETUP_COMPLETE_KEY] = value }
    }

    suspend fun setDataWarningAcked(value: Boolean) {
        context.dataStore.edit { it[DATA_WARNING_ACK_KEY] = value }
    }

    suspend fun setGeminiApiKey(value: String) {
        context.dataStore.edit { it[GEMINI_API_KEY] = value.trim() }
    }

    suspend fun getGeminiApiKey(): String? {
        return context.dataStore.data.first()[GEMINI_API_KEY]
    }

    suspend fun setPriceMarket(value: PriceMarket) {
        context.dataStore.edit { it[PRICE_MARKET_KEY] = value.name }
    }

    suspend fun setSyncToPiltoverArchive(value: Boolean) {
        context.dataStore.edit { it[SYNC_TO_PA_KEY] = value }
    }
}

data class SettingsData(
    val appearance: AppAppearance = AppAppearance.System,
    val accent: AppAccentPalette = AppAccentPalette.RiftBlue,
    val secondaryAccent: AppAccentPalette? = null,
    val backgroundTransparency: Float = 0f,
    val biometricEnabled: Boolean = false,
    val setupComplete: Boolean = false,
    val dataWarningAcked: Boolean = false,
    val geminiApiKey: String? = null,
    val priceMarket: PriceMarket = PriceMarket.EUR,
    val syncToPiltoverArchive: Boolean = false,
)

enum class PriceMarket(val title: String, val description: String, val symbol: String, val code: String) {
    EUR("Cardmarket", "Show Cardmarket prices in euros", "€", "EUR"),
    USD("TCGplayer", "Show TCGplayer prices in dollars", "\$", "USD"),
    BOTH("Both", "Show both Cardmarket and TCGplayer", "", "BOTH"),
}
