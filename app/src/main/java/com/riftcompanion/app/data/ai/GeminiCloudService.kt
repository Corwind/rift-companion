package com.riftcompanion.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the Google Gemini cloud API for rules Q&A.
 * Queries the ListModels endpoint to discover available models,
 * then picks the best one for text generation — no hardcoded model list.
 * Requires a Gemini API key stored in settings.
 */
@Singleton
class GeminiCloudService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "GeminiCloudService"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedModel: String? = null

    suspend fun isAvailable(apiKey: String?): Boolean = !apiKey.isNullOrBlank()

    private var cachedModels: List<String>? = null

    /**
     * Queries the ListModels API and returns all models supporting generateContent,
     * sorted to prefer flash models first (fast + cheap).
     */
    private suspend fun resolveModels(apiKey: String): List<String> {
        cachedModels?.let { return it }

        val resolved = runCatching {
            val request = Request.Builder()
                .url("${BASE_URL}models?key=$apiKey")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "ListModels failed: ${response.code}")
                return@runCatching listOf("gemini-flash-latest")
            }

            val body = response.body?.string() ?: return@runCatching listOf("gemini-flash-latest")
            response.close()

            val parsed = json.parseToJsonElement(body) as? JsonObject
            val models = parsed?.get("models") as? JsonArray ?: return@runCatching listOf("gemini-flash-latest")

            val generatable = models.mapNotNull { model ->
                val obj = model as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.content?.removePrefix("models/") ?: return@mapNotNull null
                val methods = (obj["supportedGenerationMethods"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?: emptyList()
                if ("generateContent" in methods) name else null
            }

            // Sort: prefer flash (fast + cheap), then pro, then everything else.
            // Within each group, sort by version descending (most recent first).
            val versionRegex = Regex("\\d+\\.\\d+")
            fun versionOf(name: String): Double {
                return versionRegex.find(name)?.value?.toDouble() ?: 0.0
            }
            val sorted = generatable.sortedWith(
                compareBy(
                    { !it.contains("flash") },
                    { it.contains("lite") },
                    { !it.contains("pro") },
                    { -versionOf(it) },  // descending version
                    { it },
                )
            )

            Log.d(TAG, "Generatable models (sorted): ${sorted.joinToString(", ")}")
            if (sorted.isEmpty()) listOf("gemini-flash-latest") else sorted
        }.getOrElse {
            Log.w(TAG, "Model resolution failed", it)
            listOf("gemini-flash-latest")
        }

        cachedModels = resolved
        return resolved
    }

    suspend fun generate(
        apiKey: String,
        systemInstruction: String,
        context: String,
        query: String,
        history: List<Pair<String, String>> = emptyList(),
    ): String? = withContext(Dispatchers.IO) {
        val models = resolveModels(apiKey)

        val contextPrompt = buildString {
            appendLine(systemInstruction)
            appendLine()
            if (context.isNotBlank()) {
                appendLine("Rules context:")
                appendLine(context)
                appendLine()
            }
        }

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                // System instruction + context as first user message
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", contextPrompt + "Answer questions about game rules clearly and concisely. Always cite rule numbers from the context. If the answer is not in the context, say you don't know.")
                        })
                    })
                })
                // Model acknowledgment
                add(buildJsonObject {
                    put("role", "model")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "Understood. I'll answer based on the rules context and card texts provided, quote rules directly without rewording, cite rule numbers when known, and won't mention when I don't know a number.")
                        })
                    })
                })
                // Conversation history
                for ((role, text) in history) {
                    add(buildJsonObject {
                        put("role", if (role == "user") "user" else "model")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", text)
                            })
                        })
                    })
                }
                // Current query
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", query)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.3)
                put("maxOutputTokens", 8192)
            })
        }

        // Try each candidate model until one works
        // On rate limits (429) or server errors (503), stop trying — don't waste quota
        for (model in models) {
            val request = Request.Builder()
                .url("${BASE_URL}models/$model:generateContent?key=$apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val result = runCatching {
                val response = okHttpClient.newCall(request).execute()
                Log.d(TAG, "Gemini API response: ${response.code} (model: $model)")
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    val errorCode = response.code
                    Log.w(TAG, "Model $model failed: $errorCode - ${errorBody?.take(200)}")
                    response.close()
                    // On 429 (rate limit) or 503 (overloaded), don't try more models — just fail
                    if (errorCode == 429 || errorCode == 503) {
                        return@withContext null
                    }
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                response.close()

                val parsed = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
                val candidates = parsed["candidates"] as? JsonArray ?: return@runCatching null
                val firstCandidate = candidates.firstOrNull() as? JsonObject ?: return@runCatching null
                val content = firstCandidate["content"] as? JsonObject ?: return@runCatching null
                val parts = content["parts"] as? JsonArray ?: return@runCatching null
                val firstPart = parts.firstOrNull() as? JsonObject ?: return@runCatching null
                (firstPart["text"] as? JsonPrimitive)?.content
            }.getOrElse { null }

            if (result != null) {
                cachedModel = model
                Log.i(TAG, "Success with model: $model")
                return@withContext result
            }
        }

        Log.e(TAG, "All models failed")
        null
    }
}
