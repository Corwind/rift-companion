package com.riftcompanion.app.domain.model

/**
 * A card or battlefield banned in a specific format.
 */
data class BanlistEntry(
    val cardName: String,
    val cardType: BanlistEntryType,
    val format: String = "constructed-1v1",
    val effectiveDate: String? = null,
    val sourceUrl: String? = null,
)

enum class BanlistEntryType {
    CARD,
    BATTLEFIELD,
}
