package com.riftcompanion.app.data.api

import com.riftcompanion.app.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * Client for the Piltover Archive external API.
 *
 * Base URL: https://piltoverarchive.com/api/external/v1
 * Auth: Bearer token (Clerk session token from captive WebView login)
 *
 * API responses wrap lists in {"data": [...], "pagination": {...}}.
 * Cards have a nested structure: variant `id` → `card.id` (card UUID) + `card.name`.
 */
class PiltoverArchiveClient @Inject constructor(
    private val credentialStore: CredentialStore,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        const val BASE_URL = "https://piltoverarchive.com/api/external/v1"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private fun authRequestBuilder(url: String): Request.Builder {
        val token = credentialStore.loadPiltoverArchiveToken()
            ?: throw Exception("No Piltover Archive token. Please log in.")
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
    }

    fun isAvailable(): Boolean = credentialStore.hasValidPiltoverArchiveToken()

    // ── Cards ─────────────────────────────────────────────────────────

    @Serializable
    data class PaCardColor(
        val id: String? = null,
        val name: String? = null,
    )

    @Serializable
    data class PaCardInfo(
        val id: String,
        val name: String,
        val type: String? = null,
        val superType: String? = null,
        val energy: Int? = null,
        val might: Int? = null,
        val power: Int? = null,
    )

    @Serializable
    data class PaCardVariant(
        val id: String,
        val variantNumber: String? = null,
        val rarity: String? = null,
        val variantType: String? = null,
        val card: PaCardInfo? = null,
        val cardmarketId: Long? = null,
        val tcgplayerId: Long? = null,
    )

    @Serializable
    data class CardListResponse(
        val data: List<PaCardVariant> = emptyList(),
        val pagination: Pagination? = null,
    )

    @Serializable
    data class Pagination(
        val total: Int = 0,
        val page: Int = 1,
        val limit: Int = 0,
        val totalPages: Int = 0,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false,
    )

    /** Fetch all card variants. Returns a map of card name → cardId (first variant found). */
    suspend fun fetchAllCards(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = mutableMapOf<String, String>()
            var page = 1
            val limit = 500
            do {
                val url = "$BASE_URL/cards?limit=$limit&page=$page"
                val request = authRequestBuilder(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("PA cards fetch failed: ${response.code}")
                val body = response.body!!.string()
                response.close()
                val parsed = json.decodeFromString(CardListResponse.serializer(), body)
                for (variant in parsed.data) {
                    val cardName = variant.card?.name ?: continue
                    val cardId = variant.card.id
                    // Keep first variant's cardId per name
                    if (cardName !in result) {
                        result[cardName] = cardId
                    }
                }
                page++
            } while (parsed.pagination?.hasNext == true)
            result
        }
    }

    // ── Collection ───────────────────────────────────────────────────

    @Serializable
    data class CollectionEntry(
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int,
    )

    @Serializable
    data class CollectionListResponse(
        val data: List<CollectionEntry> = emptyList(),
    )

    suspend fun getCollection(): Result<List<CollectionEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/collection/export").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA collection export failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            val parsed = json.decodeFromString(CollectionListResponse.serializer(), body)
            parsed.data
        }
    }

    @Serializable
    data class CollectionUpdate(
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int,
    )

    suspend fun updateCollection(entries: List<CollectionUpdate>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (entry in entries) {
                val body = json.encodeToString(CollectionUpdate.serializer(), entry)
                    .toRequestBody("application/json".toMediaType())
                val request = authRequestBuilder("$BASE_URL/collection/${entry.cardId}")
                    .patch(body)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("PA collection update failed: ${response.code}")
                response.close()
            }
        }
    }

    // ── Decks ─────────────────────────────────────────────────────────

    @Serializable
    data class PaDeckCardEntry(
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int? = null,
    )

    @Serializable
    data class PaDeckLegend(
        val id: String,
        val name: String? = null,
        val variantNumber: String? = null,
    )

    @Serializable
    data class PaDeckDetail(
        val id: String,
        val name: String,
        val description: String? = null,
        val legend: PaDeckLegend? = null,
        val champions: List<PaDeckCardEntry> = emptyList(),
        val battlefields: List<PaDeckCardEntry> = emptyList(),
        val runes: List<PaDeckCardEntry> = emptyList(),
        val maindeck: List<PaDeckCardEntry> = emptyList(),
        val sideboard: List<PaDeckCardEntry> = emptyList(),
        val bench: List<PaDeckCardEntry> = emptyList(),
        val additionalLegends: List<PaDeckCardEntry> = emptyList(),
    )

    @Serializable
    data class DeckListResponse(
        val data: List<PaDeckDetail> = emptyList(),
        val pagination: Pagination? = null,
    )

    suspend fun getDecks(limit: Int = 100): Result<List<PaDeckDetail>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/decks?limit=$limit"
            val request = authRequestBuilder(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA get decks failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            val parsed = json.decodeFromString(DeckListResponse.serializer(), body)
            parsed.data
        }
    }

    suspend fun getDeck(uuid: String): Result<PaDeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/decks/$uuid").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA get deck failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(PaDeckDetail.serializer(), body)
        }
    }

    @Serializable
    data class DeckWrite(
        val name: String,
        val description: String? = null,
        val legendId: String? = null,
        val champions: List<PaDeckCardEntry> = emptyList(),
        val battlefields: List<PaDeckCardEntry> = emptyList(),
        val runes: List<PaDeckCardEntry> = emptyList(),
        val maindeck: List<PaDeckCardEntry> = emptyList(),
        val sideboard: List<PaDeckCardEntry> = emptyList(),
        val bench: List<PaDeckCardEntry> = emptyList(),
    )

    suspend fun createDeck(deck: DeckWrite): Result<PaDeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(DeckWrite.serializer(), deck)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/decks")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA create deck failed: ${response.code}")
            val responseBody = response.body!!.string()
            response.close()
            json.decodeFromString(PaDeckDetail.serializer(), responseBody)
        }
    }

    suspend fun updateDeck(uuid: String, deck: DeckWrite): Result<PaDeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(DeckWrite.serializer(), deck)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/decks/$uuid")
                .patch(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA update deck failed: ${response.code}")
            val responseBody = response.body!!.string()
            response.close()
            json.decodeFromString(PaDeckDetail.serializer(), responseBody)
        }
    }
}
