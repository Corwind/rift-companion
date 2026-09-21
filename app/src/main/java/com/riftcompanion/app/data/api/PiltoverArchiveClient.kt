package com.riftcompanion.app.data.api

import com.riftcompanion.app.security.CredentialStore
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Piltover Archive external API.
 *
 * Base URL: https://piltoverarchive.com/api/external/v1
 * Auth: Bearer token (Clerk session token)
 *
 * Used to sync inventory (as collection) and deck definitions
 * from CardNexus (source of truth) to Piltover Archive.
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
    data class CardSearchResult(
        val id: String,
        val name: String,
        val variantId: String? = null,
    )

    suspend fun searchCards(query: String, limit: Int = 100): Result<List<CardSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/cards?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit"
            val request = authRequestBuilder(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA search failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CardSearchResult.serializer()),
                body,
            )
        }
    }

    @Serializable
    data class BatchCardRequest(val ids: List<String>)

    @Serializable
    data class BatchCardResult(
        val id: String,
        val name: String,
        val nameSlug: String? = null,
    )

    suspend fun batchResolveCards(ids: List<String>): Result<List<BatchCardResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                BatchCardRequest.serializer(),
                BatchCardRequest(ids),
            ).toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/cards/batch")
                .post(requestBody)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA batch resolve failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(BatchCardResult.serializer()),
                body,
            )
        }
    }

    // ── Collection ───────────────────────────────────────────────────

    @Serializable
    data class CollectionEntry(
        val id: String,
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int,
    )

    suspend fun getCollection(): Result<List<CollectionEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/collection/export").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA collection export failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CollectionEntry.serializer()),
                body,
            )
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
    data class DeckSummary(
        val uuid: String,
        val name: String,
        val description: String? = null,
    )

    suspend fun getDecks(limit: Int = 100): Result<List<DeckSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE_URL/decks?limit=$limit"
            val request = authRequestBuilder(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA get decks failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(DeckSummary.serializer()),
                body,
            )
        }
    }

    @Serializable
    data class DeckDetail(
        val uuid: String,
        val name: String,
        val description: String? = null,
        val sections: DeckSections? = null,
    )

    @Serializable
    data class DeckSections(
        val champions: List<DeckCardEntry> = emptyList(),
        val battlefields: List<DeckCardEntry> = emptyList(),
        val runes: List<DeckCardEntry> = emptyList(),
        val maindeck: List<DeckCardEntry> = emptyList(),
        val sideboard: List<DeckCardEntry> = emptyList(),
        val bench: List<DeckCardEntry> = emptyList(),
    )

    @Serializable
    data class DeckCardEntry(
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int,
    )

    suspend fun getDeck(uuid: String): Result<DeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/decks/$uuid").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA get deck failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            json.decodeFromString(DeckDetail.serializer(), body)
        }
    }

    @Serializable
    data class DeckCreate(
        val name: String,
        val description: String? = null,
        val sections: DeckSections? = null,
    )

    suspend fun createDeck(deck: DeckCreate): Result<DeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(DeckCreate.serializer(), deck)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/decks")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA create deck failed: ${response.code}")
            val responseBody = response.body!!.string()
            response.close()
            json.decodeFromString(DeckDetail.serializer(), responseBody)
        }
    }

    suspend fun updateDeck(uuid: String, deck: DeckCreate): Result<DeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(DeckCreate.serializer(), deck)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/decks/$uuid")
                .patch(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA update deck failed: ${response.code}")
            val responseBody = response.body!!.string()
            response.close()
            json.decodeFromString(DeckDetail.serializer(), responseBody)
        }
    }
}
