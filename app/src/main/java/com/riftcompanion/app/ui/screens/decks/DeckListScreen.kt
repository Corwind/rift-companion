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
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
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

    // Create empty deck dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New empty deck") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createEmptyDeck(name)
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            when {
                deck.isBuilt -> Icon(
                    Icons.Default.Build,
                    contentDescription = "Built",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                deck.isLegal -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Legal",
                    tint = com.riftcompanion.app.ui.theme.freeColor(),
                    modifier = Modifier.size(24.dp),
                )
                else -> Icon(
                    Icons.Default.Error,
                    contentDescription = "Not legal",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
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
