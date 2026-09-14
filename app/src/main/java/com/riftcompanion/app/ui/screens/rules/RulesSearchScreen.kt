package com.riftcompanion.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.data.ai.AiBackend
import com.riftcompanion.app.data.ai.Availability
import com.riftcompanion.app.domain.model.RuleSection
import com.riftcompanion.app.ui.components.RuleBlocks
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.parseMarkdownToBlocks
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.ChatMessage
import com.riftcompanion.app.ui.viewmodel.RulesViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesSearchScreen(
    onBack: () -> Unit,
    viewModel: com.riftcompanion.app.ui.viewmodel.RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.conversation.size) {
        if (uiState.conversation.isNotEmpty()) {
            listState.animateScrollToItem(uiState.conversation.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground(),
    ) {
        // Header with back button and mode toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "Rules",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Single toggle: shows the icon of the mode you'll switch TO
            IconButton(onClick = {
                viewModel.setViewMode(
                    if (uiState.viewMode == RulesViewMode.Chat) RulesViewMode.Browse else RulesViewMode.Chat
                )
            }) {
                if (uiState.viewMode == RulesViewMode.Chat) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Switch to browse", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Switch to AI chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when (uiState.viewMode) {
            RulesViewMode.Chat -> ChatView(uiState, viewModel, listState)
            RulesViewMode.Browse -> BrowseView(uiState, viewModel)
        }
    }
}

@Composable
private fun ChatView(
    uiState: com.riftcompanion.app.ui.viewmodel.RulesSearchUiState,
    viewModel: com.riftcompanion.app.ui.viewmodel.RulesViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val availability = uiState.availability

    if (availability != null && !availability.any) {
        // No AI backend available
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Rules search is not available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Set a Gemini API key in Settings to enable cloud AI, or use a device with on-device AI support.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.conversation) { message ->
                    ChatBubble(message)
                }

                if (uiState.isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                uiState.error?.let { err ->
                    item {
                        Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            // Input bar — always visible when chat is available
            // When keyboard is hidden: sit above the floating pill nav
            // When keyboard is shown: sit right above the keyboard
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (imeVisible) 8.dp else 88.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    placeholder = { Text("Ask about the rules…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !uiState.isGenerating,
                    maxLines = 3,
                )
                IconButton(onClick = { viewModel.ask() }, enabled = uiState.query.isNotBlank() && !uiState.isGenerating) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BrowseView(
    uiState: com.riftcompanion.app.ui.viewmodel.RulesSearchUiState,
    viewModel: com.riftcompanion.app.ui.viewmodel.RulesViewModel,
) {
    if (uiState.rulesSections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No rules available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = uiState.browseSearch,
                onValueChange = { viewModel.updateBrowseSearch(it) },
                placeholder = { Text("Search rules…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (uiState.browseSearchActive) {
                        IconButton(onClick = { viewModel.clearBrowseSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
            )

            if (uiState.browseSearchActive) {
                // Search results mode: show only matching blocks
                BrowseSearchResults(uiState, viewModel)
            } else {
                // Normal browse mode: major sections → sub-sections
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.rulesSections.forEach { section ->
                        item(key = section.id) {
                            MajorSectionHeader(
                                title = section.title,
                                isExpanded = section.id in uiState.expandedSections,
                                onToggle = { viewModel.toggleSection(section.id) },
                            )
                        }
                        if (section.id in uiState.expandedSections) {
                            items(section.subSections) { subSection ->
                                SubSectionCard(
                                    subSection = subSection,
                                    isExpanded = subSection.id in uiState.expandedSections,
                                    onToggle = { viewModel.toggleSection(subSection.id) },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BrowseSearchResults(
    uiState: com.riftcompanion.app.ui.viewmodel.RulesSearchUiState,
    viewModel: com.riftcompanion.app.ui.viewmodel.RulesViewModel,
) {
    val query = uiState.browseSearch.trim()
    val queryLower = query.lowercase()

    // Collect all matching sub-sections with their parent section title
    data class SearchResult(
        val sectionTitle: String,
        val subSection: com.riftcompanion.app.domain.model.RuleSubSection,
        val matchingBlocks: List<com.riftcompanion.app.domain.model.RuleBlock>,
    )

    val results = mutableListOf<SearchResult>()
    for (section in uiState.rulesSections) {
        for (sub in section.subSections) {
            // Check if sub-section title matches
            val titleMatches = sub.title.lowercase().contains(queryLower)
            // Filter blocks that contain the query
            val matchingBlocks = sub.blocks.filter { block ->
                fun blockText(b: com.riftcompanion.app.domain.model.RuleBlock): String = when (b) {
                    is com.riftcompanion.app.domain.model.RuleBlock.Paragraph -> b.segments.joinToString("") { it.text }
                    is com.riftcompanion.app.domain.model.RuleBlock.Heading -> b.text
                    is com.riftcompanion.app.domain.model.RuleBlock.Bullets -> b.items.flatten().joinToString("") { it.text }
                    is com.riftcompanion.app.domain.model.RuleBlock.Numbered -> b.items.flatten().joinToString("") { it.text }
                    is com.riftcompanion.app.domain.model.RuleBlock.Table -> b.headers.joinToString(" ") + " " + b.rows.flatten().joinToString(" ")
                }
                blockText(block).lowercase().contains(queryLower)
            }
            if (titleMatches || matchingBlocks.isNotEmpty()) {
                results.add(SearchResult(section.title, sub, if (titleMatches) sub.blocks else matchingBlocks))
            }
        }
    }

    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No results for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "${results.size} result${if (results.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(results) { result ->
                ThemedCardSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 10,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = result.subSection.title,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = result.sectionTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        RuleBlocks(
                            blocks = result.matchingBlocks,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MajorSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .then(if (isExpanded) Modifier.rotate(180f) else Modifier),
        )
    }
}

@Composable
private fun SubSectionCard(
    subSection: com.riftcompanion.app.domain.model.RuleSubSection,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    ThemedCardSurface(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        cornerRadius = 10,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subSection.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isExpanded) Modifier.rotate(180f) else Modifier),
                )
            }

            if (isExpanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    RuleBlocks(
                        blocks = subSection.blocks,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isUser) 48.dp else 0.dp),
    ) {
        ThemedCardSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 12,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    // Render assistant responses as markdown
                    RuleBlocks(
                        blocks = parseMarkdownToBlocks(message.text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isUser && message.backend != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (message.backend) {
                                AiBackend.OnDevice -> Icons.Default.PhoneAndroid
                                AiBackend.Cloud -> Icons.Default.Cloud
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = when (message.backend) {
                                AiBackend.OnDevice -> "On-device"
                                AiBackend.Cloud -> "Cloud"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
