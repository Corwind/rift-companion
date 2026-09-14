package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.ai.AiBackend
import com.riftcompanion.app.data.ai.AiResult
import com.riftcompanion.app.data.ai.Availability
import com.riftcompanion.app.data.ai.RulesAiService
import com.riftcompanion.app.data.ai.RulesRag
import com.riftcompanion.app.data.api.RulesFetcher
import com.riftcompanion.app.data.prefs.SettingsDataStore
import com.riftcompanion.app.domain.model.RuleSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesSearchUiState(
    val availability: Availability? = null,
    val query: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
    val conversation: List<ChatMessage> = emptyList(),
    val rulesSections: List<RuleSection> = emptyList(),
    val keywords: Map<String, String> = emptyMap(),
    val sectionIndex: Map<String, String> = emptyMap(),
    val browseSearch: String = "",
    val browseSearchActive: Boolean = false,
    val viewMode: RulesViewMode = RulesViewMode.Chat,
    val expandedSections: Set<String> = emptySet(),
    val scrollTarget: String? = null,
)

enum class RulesViewMode(val title: String) { Chat("Chat"), Browse("Browse") }
data class ChatMessage(
    val role: String,  // "user" or "assistant"
    val text: String,
    val backend: AiBackend? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rulesAiService: RulesAiService,
    private val rulesFetcher: RulesFetcher,
    private val rulesRag: RulesRag,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RulesSearchUiState())
    val uiState: StateFlow<RulesSearchUiState> = _uiState.asStateFlow()

    init {
        checkAvailability()
        loadRules()
        initRag()

        viewModelScope.launch {
            settingsDataStore.settingsFlow.drop(1).collect {
                checkAvailability()
            }
        }
    }

    fun checkAvailability() {
        viewModelScope.launch {
            val availability = rulesAiService.checkAvailability()
            _uiState.value = _uiState.value.copy(availability = availability)
        }
    }

    private fun loadRules() {
        viewModelScope.launch {
            val data = rulesFetcher.getRulesData()
            _uiState.value = _uiState.value.copy(
                rulesSections = data.sections,
                keywords = data.keywords,
                sectionIndex = data.sectionIndex,
            )
        }
    }

    private fun initRag() {
        viewModelScope.launch {
            rulesRag.initialize()
        }
    }

    fun updateBrowseSearch(text: String) {
        _uiState.value = _uiState.value.copy(browseSearch = text, browseSearchActive = text.isNotBlank())
    }

    fun clearBrowseSearch() {
        _uiState.value = _uiState.value.copy(browseSearch = "", browseSearchActive = false)
    }

    fun setViewMode(mode: RulesViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun toggleSection(sectionId: String) {
        val current = _uiState.value.expandedSections
        _uiState.value = _uiState.value.copy(
            expandedSections = if (sectionId in current) current - sectionId else current + sectionId,
        )
    }

    fun expandAndScrollTo(sectionId: String) {
        _uiState.value = _uiState.value.copy(
            expandedSections = _uiState.value.expandedSections + sectionId,
            scrollTarget = sectionId,
        )
    }

    fun clearScrollTarget() {
        _uiState.value = _uiState.value.copy(scrollTarget = null)
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun ask() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                error = null,
                conversation = _uiState.value.conversation + ChatMessage("user", query),
                query = "",
            )

            // Use RAG for context retrieval (rules + cards)
            val context = rulesRag.retrieve(query)

            val systemInstruction = buildString {
                appendLine("You are a helpful assistant for the Riftbound trading card game.")
                appendLine("Answer questions about game rules clearly and concisely.")
                appendLine("Base your answer on the rules context and card texts provided. If the answer is not in the context, say you don't know.")
                appendLine("Cite the relevant rule numbers (e.g., rule 825, rule 340.1) from the context when known.")
                appendLine("Do not mention or apologize for not knowing a rule number — just provide it when you know it, and omit it when you don't.")
                appendLine("When referencing cards, mention their name and relevant keyword abilities.")
            }

            // Build conversation history for multi-turn context
            val history = _uiState.value.conversation.map { it.role to it.text }

            val result = rulesAiService.generate(systemInstruction, context, query, history)

            when (result) {
                is AiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        conversation = _uiState.value.conversation + ChatMessage("assistant", result.text, result.backend),
                    )
                }
                is AiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = result.message,
                    )
                }
                is AiResult.Unavailable -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = result.message,
                    )
                }
            }
        }
    }
}
