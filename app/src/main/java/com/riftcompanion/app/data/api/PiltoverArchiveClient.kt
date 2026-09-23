package com.riftcompanion.app.data.api

import com.riftcompanion.app.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the Piltover Archive external API.
 *
 * Base URL: https://piltoverarchive.com/api/external/v1
 * Auth: Bearer token (Clerk session JWT, cached and refreshed on expiry)
 *
 * API responses wrap lists in {"data": [...], "pagination": {...}}.
 * Cards have a nested structure: variant `id` → `card.id` (card UUID) + `card.name`.
 */
@Singleton
class PiltoverArchiveClient @Inject constructor(
    private val credentialStore: CredentialStore,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        const val BASE_URL = "https://piltoverarchive.com/api/external/v1"
        const val CLERK_PUBLISHABLE_KEY = "pk_live_Y2xlcmsucGlsdG92ZXJhcmNoaXZlLmNvbSQ"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    @Volatile
    private var cachedJwt: String? = null
    @Volatile
    private var cachedJwtExpiry: Long = 0L

    private fun authRequestBuilder(url: String): Request.Builder {
        val token = getFreshClerkToken()
        val builder = Request.Builder().url(url)
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        } else {
            throw Exception("No Piltover Archive credentials. Please log in.")
        }
        return builder
    }

    /**
     * Gets a Clerk session JWT, cached until expiry (minus 10s buffer).
     * Clerk JWTs expire in ~60s, so we refresh only when expired.
     */
    @Synchronized
    private fun getFreshClerkToken(): String? {
        // Return cached JWT if still valid
        val now = System.currentTimeMillis()
        if (cachedJwt != null && now < cachedJwtExpiry) {
            return cachedJwt
        }

        val cookies = credentialStore.loadPiltoverArchiveCookies()
        if (cookies.isNullOrBlank()) return credentialStore.loadPiltoverArchiveToken()

        return try {
            // Step 1: Get session ID from Clerk
            val clientRequest = Request.Builder()
                .url("https://clerk.piltoverarchive.com/v1/client")
                .header("Cookie", cookies)
                .header("Authorization", CLERK_PUBLISHABLE_KEY)
                .get()
                .build()
            val clientResponse = okHttpClient.newCall(clientRequest).execute()
            if (!clientResponse.isSuccessful) {
                clientResponse.close()
                return credentialStore.loadPiltoverArchiveToken()
            }
            val clientBody = clientResponse.body!!.string()
            clientResponse.close()

            val sessionId = try {
                val parsed = json.decodeFromString(JsonObject.serializer(), clientBody)
                parsed["response"]?.jsonObject?.get("last_active_session_id")?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }

            if (sessionId == null) {
                return credentialStore.loadPiltoverArchiveToken()
            }

            // Step 2: Get fresh JWT
            val tokenRequest = Request.Builder()
                .url("https://clerk.piltoverarchive.com/v1/client/sessions/$sessionId/tokens")
                .header("Cookie", cookies)
                .header("Authorization", CLERK_PUBLISHABLE_KEY)
                .post(ByteArray(0).toRequestBody("application/json".toMediaType()))
                .build()
            val tokenResponse = okHttpClient.newCall(tokenRequest).execute()
            if (!tokenResponse.isSuccessful) {
                tokenResponse.close()
                return credentialStore.loadPiltoverArchiveToken()
            }
            val tokenBody = tokenResponse.body!!.string()
            tokenResponse.close()

            val jwt = try {
                val parsed = json.decodeFromString(JsonObject.serializer(), tokenBody)
                parsed["jwt"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }

            if (jwt != null) {
                cachedJwt = jwt
                cachedJwtExpiry = extractJwtExpiry(jwt) - 10_000 // 10s buffer
            }
            jwt
        } catch (e: Exception) {
            credentialStore.loadPiltoverArchiveToken()
        }
    }

    private fun extractJwtExpiry(token: String): Long {
        try {
            val parts = token.split(".")
            if (parts.size < 2) return System.currentTimeMillis() + 60_000
            val payload = parts[1]
            val decoded = android.util.Base64.decode(
                payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '='),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
            ).toString(Charsets.UTF_8)
            val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
            val match = expRegex.find(decoded)
            if (match != null) return match.groupValues[1].toLong() * 1000
        } catch (_: Exception) {}
        return System.currentTimeMillis() + 60_000
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
        val mightBonus: Int? = null,
        val maxCopies: Int? = null,
        val banEffectiveDate: String? = null,
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

    data class PaVariantMatch(
        val cardId: String,
        val variantId: String,
        val variantType: String? = null,
    )

    /** Fetch all card variants. Returns Pair(variantNumberMap, cardInfoMap) where:
     *  - variantNumberMap: variantNumber (lowercase) → List of PaVariantMatch (one per PA variant)
     *  - cardInfoMap: cardName → PaCardInfo (for enrichment)
     */
    suspend fun fetchAllCards(): Result<Pair<Map<String, List<PaVariantMatch>>, Map<String, PaCardInfo>>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = mutableMapOf<String, MutableList<PaVariantMatch>>()
            val cardInfoMap = mutableMapOf<String, PaCardInfo>()
            var page = 1
            val limit = 100
            do {
                val url = "$BASE_URL/cards?limit=$limit&page=$page"
                val request = authRequestBuilder(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw Exception("PA cards fetch failed: ${response.code}")
                }
                val body = response.body!!.string()
                response.close()
                val parsed = json.decodeFromString(CardListResponse.serializer(), body)
                for (variant in parsed.data) {
                    val cardName = variant.card?.name ?: continue
                    val cardId = variant.card.id
                    val variantId = variant.id
                    val variantNumber = variant.variantNumber
                    if (!variantNumber.isNullOrBlank()) {
                        result.getOrPut(variantNumber) { mutableListOf() }.add(PaVariantMatch(cardId, variantId, variant.variantType))
                    }
                    // Keep card info for enrichment (first variant wins)
                    if (cardName !in cardInfoMap) {
                        cardInfoMap[cardName] = variant.card!!
                    }
                }
                page++
            } while (parsed.pagination?.hasNext == true)
            result to cardInfoMap
        }
    }

    // ── Collection ───────────────────────────────────────────────────

    @Serializable
    data class CollectionEntry(
        val id: String? = null,
        val collectionId: String? = null,
        val variantId: String? = null,
        val quantity: Int,
    )

    @Serializable
    data class CollectionListResponse(
        val data: List<CollectionEntry> = emptyList(),
        val pagination: Pagination? = null,
    )

    suspend fun getCollection(): Result<List<CollectionEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val allEntries = mutableListOf<CollectionEntry>()
            var page = 1
            val limit = 100
            do {
                val url = "$BASE_URL/collection?limit=$limit&page=$page"
                val request = authRequestBuilder(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    if (page == 1) {
                        val exportRequest = authRequestBuilder("$BASE_URL/collection/export").build()
                        val exportResponse = okHttpClient.newCall(exportRequest).execute()
                        if (!exportResponse.isSuccessful) throw Exception("PA collection export failed: ${exportResponse.code}")
                        val exportBody = exportResponse.body!!.string()
                        exportResponse.close()
                        return@runCatching json.decodeFromString(CollectionListResponse.serializer(), exportBody).data
                    }
                    break
                }
                val body = response.body!!.string()
                response.close()
                val parsed = json.decodeFromString(CollectionListResponse.serializer(), body)
                allEntries.addAll(parsed.data)
                page++
            } while (parsed.pagination?.hasNext == true && page <= 1000)
            allEntries
        }
    }

    @Serializable
    data class CollectionUpdate(
        val variantId: String,
        val quantity: Int,
    )

    /** Create a new collection entry. */
    suspend fun createCollectionEntry(entry: CollectionUpdate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(CollectionUpdate.serializer(), entry)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/collection")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA collection create failed: ${response.code}")
            response.close()
        }
    }

    /** Update an existing collection entry's quantity. Path uses variantId (UUID). */
    suspend fun updateCollectionEntry(variantId: String, quantity: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = """{"quantity":$quantity}""".toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/collection/$variantId")
                .patch(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("PA collection update failed: ${response.code}")
            }
            response.close()
        }
    }

    /** Delete a collection entry. Path uses variantId (UUID). */
    suspend fun deleteCollectionEntry(variantId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/collection/$variantId")
                .delete()
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA collection delete failed: ${response.code}")
            response.close()
        }
    }

    // ── Decks ─────────────────────────────────────────────────────────

    @Serializable
    data class PaDeckCardEntry(
        val cardId: String,
        val variantId: String? = null,
        val quantity: Int? = null,
    )

    /** Entry for writing decks — both cardId and variantId are required. */
    @Serializable
    data class PaDeckWriteEntry(
        val cardId: String,
        val variantId: String,
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
        val authorId: String? = null,
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

    /** Get the current user's PA UUID from Clerk session user.external_id. */
    fun getPaUserId(): String? {
        val cookies = credentialStore.loadPiltoverArchiveCookies()
        if (cookies.isNullOrBlank()) return null

        return try {
            val clientRequest = Request.Builder()
                .url("https://clerk.piltoverarchive.com/v1/client")
                .header("Cookie", cookies)
                .header("Authorization", CLERK_PUBLISHABLE_KEY)
                .get()
                .build()
            val clientResponse = okHttpClient.newCall(clientRequest).execute()
            if (!clientResponse.isSuccessful) {
                clientResponse.close()
                return null
            }
            val clientBody = clientResponse.body!!.string()
            clientResponse.close()

            val parsed = json.decodeFromString(JsonObject.serializer(), clientBody)
            val sessions = parsed["response"]?.jsonObject?.get("sessions")?.jsonArray
            val firstSession = sessions?.firstOrNull()?.jsonObject
            val user = firstSession?.get("user")?.jsonObject
            if (user != null) {
                // PA stores their UUID in external_id
                val externalId = user["external_id"]?.jsonPrimitive?.content
                if (externalId != null) return externalId
                // Fall back to public_metadata
                val publicMetadata = user["public_metadata"]?.jsonObject
                if (publicMetadata != null) {
                    for (key in publicMetadata.keys) {
                        val value = publicMetadata[key]?.jsonPrimitive?.content
                        if (value != null && value.contains('-')) return value
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Get the current user's decks (including private/draft). Requires auth. */
    suspend fun getMyDecks(): Result<List<PaDeckDetail>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder("$BASE_URL/decks/my").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("PA get my decks failed: ${response.code}")
            val body = response.body!!.string()
            response.close()
            val parsed = json.decodeFromString(DeckListResponse.serializer(), body)
            parsed.data
        }
    }

    suspend fun getDecks(limit: Int = 100, authorId: String? = null, page: Int = 1): Result<List<PaDeckDetail>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (authorId != null) {
                "$BASE_URL/decks?limit=$limit&page=$page&authorId=$authorId"
            } else {
                "$BASE_URL/decks?limit=$limit&page=$page"
            }
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
        val champions: List<PaDeckWriteEntry> = emptyList(),
        val battlefields: List<PaDeckWriteEntry> = emptyList(),
        val runes: List<PaDeckWriteEntry> = emptyList(),
        val maindeck: List<PaDeckWriteEntry> = emptyList(),
        val sideboard: List<PaDeckWriteEntry> = emptyList(),
        val bench: List<PaDeckWriteEntry> = emptyList(),
    )

    suspend fun createDeck(deck: DeckWrite): Result<PaDeckDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(DeckWrite.serializer(), deck)
                .toRequestBody("application/json".toMediaType())
            val request = authRequestBuilder("$BASE_URL/decks")
                .post(body)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("PA create deck failed: ${response.code}")
            }
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
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                response.close()
                throw Exception("PA update deck failed: ${response.code}")
            }
            val responseBody = response.body!!.string()
            response.close()
            json.decodeFromString(PaDeckDetail.serializer(), responseBody)
        }
    }

    /** Try to update a deck, removing entries with unknown variant IDs. Returns the deck detail or null. */
    suspend fun updateDeckSafe(uuid: String, deck: DeckWrite): Result<PaDeckDetail?> = withContext(Dispatchers.IO) {
        runCatching {
            updateDeckSafeRecursive(uuid, deck, maxRetries = 5)
        }
    }

    private suspend fun updateDeckSafeRecursive(uuid: String, deck: DeckWrite, maxRetries: Int): PaDeckDetail? {
        val body = json.encodeToString(DeckWrite.serializer(), deck)
            .toRequestBody("application/json".toMediaType())
        val request = authRequestBuilder("$BASE_URL/decks/$uuid")
            .patch(body)
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body!!.string()
            response.close()
            return json.decodeFromString(PaDeckDetail.serializer(), responseBody)
        }
        if (response.code == 400 && maxRetries > 0) {
            val errorBody = response.body?.string()
            response.close()
            val unknownIds = extractUnknownVariantIds(errorBody)
            if (unknownIds.isEmpty()) return null
            val filteredDeck = filterDeckEntries(deck, unknownIds)
            return updateDeckSafeRecursive(uuid, filteredDeck, maxRetries - 1)
        }
        response.close()
        return null
    }

    private fun extractUnknownVariantIds(errorBody: String?): Set<String> {
        if (errorBody == null) return emptySet()
        return try {
            val parsed = json.decodeFromString(JsonObject.serializer(), errorBody)
            val details = parsed["details"]?.jsonObject
            val variantIds = details?.get("variantIds")?.jsonArray
            variantIds?.mapNotNull { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun filterDeckEntries(deck: DeckWrite, unknownIds: Set<String>): DeckWrite {
        fun filter(entries: List<PaDeckWriteEntry>) = entries.filter { it.variantId !in unknownIds }
        // Also clear legendId if it's an unknown variant
        val filteredLegendId = if (deck.legendId != null && unknownIds.any { id -> deck.legendId == id }) null else deck.legendId
        return deck.copy(
            legendId = filteredLegendId,
            champions = filter(deck.champions),
            battlefields = filter(deck.battlefields),
            runes = filter(deck.runes),
            maindeck = filter(deck.maindeck),
            sideboard = filter(deck.sideboard),
            bench = filter(deck.bench),
        )
    }
}
