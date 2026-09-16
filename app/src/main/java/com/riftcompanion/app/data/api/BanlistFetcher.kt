package com.riftcompanion.app.data.api

import com.riftcompanion.app.domain.model.BanlistEntry
import com.riftcompanion.app.domain.model.BanlistEntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the Riftbound banlist as a static list.
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
    }

    private val staticBans = listOf(
        // First bans — effective March 31, 2026
        BanlistEntry("Called Shot", BanlistEntryType.CARD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Draven, Vanquisher", BanlistEntryType.CARD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Fight or Flight", BanlistEntryType.CARD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Scrapheap", BanlistEntryType.CARD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Dreaming Tree", BanlistEntryType.BATTLEFIELD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Obelisk of Power", BanlistEntryType.BATTLEFIELD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),
        BanlistEntry("Reaver's Row", BanlistEntryType.BATTLEFIELD, effectiveDate = "March 31, 2026", sourceUrl = FIRST_BANS_URL),

        // September bans — effective September 18, 2026
        BanlistEntry("Ekko, Recurrent", BanlistEntryType.CARD, effectiveDate = "September 18, 2026", sourceUrl = SEPTEMBER_BANS_URL),
        BanlistEntry("Stacked Deck", BanlistEntryType.CARD, effectiveDate = "September 18, 2026", sourceUrl = SEPTEMBER_BANS_URL),
    )

    suspend fun fetchBanlist(): Result<List<BanlistEntry>> = withContext(Dispatchers.IO) {
        Result.success(staticBans)
    }
}
