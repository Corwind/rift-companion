package com.riftcompanion.app.data.api

import android.content.Context
import android.util.Log
import com.riftcompanion.app.domain.model.RuleSection
import com.riftcompanion.app.domain.model.RulesData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the Riftbound Core Rules from bundled assets:
 * - rules.json: structured typed blocks for visual browsing with cross-references
 * - rules_context.md: full markdown text for AI context retrieval
 */
@Singleton
class RulesFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RulesFetcher"
        private const val JSON_ASSET = "rules.json"
        private const val MD_ASSET = "rules_context.md"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private var cachedData: RulesData? = null
    private var cachedMarkdown: String? = null

    suspend fun fetchRules(): Result<List<RuleSection>> = withContext(Dispatchers.IO) {
        runCatching {
            getData().sections.also {
                Log.i(TAG, "Loaded ${it.size} rule sections from bundled asset")
            }
        }
    }

    suspend fun getRulesData(): RulesData = withContext(Dispatchers.IO) {
        getData()
    }

    suspend fun getMarkdownContext(): String = withContext(Dispatchers.IO) {
        cachedMarkdown ?: context.assets.open(MD_ASSET).bufferedReader().use {
            it.readText()
        }.also { cachedMarkdown = it }
    }

    private fun getData(): RulesData {
        cachedData?.let { return it }
        val jsonString = context.assets.open(JSON_ASSET).bufferedReader().use { it.readText() }
        val data = json.decodeFromString<RulesData>(jsonString)
        cachedData = data
        return data
    }
}
