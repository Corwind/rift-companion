package com.riftcompanion.app.data.ai

import android.util.Log
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.api.RulesFetcher
import com.riftcompanion.app.domain.model.RuleSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Local Retrieval-Augmented Generation index.
 *
 * Builds a searchable corpus from:
 * - Rules sections (bundled JSON + markdown)
 * - Card catalog (from Room DB)
 *
 * Uses BM25 scoring for retrieval — no ML model needed, just text similarity.
 * The retrieved context is sent to the cloud LLM for generation.
 */
@Singleton
class RulesRag @Inject constructor(
    private val rulesFetcher: RulesFetcher,
    private val cardIdentityDao: CardIdentityDao,
) {
    companion object {
        private const val TAG = "RulesRag"
        private const val MAX_RESULTS = 8
        private const val MAX_CARD_RESULTS = 12
    }

    data class Chunk(
        val source: String,    // "rules" or "cards"
        val title: String,
        val content: String,
        val score: Float = 0f,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private var chunks: List<Chunk> = emptyList()
    private var avgDocLength: Double = 0.0
    private var docFreq: MutableMap<String, Int> = mutableMapOf()
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            val allChunks = mutableListOf<Chunk>()

            // Index rules sub-sections
            val rulesData = rulesFetcher.getRulesData()
            for (section in rulesData.sections) {
                for (subSection in section.subSections) {
                    val text = buildSectionText(subSection.blocks)
                    if (text.isNotBlank()) {
                        allChunks.add(Chunk("rules", subSection.title, text))
                    }
                }
            }

            // Index card catalog — include ALL cards, not just those with rulesText
            val cards = cardIdentityDao.getAllCards()
            Log.i(TAG, "Indexing ${cards.size} cards")
            for (card in cards) {
                val rulesText = extractRulesText(card.attributesJson)
                val tags = extractTags(card.tagsCsv)
                val domains = card.domainsCsv.split(",").filter { it.isNotBlank() }
                val domainStr = if (domains.isNotEmpty()) domains.joinToString("/") else "Universal"
                // Include name, type, domains, tags, and rules text for best matching
                val content = buildString {
                    append(card.displayName)
                    append(" (")
                    append(card.cardType ?: "Unknown")
                    append(", ")
                    append(domainStr)
                    if (tags.isNotEmpty()) {
                        append(", ")
                        append(tags.joinToString(", "))
                    }
                    append("): ")
                    append(rulesText)
                }
                allChunks.add(Chunk("cards", card.displayName, content))
            }

            chunks = allChunks
            avgDocLength = allChunks.map { it.content.split(Regex("\\s+")).size }.average().coerceAtLeast(1.0)

            // Build document frequency map for BM25
            docFreq.clear()
            for (chunk in allChunks) {
                val terms = tokenize(chunk.content)
                for (term in terms.toSet()) {
                    docFreq[term] = (docFreq[term] ?: 0) + 1
                }
            }

            initialized = true
            Log.i(TAG, "RAG index built: ${allChunks.size} chunks (${rulesData.sections.size} rules + ${cards.size} cards)")
        }
    }

    /**
     * Retrieves the most relevant chunks for a query using BM25 scoring.
     * Returns rules chunks first, then card chunks, each sorted by relevance.
     */
    suspend fun retrieve(query: String): String {
        if (!initialized) initialize()
        if (chunks.isEmpty()) {
            Log.w(TAG, "No chunks in RAG index")
            return ""
        }

        val queryTerms = tokenize(query)
        Log.d(TAG, "Retrieving for query: '$query' — terms: $queryTerms")
        if (queryTerms.isEmpty()) return ""

        // Score all chunks
        val queryLower = query.lowercase().trim()
        val scored = chunks.map { chunk ->
            val docTerms = tokenize(chunk.content)
            val docLength = docTerms.size
            val termFreqs = docTerms.groupingBy { it }.eachCount()

            var score = 0.0
            for (term in queryTerms) {
                val tf = termFreqs[term] ?: 0
                if (tf == 0) continue
                val df = docFreq[term] ?: 0
                val idf = ln((chunks.size - df + 0.5) / (df + 0.5) + 1.0)
                val k1 = 1.5
                val b = 0.75
                val tfNorm = (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * docLength / avgDocLength))
                score += idf * tfNorm
            }

            // Boost: if the query contains the chunk title (card name), boost significantly
            if (chunk.source == "cards" && queryLower.contains(chunk.title.lowercase())) {
                score += 10.0
            }

            chunk.copy(score = score.toFloat())
        }.filter { it.score > 0 }

        // Split by source and take top results from each
        // Limit rules context to ~12000 chars to leave room for the response
        val rulesResults = mutableListOf<Chunk>()
        var rulesCharCount = 0
        for (chunk in scored.filter { it.source == "rules" }.sortedByDescending { it.score }) {
            if (rulesCharCount + chunk.content.length > 12000) break
            rulesResults.add(chunk)
            rulesCharCount += chunk.content.length
        }

        val cardResults = scored.filter { it.source == "cards" }
            .sortedByDescending { it.score }
            .take(MAX_CARD_RESULTS)

        Log.d(TAG, "Retrieved: ${rulesResults.size} rules chunks, ${cardResults.size} card chunks")
        if (rulesResults.isNotEmpty()) Log.d(TAG, "Top rule: ${rulesResults.first().title} (score=${rulesResults.first().score})")
        if (cardResults.isNotEmpty()) Log.d(TAG, "Top card: ${cardResults.first().title} (score=${cardResults.first().score})")

        // Build context string
        return buildString {
            if (rulesResults.isNotEmpty()) {
                appendLine("## Rules context")
                for (chunk in rulesResults) {
                    appendLine("### ${chunk.title}")
                    appendLine(chunk.content)
                    appendLine()
                }
            }
            if (cardResults.isNotEmpty()) {
                appendLine("## Relevant cards")
                for (chunk in cardResults) {
                    appendLine("- ${chunk.content}")
                }
            }
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }  // Allow 2+ char terms for short card names
    }

    private fun buildSectionText(blocks: List<com.riftcompanion.app.domain.model.RuleBlock>): String {
        return buildString {
            for (block in blocks) {
                when (block) {
                    is com.riftcompanion.app.domain.model.RuleBlock.Paragraph ->
                        block.segments.forEach { append(it.text).append(' ') }
                    is com.riftcompanion.app.domain.model.RuleBlock.Heading ->
                        append(block.text).append(' ')
                    is com.riftcompanion.app.domain.model.RuleBlock.Bullets ->
                        block.items.forEach { item -> item.forEach { append(it.text).append(' ') } }
                    is com.riftcompanion.app.domain.model.RuleBlock.Numbered ->
                        block.items.forEach { item -> item.forEach { append(it.text).append(' ') } }
                    is com.riftcompanion.app.domain.model.RuleBlock.Table -> {
                        block.headers.forEach { append(it).append(' ') }
                        block.rows.forEach { row -> row.forEach { append(it).append(' ') } }
                    }
                }
            }
        }.trim()
    }

    private fun extractTags(tagsCsv: String): List<String> {
        return tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractRulesText(attributesJson: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(attributesJson) as? JsonObject
            obj?.get("rulesText")?.jsonPrimitive?.content ?: ""
        }.getOrElse { "" }
    }
}
