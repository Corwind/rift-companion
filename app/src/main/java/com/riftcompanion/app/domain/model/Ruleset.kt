package com.riftcompanion.app.domain.model

/**
 * Constructed ruleset for Riftbound.
 * Values from riftbuilder/Sources/RiftBuilderCore/Resources/constructed-2026-07-16.json
 */
data class ConstructedRuleset(
    val id: String = "constructed-2026-07-16",
    val name: String = "Constructed",
    val mainDeckCount: Int = 40,
    val runeCount: Int = 12,
    val battlefieldCount: Int = 3,
    val maximumCopiesByName: Int = 3,
    val maximumSideboardCount: Int = 10,
    val maximumSignatureCards: Int = 3,
    val bannedCards: List<String> = listOf(
        "Called Shot",
        "Draven, Vanquisher",
        "Fight or Flight",
        "Scrapheap",
        "Stealthy Pursuer",
    ),
    val bannedBattlefields: List<String> = listOf(
        "The Arena's Greatest",
        "Aspirant's Climb",
        "Dreaming Tree",
        "Obelisk of Power",
        "Reaver's Row",
    ),
)

/**
 * A validation issue found when checking deck legality.
 */
data class DeckValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val message: String,
    val affectedNameSlugs: List<String> = emptyList(),
)

enum class ValidationSeverity {
    warning,
    error,
}

/**
 * Card identity info needed for validation.
 */
data class CardIdentityInfo(
    val nameSlug: String,
    val displayName: String,
    val domains: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val cardType: String? = null,
    val superType: String? = null,
    val expansionSlug: String? = null,
    val attributes: Map<String, String?> = emptyMap(),
)
