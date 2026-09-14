package com.riftcompanion.app.data.ai

import android.util.Log
import com.riftcompanion.app.data.prefs.LlmPriority
import com.riftcompanion.app.data.prefs.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified AI service for rules Q&A.
 * Routes between on-device (Gemini Nano) and cloud (Gemini API)
 * based on the user's priority setting and availability.
 *
 * Two modes:
 * - CloudFirst (default): On-device → Cloud API
 * - PrivacyFirst: On-device → Cloud API (local LLM removed)
 *
 * Context retrieval is handled by [RulesRag] (local BM25 search).
 */
@Singleton
class RulesAiService @Inject constructor(
    private val geminiNanoService: GeminiNanoService,
    private val geminiCloudService: GeminiCloudService,
    private val settingsDataStore: SettingsDataStore,
) {
    companion object {
        private const val TAG = "RulesAiService"
    }

    suspend fun checkAvailability(): Availability {
        val settings = settingsDataStore.settingsFlow.first()
        Log.d(TAG, "Checking availability: geminiKey=${settings.geminiApiKey?.let { "set (${it.length} chars)" } ?: "null"}")
        val onDeviceAvailable = geminiNanoService.isAvailable()
        val cloudAvailable = geminiCloudService.isAvailable(settings.geminiApiKey)
        Log.d(TAG, "Availability: onDevice=$onDeviceAvailable, cloud=$cloudAvailable")
        return Availability(onDeviceAvailable, cloudAvailable)
    }

    suspend fun generate(
        systemInstruction: String,
        context: String,
        query: String,
        history: List<Pair<String, String>> = emptyList(),
    ): AiResult {
        val settings = settingsDataStore.settingsFlow.first()
        val onDevice = geminiNanoService.isAvailable()
        val cloud = geminiCloudService.isAvailable(settings.geminiApiKey)

        val chain = when (settings.llmPriority) {
            LlmPriority.CloudFirst -> buildList {
                add(BackendStep(Backend.OnDevice, onDevice))
                add(BackendStep(Backend.Cloud, cloud))
            }
            LlmPriority.PrivacyFirst -> buildList {
                add(BackendStep(Backend.OnDevice, onDevice))
                add(BackendStep(Backend.Cloud, cloud))
            }
        }

        for (step in chain) {
            if (!step.available) continue
            val result = tryBackend(step.backend, settings, systemInstruction, context, query, history)
            if (result is AiResult.Success) return result
        }

        val availableBackends = chain.filter { it.available }.map { it.backend }
        return if (availableBackends.isEmpty()) {
            AiResult.Unavailable("No AI backend available. Configure a Gemini API key in Settings or use a device with on-device AI support.")
        } else {
            AiResult.Error("All available AI backends failed to generate a response.")
        }
    }

    private suspend fun tryBackend(
        backend: Backend,
        settings: com.riftcompanion.app.data.prefs.SettingsData,
        systemInstruction: String,
        context: String,
        query: String,
        history: List<Pair<String, String>> = emptyList(),
    ): AiResult? {
        Log.d(TAG, "Trying backend: $backend")
        return runCatching {
            when (backend) {
                Backend.OnDevice -> geminiNanoService.generateWithcontext(systemInstruction, context, query)
                    ?.let { AiResult.Success(it, AiBackend.OnDevice) }
                Backend.Cloud -> settings.geminiApiKey?.let { key ->
                    Log.d(TAG, "Calling cloud with key length ${key.length}")
                    val result = geminiCloudService.generate(key, systemInstruction, context, query, history)
                    Log.d(TAG, "Cloud generate returned: ${result?.length ?: "null"} chars")
                    result?.let { AiResult.Success(it, AiBackend.Cloud) }
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "Backend $backend failed", e)
            null
        }
    }
}

enum class Backend { OnDevice, Cloud }
enum class AiBackend { OnDevice, Cloud }

data class BackendStep(val backend: Backend, val available: Boolean)

data class Availability(
    val onDevice: Boolean,
    val cloud: Boolean,
) {
    val any: Boolean get() = onDevice || cloud
}

sealed class AiResult {
    data class Success(val text: String, val backend: AiBackend) : AiResult()
    data class Error(val message: String) : AiResult()
    data class Unavailable(val message: String) : AiResult()
}
