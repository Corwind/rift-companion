package com.riftcompanion.app.data.api

import com.riftcompanion.app.domain.model.BanlistEntry
import com.riftcompanion.app.domain.model.BanlistEntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and parses the Riftbound banlist from the official announcements page.
 * The banlist is an HTML page with banned cards and battlefields listed
 * under "Cards" and "Battlefields" headings.
 *
 * URL: https://playriftbound.com/en-us/news/announcements/announcing-riftbounds-first-bans/
 */
@Singleton
class BanlistFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        const val BANLIST_URL = "https://playriftbound.com/en-us/news/announcements/announcing-riftbounds-first-bans/"
    }

    suspend fun fetchBanlist(): Result<List<BanlistEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(BANLIST_URL)
                .header("Accept", "text/html")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch banlist: HTTP ${response.code}")
            }
            val html = response.body?.string() ?: throw Exception("Empty response body")
            response.close()
            parseBanlistHtml(html)
        }
    }

    /**
     * Parses the banlist HTML to extract banned cards and battlefields.
     * The page structure has sections like:
     *   **Cards**
     *   * Called Shot
     *   * Draven, Vanquisher
     *   **Battlefields**
     *   * Dreaming Tree
     */
    private fun parseBanlistHtml(html: String): List<BanlistEntry> {
        val entries = mutableListOf<BanlistEntry>()

        // Extract effective date
        val effectiveDate = extractEffectiveDate(html)

        // Find the "Cards" section and extract list items until the next section
        val bannedCards = extractListItemsAfterHeading(html, "Cards")
        entries.addAll(bannedCards.map { name ->
            BanlistEntry(
                cardName = name.trim(),
                cardType = BanlistEntryType.CARD,
                effectiveDate = effectiveDate,
                sourceUrl = BANLIST_URL,
            )
        })

        // Find the "Battlefields" section
        val bannedBattlefields = extractListItemsAfterHeading(html, "Battlefields")
        entries.addAll(bannedBattlefields.map { name ->
            BanlistEntry(
                cardName = name.trim(),
                cardType = BanlistEntryType.BATTLEFIELD,
                effectiveDate = effectiveDate,
                sourceUrl = BANLIST_URL,
            )
        })

        if (entries.isEmpty()) {
            throw Exception("No banned cards or battlefields found on the banlist page")
        }

        return entries
    }

    /**
     * Extracts the effective date from the page (e.g., "Effective March 31, 2026").
     */
    private fun extractEffectiveDate(html: String): String? {
        val dateRegex = Regex("""Effective[:\s]*([^<\n]+)""", RegexOption.IGNORE_CASE)
        return dateRegex.find(html)?.groupValues?.get(1)?.trim()
    }

    /**
     * Extracts list items (<li>...</li>) that appear after a heading containing [headingText].
     * Stops at the next heading or section boundary.
     */
    private fun extractListItemsAfterHeading(html: String, headingText: String): List<String> {
        // Find the position of the heading (could be <h2>, <h3>, <strong>, etc.)
        val headingRegex = Regex(
            """<(?:h[1-6]|strong)[^>]*>\s*\*?\s*${Regex.escape(headingText)}\s*\*?\s*</(?:h[1-6]|strong)>""",
            RegexOption.IGNORE_CASE,
        )
        val headingMatch = headingRegex.find(html) ?: return emptyList()

        // Extract content after the heading until the next heading or end of a list
        val afterHeading = html.substring(headingMatch.range.last + 1)
        val nextHeadingRegex = Regex("""<(?:h[1-6]|strong)""", RegexOption.IGNORE_CASE)
        val nextHeadingMatch = nextHeadingRegex.find(afterHeading)
        val sectionContent = if (nextHeadingMatch != null) {
            afterHeading.substring(0, nextHeadingMatch.range.first)
        } else {
            // Take a reasonable chunk to avoid grabbing the entire rest of the page
            afterHeading.take(2000)
        }

        // Extract <li> items from this section
        val liRegex = Regex("""<li[^>]*>\s*(.*?)\s*</li>""", RegexOption.DOT_MATCHES_ALL)
        return liRegex.findAll(sectionContent)
            .map { it.groupValues[1] }
            .map { it.replace(Regex("""<[^>]+>"""), "").trim() } // strip inner HTML tags
            .filter { it.isNotBlank() }
            .toList()
    }
}
