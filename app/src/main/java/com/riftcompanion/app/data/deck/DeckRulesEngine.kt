package com.riftcompanion.app.data.deck

import com.riftcompanion.app.domain.model.CardIdentityInfo
import com.riftcompanion.app.domain.model.ConstructedRuleset
import com.riftcompanion.app.domain.model.DeckValidationIssue
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.domain.model.ValidationSeverity

/**
 * Validates deck legality against the Constructed ruleset.
 *
 * Rules (from RiftBuilder's DeckRulesEngine):
 * 1. Exactly 1 Legend card
 * 2. Exactly 1 Champion card
 * 3. Exactly 40 main deck cards (main + chosenChampion combined)
 * 4. Exactly 12 runes
 * 5. Exactly 3 battlefields (each unique, quantity 1)
 * 6. Max 3 copies of any card by name (across main + sideboard)
 * 7. Max 10 sideboard cards
 * 8. Max 3 signature cards
 * 9. Domain identity: all cards must share domains with the Legend
 * 10. Champion tag: champion must share a tag with the Legend
 * 11. Signature restrictions: signature cards must match the champion
 * 12. Banned cards/battlefields not allowed
 * 13. Legal expansions only (if ruleset specifies)
 */
object DeckRulesEngine {

    fun validate(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo>,
        ruleset: ConstructedRuleset = ConstructedRuleset(),
    ): List<DeckValidationIssue> {
        val issues = mutableListOf<DeckValidationIssue>()

        // Invalid quantities
        val invalidEntries = entries.filter { it.quantity <= 0 }
        if (invalidEntries.isNotEmpty()) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "invalid_quantity",
                "Deck entries must have a positive quantity.",
                invalidEntries.map { it.nameSlug },
            ))
        }

        // Unknown cards
        val unknownSlugs = entries.map { it.nameSlug }.toSet() - identities.keys
        if (unknownSlugs.isNotEmpty()) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "unknown_cards",
                "Some deck entries do not resolve to a card identity.",
                unknownSlugs.toList(),
            ))
        }

        // Zone counts
        validateZoneCount(entries, setOf(DeckZone.main, DeckZone.chosenChampion), ruleset.mainDeckCount,
            "main_deck_count", "main deck cards including the chosen champion", issues)
        validateZoneCount(entries, setOf(DeckZone.legend), 1,
            "legend_count", "Champion Legend", issues)
        validateZoneCount(entries, setOf(DeckZone.chosenChampion), 1,
            "chosen_champion_count", "chosen champion", issues)
        validateZoneCount(entries, setOf(DeckZone.rune), ruleset.runeCount,
            "rune_count", "runes", issues)
        validateZoneCount(entries, setOf(DeckZone.battlefield), ruleset.battlefieldCount,
            "battlefield_count", "battlefields", issues)

        // Battlefield uniqueness
        val battlefieldEntries = entries.filter { it.zone == DeckZone.battlefield && it.quantity > 0 }
        val battlefieldNames = battlefieldEntries.map { it.nameSlug }.toSet()
        if (battlefieldNames.size != ruleset.battlefieldCount ||
            battlefieldEntries.any { it.quantity != 1 }) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "battlefield_uniqueness",
                "Battlefields must be ${ruleset.battlefieldCount} uniquely named single cards.",
                battlefieldEntries.map { it.nameSlug },
            ))
        }

        // Copy limit (main + chosenChampion + sideboard)
        val copyLimitZones = setOf(DeckZone.main, DeckZone.chosenChampion, DeckZone.sideboard)
        val quantitiesByName = entries
            .filter { it.zone in copyLimitZones }
            .groupBy { it.nameSlug }
            .mapValues { (_, group) -> group.sumOf { maxOf(0, it.quantity) } }
        for ((slug, quantity) in quantitiesByName) {
            if (quantity > ruleset.maximumCopiesByName) {
                val name = identities[slug]?.displayName ?: slug
                issues.add(DeckValidationIssue(
                    ValidationSeverity.error, "copy_limit",
                    "$name has $quantity copies; the limit is ${ruleset.maximumCopiesByName} across the main deck and sideboard.",
                    listOf(slug),
                ))
            }
        }

        // Sideboard count
        val sideboardCount = count(entries, setOf(DeckZone.sideboard))
        if (sideboardCount > ruleset.maximumSideboardCount) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "sideboard_count",
                "The sideboard has $sideboardCount cards; the maximum is ${ruleset.maximumSideboardCount}.",
                entries.filter { it.zone == DeckZone.sideboard }.map { it.nameSlug },
            ))
        }

        // Domain identity
        validateDomains(entries, identities, issues)

        // Champion tags
        validateChampionTags(entries, identities, issues)

        // Signature cards
        validateSignatures(entries, identities, ruleset, issues)

        // Banned cards
        validateBans(entries, identities, ruleset, issues)

        return issues
    }

    private fun validateZoneCount(
        entries: List<DeckEntryData>,
        zones: Set<DeckZone>,
        expected: Int,
        code: String,
        label: String,
        issues: MutableList<DeckValidationIssue>,
    ) {
        val actual = count(entries, zones)
        if (actual != expected) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, code,
                "The deck must contain exactly $expected $label; it currently contains $actual.",
                entries.filter { it.zone in zones }.map { it.nameSlug },
            ))
        }
    }

    private fun count(entries: List<DeckEntryData>, zones: Set<DeckZone>): Int =
        entries.filter { it.zone in zones }.sumOf { maxOf(0, it.quantity) }

    private fun validateDomains(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo>,
        issues: MutableList<DeckValidationIssue>,
    ) {
        val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend } ?: return
        val legend = identities[legendEntry.nameSlug] ?: return
        val allowed = legend.domains.map { normalize(it) }.toSet()
        if (allowed.isEmpty()) return

        val checkedZones = setOf(DeckZone.chosenChampion, DeckZone.main, DeckZone.rune, DeckZone.sideboard)
        for (entry in entries) {
            if (entry.zone !in checkedZones) continue
            val identity = identities[entry.nameSlug] ?: continue
            val cardDomains = identity.domains.map { normalize(it) }.toSet()
            if (cardDomains.isEmpty()) continue
            if (!cardDomains.all { it in allowed }) {
                issues.add(DeckValidationIssue(
                    ValidationSeverity.error, "domain_identity",
                    "${identity.displayName} contains a domain outside the Champion Legend's domain identity.",
                    listOf(identity.nameSlug),
                ))
            }
        }
    }

    private fun validateChampionTags(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo>,
        issues: MutableList<DeckValidationIssue>,
    ) {
        val legendEntry = entries.firstOrNull { it.zone == DeckZone.legend } ?: return
        val championEntry = entries.firstOrNull { it.zone == DeckZone.chosenChampion } ?: return
        val legend = identities[legendEntry.nameSlug] ?: return
        val champion = identities[championEntry.nameSlug] ?: return

        val legendTags = legend.tags.map { normalize(it) }.toSet()
        val championTags = champion.tags.map { normalize(it) }.toSet()
        if (legendTags.intersect(championTags).isEmpty()) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "champion_tag",
                "The chosen champion must share a champion tag with the Champion Legend.",
                listOf(legend.nameSlug, champion.nameSlug),
            ))
        }
    }

    private fun validateSignatures(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo>,
        ruleset: ConstructedRuleset,
        issues: MutableList<DeckValidationIssue>,
    ) {
        val relevantZones = setOf(DeckZone.main, DeckZone.chosenChampion, DeckZone.sideboard)
        val signatureEntries = entries.filter { entry ->
            entry.zone in relevantZones && identities[entry.nameSlug]?.let { isSignature(it) } == true
        }
        val signatureCount = signatureEntries.sumOf { maxOf(0, it.quantity) }
        if (signatureCount > ruleset.maximumSignatureCards) {
            issues.add(DeckValidationIssue(
                ValidationSeverity.error, "signature_limit",
                "The deck has $signatureCount signature cards; the maximum is ${ruleset.maximumSignatureCards}.",
                signatureEntries.map { it.nameSlug },
            ))
        }

        val championEntry = entries.firstOrNull { it.zone == DeckZone.chosenChampion } ?: return
        val champion = identities[championEntry.nameSlug] ?: return
        val championIdentifiers = setOf(champion.nameSlug, champion.displayName) + champion.tags.map { normalize(it) }

        for (entry in signatureEntries) {
            val identity = identities[entry.nameSlug] ?: continue
            val restrictions = signatureRestrictions(identity)
            if (restrictions.isEmpty()) continue
            if (restrictions.map { normalize(it) }.toSet().intersect(championIdentifiers).isEmpty()) {
                issues.add(DeckValidationIssue(
                    ValidationSeverity.error, "signature_restriction",
                    "${identity.displayName} is not a signature card for the chosen champion.",
                    listOf(identity.nameSlug, champion.nameSlug),
                ))
            }
        }
    }

    private fun validateBans(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo>,
        ruleset: ConstructedRuleset,
        issues: MutableList<DeckValidationIssue>,
    ) {
        val bannedCards = ruleset.bannedCards.map { normalize(it) }.toSet()
        val bannedBattlefields = ruleset.bannedBattlefields.map { normalize(it) }.toSet()

        for (entry in entries) {
            val identity = identities[entry.nameSlug] ?: continue
            val names = setOf(normalize(identity.nameSlug), normalize(identity.displayName))
            val isBanned = if (entry.zone == DeckZone.battlefield) {
                names.intersect(bannedBattlefields).isNotEmpty()
            } else {
                names.intersect(bannedCards).isNotEmpty()
            }
            if (isBanned) {
                issues.add(DeckValidationIssue(
                    ValidationSeverity.error,
                    if (entry.zone == DeckZone.battlefield) "banned_battlefield" else "banned_card",
                    "${identity.displayName} is banned by ${ruleset.name}.",
                    listOf(identity.nameSlug),
                ))
            }
        }
    }

    private fun isSignature(identity: CardIdentityInfo): Boolean {
        for (key in listOf("isSignature", "is_signature", "signature")) {
            val value = identity.attributes[key]
            when (value?.lowercase()) {
                "true", "yes", "signature" -> return true
            }
        }
        return normalize(identity.cardType ?: "") == "signature" ||
            normalize(identity.superType ?: "") == "signature"
    }

    private fun signatureRestrictions(identity: CardIdentityInfo): List<String> {
        return listOf("signatureFor", "signature_for", "signatureChampion", "signature_champion")
            .mapNotNull { identity.attributes[it] }
            .filter { it.isNotBlank() }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase()
}

/**
 * Simple deck entry data for validation (avoids Room entity dependency).
 */
data class DeckEntryData(
    val zone: DeckZone,
    val nameSlug: String,
    val quantity: Int,
)
