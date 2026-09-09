package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.DeckSummary
import com.riftcompanion.app.ui.viewmodel.DeckViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onDeckClick: (String) -> Unit,
    onImportClick: () -> Unit,
    onCreateFromLocation: () -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val deckListState by viewModel.deckListState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadDecks()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .statusBarsPadding()
                .fillMaxSize()
                .gradientBackground(),
        ) {
            // Header row with title and add button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Decks",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add deck", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import from text") },
                            onClick = { showMenu = false; onImportClick() },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Create from location") },
                            onClick = { showMenu = false; onCreateFromLocation() },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Create empty deck") },
                            onClick = { showMenu = false; showCreateDialog = true },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                    }
                }
            }

            if (deckListState.decks.isEmpty() && !deckListState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No decks yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap + to create or import a deck", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(deckListState.decks, key = { it.id }) { deck ->
                        DeckRow(
                            deck = deck,
                            onClick = { onDeckClick(deck.id) },
                            onDelete = { deckToDelete = deck },
                        )
                    }
                }
            }
        }
    }

    // Create empty deck — legend picker funnel
    if (showCreateDialog) {
        CreateDeckFunnel(
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
            onCreated = { showCreateDialog = false },
        )
    }

    // Delete confirmation
    deckToDelete?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckToDelete = null },
            title = { Text("Delete deck") },
            text = { Text("Delete \"${deck.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDeck(deck.id)
                    deckToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deckToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DeckRow(deck: DeckSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        cornerRadius = 14,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Legend artwork
            if (deck.legendImageURL != null) {
                com.riftcompanion.app.ui.components.CardArtwork(
                    imageURL = deck.legendImageURL,
                    name = deck.legendDisplayName ?: "",
                    modifier = Modifier.width(44.dp).height(62.dp),
                    cornerRadius = 6,
                )
            } else {
                Icon(
                    Icons.Default.Style,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    deck.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${deck.cardCount} cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (deck.isBuilt) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "• Built",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!deck.isLegal && !deck.isBuilt) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "• ${deck.legalityIssues.size} issues",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Multi-step funnel for creating a new empty deck:
 * Step 1: Pick a Legend card (required)
 * Step 2: Name the deck and create
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateDeckFunnel(
    viewModel: DeckViewModel,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    var legends by remember { mutableStateOf<List<com.riftcompanion.app.domain.model.CatalogueCardSummary>>(emptyList()) }
    var selectedLegend by remember { mutableStateOf<com.riftcompanion.app.domain.model.CatalogueCardSummary?>(null) }
    var deckName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.getLegendCards { legends = it }
    }

    // Auto-suggest deck name from legend
    LaunchedEffect(selectedLegend) {
        if (deckName.isBlank() && selectedLegend != null) {
            deckName = "${selectedLegend!!.identity.displayName.split(",").firstOrNull()?.trim() ?: "New"} Deck"
        }
    }

    // Handle creation success
    LaunchedEffect(importState.success) {
        if (importState.success != null) {
            viewModel.clearImportState()
            onCreated()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedLegend == null) "Select a Legend" else "Name Your Deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedLegend == null) {
                    // Step 1: Legend picker
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search legends…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val filtered = if (searchQuery.isBlank()) legends
                        else legends.filter { it.identity.displayName.contains(searchQuery, ignoreCase = true) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(300.dp),
                    ) {
                        items(filtered, key = { it.id }) { legend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedLegend = legend }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.riftcompanion.app.ui.components.CardArtwork(
                                    imageURL = legend.preferredImageURL,
                                    name = legend.identity.displayName,
                                    modifier = Modifier.width(36.dp).height(50.dp),
                                    cornerRadius = 4,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(legend.identity.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val domains = legend.identity.appVisibleDomains
                                    if (domains.isNotEmpty()) {
                                        Text(domains.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Step 2: Name the deck
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.riftcompanion.app.ui.components.CardArtwork(
                            imageURL = selectedLegend!!.preferredImageURL,
                            name = selectedLegend!!.identity.displayName,
                            modifier = Modifier.width(44.dp).height(62.dp),
                            cornerRadius = 6,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(selectedLegend!!.identity.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Legend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { deckName = it },
                        label = { Text("Deck name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { selectedLegend = null }) {
                        Text("Change Legend")
                    }
                }
                importState.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (selectedLegend != null) {
                TextButton(
                    onClick = {
                        viewModel.createEmptyDeck(deckName, selectedLegend!!.id)
                    },
                    enabled = !importState.isImporting,
                ) {
                    if (importState.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Creating…")
                    } else {
                        Text("Create Deck")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
