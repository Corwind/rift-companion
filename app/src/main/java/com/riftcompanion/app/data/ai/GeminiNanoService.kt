package com.riftcompanion.app.data.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google Gemini Nano via ML Kit GenAI Prompt API for on-device inference.
 * Only available on supported devices. Call [isAvailable] before using.
 * All calls are wrapped in try-catch since ML Kit can throw fatal exceptions
 * on unsupported devices.
 */
@Singleton
class GeminiNanoService @Inject constructor() {

    companion object {
        private const val TAG = "GeminiNanoService"
    }

    /**
     * Checks whether Gemini Nano is available on this device.
     * Returns false if the feature is unavailable, not downloaded, or if
     * ML Kit throws an exception (unsupported device).
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val client = Generation.getClient()
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> true
                else -> false
            }
        }.getOrElse { e ->
            Log.w(TAG, "Gemini Nano not available: ${e.message}")
            false
        }
    }

    /**
     * Generates a response from Gemini Nano for the given prompt.
     * Returns null if the feature is unavailable or generation fails.
     */
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = Generation.getClient()
            val status = client.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w(TAG, "Gemini Nano not available: $status")
                return@runCatching null
            }
            val response = client.generateContent(prompt)
            response.candidates.firstOrNull()?.text
        }.getOrElse { e ->
            Log.e(TAG, "Generation failed", e)
            null
        }
    }

    /**
     * Generates a response with a system instruction and context.
     * The [systemInstruction] shapes the model's behavior (e.g., "You are a rules assistant").
     * The [context] provides relevant information to answer from.
     * The [query] is the user's question.
     */
    suspend fun generateWithcontext(
        systemInstruction: String,
        context: String,
        query: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = Generation.getClient()
            val status = client.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.w(TAG, "Gemini Nano not available: $status")
                return@runCatching null
            }
            val fullPrompt = buildString {
                appendLine(systemInstruction)
                appendLine()
                appendLine("Rules context:")
                appendLine(context)
                appendLine()
                appendLine("Question: $query")
                appendLine()
                appendLine("Answer based on the rules context above. Be concise and accurate. If the answer is not in the context, say you don't know.")
            }
            val response = client.generateContent(fullPrompt)
            response.candidates.firstOrNull()?.text
        }.getOrElse { e ->
            Log.e(TAG, "Generation failed", e)
            null
        }
    }
}
