package com.riftcompanion.app.data.deck

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses RiftDeck JSON export format.
 *
 * JSON structure:
 * {
 *   "formatVersion": 1,
 *   "exportedAt": "2024-01-01T00:00:00Z",
 *   "deck": { "name": "...", "state": "...", "rulesetID": "..." },
 *   "entries": [{ "zone": "main", "nameSlug": "...", "quantity": 3, ... }]
 * }
 */
object RiftDeckParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Result<ParsedRiftDeck> = runCatching {
        val doc = json.decodeFromString<RiftDeckDocument>(text)
        if (doc.formatVersion != 1) {
            throw IllegalArgumentException("Unsupported format version: ${doc.formatVersion}")
        }
        if (doc.deck.name.isBlank()) {
            throw IllegalArgumentException("Deck name is empty")
        }
        if (doc.entries.isEmpty()) {
            throw IllegalArgumentException("No entries in deck")
        }
        ParsedRiftDeck(
            name = doc.deck.name,
            state = doc.deck.state,
            rulesetId = doc.deck.rulesetID,
            entries = doc.entries.map {
                ParsedRiftDeckEntry(
                    zone = it.zone,
                    nameSlug = it.nameSlug,
                    quantity = it.quantity,
                )
            },
        )
    }
}

@Serializable
private data class RiftDeckDocument(
    @SerialName("formatVersion") val formatVersion: Int,
    @SerialName("exportedAt") val exportedAt: String? = null,
    @SerialName("deck") val deck: RiftDeckDefinition,
    @SerialName("entries") val entries: List<RiftDeckEntry>,
)

@Serializable
private data class RiftDeckDefinition(
    @SerialName("name") val name: String,
    @SerialName("state") val state: String,
    @SerialName("rulesetID") val rulesetID: String,
)

@Serializable
private data class RiftDeckEntry(
    @SerialName("zone") val zone: String,
    @SerialName("nameSlug") val nameSlug: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("preferredProductID") val preferredProductID: Long? = null,
    @SerialName("preferredFinish") val preferredFinish: String? = null,
    @SerialName("preferredLanguage") val preferredLanguage: String? = null,
)

data class ParsedRiftDeck(
    val name: String,
    val state: String,
    val rulesetId: String,
    val entries: List<ParsedRiftDeckEntry>,
)

data class ParsedRiftDeckEntry(
    val zone: String,
    val nameSlug: String,
    val quantity: Int,
)
