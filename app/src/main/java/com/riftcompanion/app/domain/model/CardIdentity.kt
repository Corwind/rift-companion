package com.riftcompanion.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CardIdentity(
    val nameSlug: String,
    val gameID: String = "riftbound",
    val displayName: String,
    val cardType: String? = null,
    val superType: String? = null,
    val domains: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val energyCost: Int? = null,
    val mightCost: Int? = null,
    val attributes: Map<String, JsonValue> = emptyMap(),
) {
    val id: String get() = nameSlug

    val appIsRune: Boolean
        get() = cardType?.lowercase()?.contains("rune") == true ||
            tags.any { it.equals("rune", ignoreCase = true) }

    val appIsBattlefield: Boolean
        get() = cardType?.lowercase()?.contains("battlefield") == true

    val appVisibleDomains: List<String>
        get() = if (appIsBattlefield) emptyList()
        else domains.filter { !it.equals("neutral", ignoreCase = true) }

    val appSearchText: String
        get() = listOf(
            displayName, nameSlug, cardType ?: "", superType ?: "",
            domains.joinToString(" "), tags.joinToString(" "),
            attributes.values.joinToString(" ") { it.searchText },
        ).joinToString(" ")
}
