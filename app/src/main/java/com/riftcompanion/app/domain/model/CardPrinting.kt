package com.riftcompanion.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CardPrinting(
    val productID: Long,
    val nameSlug: String,
    val printingSlug: String,
    val displayName: String,
    val expansionID: Long? = null,
    val expansionSlug: String? = null,
    val printNumber: String? = null,
    val variant: String? = null,
    val rarity: String? = null,
    val finishes: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val imageURL: String? = null,
    val imageBackURL: String? = null,
    val attributes: Map<String, JsonValue> = emptyMap(),
) {
    val id: Long get() = productID
}
