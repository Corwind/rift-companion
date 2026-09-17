package com.riftcompanion.app.domain.model

/**
 * Pure function that computes deck statistics from deck entries.
 *
 * Extracted so it can be unit-tested without Room/Hilt.
 */
object DeckStats {

    data class EntryInfo(
        val nameSlug: String,
        val displayName: String,
        val quantity: Int,
        val zone: DeckZone,
        val cardType: String?,
        val superType: String?,
        val domains: List<String>,
        val energyCost: Int?,
        val might: Int?,
        val rarity: String?,
    )

    data class Stats(
        val totalCards: Int,
        val mainDeckCount: Int,
        val sideboardCount: Int,
        val runeCount: Int,
        val battlefieldCount: Int,
        val cardTypeBreakdown: List<TypeCount>,
        val domainBreakdown: List<DomainCount>,
        val energyCurve: List<CostBucket>,
        val rarityBreakdown: List<RarityCount>,
        val averageMight: Double?,
        val uniqueCardCount: Int,
    )

    data class TypeCount(val cardType: String, val count: Int)
    data class DomainCount(val domain: String, val count: Int)
    data class CostBucket(val cost: Int, val count: Int)
    data class RarityCount(val rarity: String, val count: Int)

    fun compute(entries: List<EntryInfo>): Stats {
        // Only count main deck + sideboard for most stats (not legend, champion, runes, battlefields)
        val mainEntries = entries.filter { it.zone == DeckZone.main }
        val sideEntries = entries.filter { it.zone == DeckZone.sideboard }
        val runeEntries = entries.filter { it.zone == DeckZone.rune }
        val battlefieldEntries = entries.filter { it.zone == DeckZone.battlefield }
        val playableEntries = mainEntries + sideEntries

        val totalCards = entries.sumOf { it.quantity }
        val mainDeckCount = mainEntries.sumOf { it.quantity }
        val sideboardCount = sideEntries.sumOf { it.quantity }
        val runeCount = runeEntries.sumOf { it.quantity }
        val battlefieldCount = battlefieldEntries.sumOf { it.quantity }

        // Card type breakdown (main deck only, by cardType)
        val cardTypeBreakdown = mainEntries
            .filter { !it.cardType.isNullOrBlank() }
            .groupBy { it.cardType!! }
            .map { (type, list) -> TypeCount(type, list.sumOf { it.quantity }) }
            .sortedByDescending { it.count }

        // Domain breakdown (main deck only, exclude neutral)
        val domainBreakdown = mainEntries
            .flatMap { e -> e.domains.filterNot { d -> d.equals("neutral", ignoreCase = true) }.map { d -> d to e.quantity } }
            .groupBy { it.first }
            .map { (domain, pairs) -> DomainCount(domain, pairs.sumOf { it.second }) }
            .sortedByDescending { it.count }

        // Energy curve (main deck only, cards with energy cost)
        val energyCurve = mainEntries
            .filter { it.energyCost != null }
            .groupBy { it.energyCost!! }
            .map { (cost, list) -> CostBucket(cost, list.sumOf { it.quantity }) }
            .sortedBy { it.cost }

        // Rarity breakdown (main deck + sideboard)
        val rarityBreakdown = playableEntries
            .filter { !it.rarity.isNullOrBlank() }
            .groupBy { it.rarity!! }
            .map { (rarity, list) -> RarityCount(rarity, list.sumOf { it.quantity }) }
            .sortedByDescending { it.count }

        // Average might (units only — cards with might > 0)
        val mights = mainEntries.filter { (it.might ?: 0) > 0 }
        val averageMight = if (mights.isNotEmpty()) {
            mights.sumOf { it.might!! * it.quantity }.toDouble() / mights.sumOf { it.quantity }
        } else null

        val uniqueCardCount = playableEntries.map { it.nameSlug }.distinct().size

        return Stats(
            totalCards = totalCards,
            mainDeckCount = mainDeckCount,
            sideboardCount = sideboardCount,
            runeCount = runeCount,
            battlefieldCount = battlefieldCount,
            cardTypeBreakdown = cardTypeBreakdown,
            domainBreakdown = domainBreakdown,
            energyCurve = energyCurve,
            rarityBreakdown = rarityBreakdown,
            averageMight = averageMight,
            uniqueCardCount = uniqueCardCount,
        )
    }
}
