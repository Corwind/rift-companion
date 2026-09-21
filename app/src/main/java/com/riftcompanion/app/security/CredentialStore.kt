package com.riftcompanion.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the CardNexus API key in EncryptedSharedPreferences (AES-256-GCM).
 * The key never appears in plain text on disk. Biometric gating is enforced
 * at the app-lock and settings-access level, not at the file level, so the
 * key is only readable after the user has authenticated via BiometricPrompt.
 */
class CredentialStore private constructor(
    private val prefs: EncryptedSharedPreferences,
) {
    companion object {
        private const val FILE_NAME = "cardnexus_credentials"
        private const val KEY_API_KEY = "api_key"

        fun create(context: Context): CredentialStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ) as EncryptedSharedPreferences

            return CredentialStore(prefs)
        }
    }

    fun loadApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun deleteApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun hasApiKey(): Boolean = !prefs.getString(KEY_API_KEY, null).isNullOrBlank()

    // ── Piltover Archive credentials ───────────────────────────────

    private val KEY_PA_TOKEN = "piltover_archive_token"
    private val KEY_PA_TOKEN_EXPIRY = "piltover_archive_token_expiry"

    fun loadPiltoverArchiveToken(): String? = prefs.getString(KEY_PA_TOKEN, null)

    fun loadPiltoverArchiveTokenExpiry(): Long = prefs.getLong(KEY_PA_TOKEN_EXPIRY, 0L)

    fun savePiltoverArchiveToken(token: String, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_PA_TOKEN, token)
            .putLong(KEY_PA_TOKEN_EXPIRY, expiresAt)
            .apply()
    }

    fun deletePiltoverArchiveToken() {
        prefs.edit()
            .remove(KEY_PA_TOKEN)
            .remove(KEY_PA_TOKEN_EXPIRY)
            .apply()
    }

    fun hasValidPiltoverArchiveToken(): Boolean {
        val token = prefs.getString(KEY_PA_TOKEN, null)
        val expiry = prefs.getLong(KEY_PA_TOKEN_EXPIRY, 0L)
        return !token.isNullOrBlank() && expiry > System.currentTimeMillis()
    }
}
