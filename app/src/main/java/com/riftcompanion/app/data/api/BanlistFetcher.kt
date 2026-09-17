package com.riftcompanion.app.data.api

import com.riftcompanion.app.domain.model.BanlistEntry
import com.riftcompanion.app.domain.model.BanlistEntryType
import com.riftcompanion.app.domain.model.ConstructedRuleset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the Riftbound banlist.
 *
 * The single source of truth for banned cards is ConstructedRuleset.
 * This class wraps it with metadata (effective dates, source URLs) for display.
 *
 * Sources:
 * - First bans (effective March 31, 2026):
 *   https://playriftbound.com/en-us/news/announcements/announcing-riftbounds-first-bans/
 * - September update (effective September 18, 2026):
 *   https://playriftbound.com/en-us/news/announcements/september-ban-list-updates-effective-september-18-2026/
 */
@Singleton
class BanlistFetcher @Inject constructor() {

    companion object {
        private const val FIRST_BANS_URL =
            "https://playriftbound.com/en-us/news/announcements/announcing-riftbounds-first-bans/"
        private const val SEPTEMBER_BANS_URL =
            "https://playriftbound.com/en-us/news/announcements/september-ban-list-updates-effective-september-18-2026/"

        private val firstBansCards = setOf(
            "Called Shot",
            "Draven - Vanquisher",
            "Fight or Flight",
            "Scrapheap",
        )
        private val septemberBansCards = setOf(
            "Ekko - Recurrent",
            "Stacked Deck",
        )
        private val firstBansBattlefields = setOf(
            "The Arena's Greatest",
            "Aspirant's Climb",
            "The Dreaming Tree",
            "Obelisk of Power",
            "Reaver's Row",
        )
    }

    private val ruleset = ConstructedRuleset()

    private val staticBans: List<BanlistEntry> by lazy {
        ruleset.bannedCards.map { name ->
            val url = if (name in septemberBansCards) SEPTEMBER_BANS_URL else FIRST_BANS_URL
            val date = if (name in septemberBansCards) "September 18, 2026" else "March 31, 2026"
            BanlistEntry(name, BanlistEntryType.CARD, effectiveDate = date, sourceUrl = url)
        } + ruleset.bannedBattlefields.map { name ->
            val url = if (name in firstBansBattlefields) FIRST_BANS_URL else SEPTEMBER_BANS_URL
            val date = if (name in firstBansBattlefields) "March 31, 2026" else "September 18, 2026"
            BanlistEntry(name, BanlistEntryType.BATTLEFIELD, effectiveDate = date, sourceUrl = url)
        }
    }

    suspend fun fetchBanlist(): Result<List<BanlistEntry>> = withContext(Dispatchers.IO) {
        Result.success(staticBans)
    }

    fun getStaticBanlist(): List<BanlistEntry> = staticBans
}
