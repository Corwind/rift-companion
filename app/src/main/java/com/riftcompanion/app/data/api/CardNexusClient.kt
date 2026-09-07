package com.riftcompanion.app.data.api

import com.riftcompanion.app.data.api.dto.CatalogueProductDTO
import com.riftcompanion.app.domain.model.CatalogueFeedMetadata
import com.riftcompanion.app.domain.model.InventoryBulkMoveRequest
import com.riftcompanion.app.domain.model.InventoryBulkMoveResponse
import com.riftcompanion.app.domain.model.InventoryLine
import com.riftcompanion.app.domain.model.InventoryLocation
import com.riftcompanion.app.domain.model.InventoryLocationUpdateRequest
import com.riftcompanion.app.domain.model.InventoryLocationUpsertRequest
import com.riftcompanion.app.domain.model.CardPrinting
import com.riftcompanion.app.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CardNexus API client using OkHttp directly with kotlinx.serialization.
 * No Retrofit converter dependency needed. All network calls run on Dispatchers.IO.
 * No background work is scheduled — sync is strictly user-initiated.
 */
@Singleton
class CardNexusClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore,
) {
    companion object {
        const val BASE_URL = "https://public-api.cardnexus.com/v1/"
        const val GAME = "riftbound"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun authRequestBuilder(url: String): Request.Builder {
        val apiKey = credentialStore.loadApiKey()
            ?: throw Exception("No CardNexus API credential is configured.")
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
    }

    suspend fun verifyCredential(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { fetchLocationsInternal(); Unit }
    }

    suspend fun fetchAllInventoryLines(): Result<List<InventoryLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val allLines = mutableListOf<InventoryLine>()
            var cursor: String? = null
            do {
                val urlBuilder = (BASE_URL + "inventory").toHttpUrl().newBuilder()
                    .addQueryParameter("game", GAME)
                    .addQueryParameter("limit", "100")
                if (cursor != null) urlBuilder.addQueryParameter("cursor", cursor)
                val request = authRequestBuilder(urlBuilder.toString()).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw httpError(response.code, response.body?.string())
                }
                val page = json.decodeFromString(
                    com.riftcompanion.app.data.api.dto.InventoryPageDTO.serializer(),
                    response.body!!.string(),
                )
                allLines.addAll(page.data.map { DtoMapper.toDomain(it) })
                cursor = page.pagination.nextCursor
                response.close()
            } while (cursor != null)
            allLines
        }
    }

    suspend fun fetchLocations(): Result<List<InventoryLocation>> = withContext(Dispatchers.IO) {
        runCatching { fetchLocationsInternal() }
    }

    private fun fetchLocationsInternal(): List<InventoryLocation> {
        val request = authRequestBuilder(BASE_URL + "inventory/locations").build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw httpError(response.code, response.body?.string())
        }
        val dtos = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(com.riftcompanion.app.data.api.dto.InventoryLocationDTO.serializer()),
            response.body!!.string(),
        )
        response.close()
        return dtos.map { DtoMapper.toDomain(it) }
    }

    suspend fun fetchCatalogueMetadata(): Result<CatalogueFeedMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authRequestBuilder(BASE_URL + "feeds/$GAME/catalog").build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw httpError(response.code, response.body?.string())
            }
            val dto = json.decodeFromString(
                com.riftcompanion.app.data.api.dto.CatalogueFeedMetadataDTO.serializer(),
                response.body!!.string(),
            )
            response.close()
            DtoMapper.toDomain(dto)
        }
    }

    suspend fun downloadCatalogue(url: String, encoding: String): Result<Sequence<CardPrinting>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/gzip, application/x-ndjson, application/octet-stream")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw httpError(response.code, response.body?.string())
            }
            val body = response.body!!
            val stream = when (encoding.lowercase()) {
                "gzip", "x-gzip", "application/gzip" -> GZIPInputStream(body.byteStream())
                else -> body.byteStream()
            }
            sequence {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            val dto = json.decodeFromString(CatalogueProductDTO.serializer(), line)
                            DtoMapper.toDomain(dto)?.let { yield(it) }
                        }
                        line = reader.readLine()
                    }
                }
            }
        }
    }

    suspend fun upsertLocation(request: InventoryLocationUpsertRequest): Result<InventoryLocation> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = com.riftcompanion.app.data.api.dto.InventoryLocationUpsertDTO(
                name = request.name.trim(),
                color = request.color,
                icon = request.icon,
            )
            val body = json.encodeToString(com.riftcompanion.app.data.api.dto.InventoryLocationUpsertDTO.serializer(), dto)
                .toRequestBody("application/json".toMediaType())
            val httpRequest = authRequestBuilder(BASE_URL + "inventory/locations")
                .post(body)
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
            val result = DtoMapper.toDomain(json.decodeFromString(com.riftcompanion.app.data.api.dto.InventoryLocationDTO.serializer(), response.body!!.string()))
            response.close()
            result
        }
    }

    suspend fun updateLocation(request: InventoryLocationUpdateRequest): Result<InventoryLocation> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = com.riftcompanion.app.data.api.dto.InventoryLocationUpdateDTO(
                name = request.name.trim(),
                color = request.color,
                icon = request.icon,
            )
            val body = json.encodeToString(com.riftcompanion.app.data.api.dto.InventoryLocationUpdateDTO.serializer(), dto)
                .toRequestBody("application/json".toMediaType())
            val encodedName = java.net.URLEncoder.encode(request.currentName.trim(), "UTF-8")
            val httpRequest = authRequestBuilder(BASE_URL + "inventory/locations/$encodedName")
                .patch(body)
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
            val result = DtoMapper.toDomain(json.decodeFromString(com.riftcompanion.app.data.api.dto.InventoryLocationDTO.serializer(), response.body!!.string()))
            response.close()
            result
        }
    }

    suspend fun deleteLocation(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedName = java.net.URLEncoder.encode(name.trim(), "UTF-8")
            val body = "{}".toRequestBody("application/json".toMediaType())
            val httpRequest = authRequestBuilder(BASE_URL + "inventory/locations/$encodedName")
                .delete(body)
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful && response.code != 404) {
                throw httpError(response.code, response.body?.string())
            }
            response.close()
        }
    }

    suspend fun bulkUpdateInventory(request: InventoryBulkMoveRequest): Result<InventoryBulkMoveResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = DtoMapper.toBulkUpdateDTO(request)
            val body = json.encodeToString(com.riftcompanion.app.data.api.dto.InventoryBulkUpdateRequestDTO.serializer(), dto)
                .toRequestBody("application/json".toMediaType())
            val httpRequest = authRequestBuilder(BASE_URL + "inventory/bulk/update")
                .post(body)
                .header("Idempotency-Key", request.idempotencyKey)
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
            val result = DtoMapper.toDomain(json.decodeFromString(com.riftcompanion.app.data.api.dto.InventoryBulkUpdateResponseDTO.serializer(), response.body!!.string()))
            response.close()
            result
        }
    }

    suspend fun deleteInventoryLine(inventoryID: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedID = java.net.URLEncoder.encode(inventoryID.trim(), "UTF-8")
            val body = "{}".toRequestBody("application/json".toMediaType())
            val httpRequest = authRequestBuilder(BASE_URL + "inventory/$encodedID")
                .delete(body)
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful) throw httpError(response.code, response.body?.string())
            response.close()
        }
    }

    private fun httpError(code: Int, body: String?): Exception {
        val message = if (body != null) {
            try {
                val error = json.decodeFromString(com.riftcompanion.app.data.api.dto.CardNexusAPIErrorEnvelope.serializer(), body)
                "${error.code}: ${error.message}"
            } catch (_: Exception) {
                "HTTP $code"
            }
        } else {
            "HTTP $code"
        }
        return Exception(message)
    }
}

val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
