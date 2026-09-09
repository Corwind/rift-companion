package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.CatalogueViewModel
import com.riftcompanion.app.ui.viewmodel.DeckBuildPreview
import com.riftcompanion.app.ui.viewmodel.DeckEntryDisplay
import com.riftcompanion.app.ui.viewmodel.DeckViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val detailState by viewModel.deckDetailState.collectAsStateWithLifecycle()
    val buildState by viewModel.buildState.collectAsStateWithLifecycle()

    LaunchedEffect(deckId) {
        viewModel.loadDeckDetail(deckId)
    }

    var showBuildPreview by remember { mutableStateOf(false) }
    var showDisassembleDialog by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DeckEntryDisplay?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground(),
    ) {
        // Header with back button, title, and view toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = detailState.deck?.name ?: "Deck",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { gridMode = !gridMode }) {
                Icon(
                    if (gridMode) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = if (gridMode) "List view" else "Grid view",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (detailState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val deck = detailState.deck
            if (deck != null) {
                LegalityBanner(deck.isLegal, deck.isBuilt, deck.legalityIssues)
            }

            // Build/Disassemble + Add card buttons
            if (deck != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!deck.isBuilt) {
                        Button(
                            onClick = {
                                viewModel.previewBuild(deckId)
                                showBuildPreview = true
                            },
                            enabled = deck.isLegal,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Build")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDisassembleDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Disassemble")
                        }
                    }
                    OutlinedButton(
                        onClick = { showAddCardSheet = true },
                        enabled = !deck.isBuilt,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Add Card")
                    }
                }
            }

            // Card entries grouped by zone
            val grouped = detailState.entries.groupBy { it.zone }

            if (gridMode) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DeckZone.entries.forEach { zone ->
                        val items = grouped[zone]
                        if (!items.isNullOrEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(currentLineSpan = 2) }) {
                                Text(
                                    text = zone.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(items.sortedBy { it.displayName }) { entry ->
                                DeckCardGrid(entry,
                                    onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                    onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeckZone.entries.forEach { zone ->
                        val items = grouped[zone]
                        if (!items.isNullOrEmpty()) {
                            item {
                                Text(
                                    text = zone.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(items.sortedBy { it.displayName }) { entry ->
                                DeckCardRow(
                                    entry,
                                    onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                    onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Build preview dialog
    if (showBuildPreview) {
        buildState.preview?.let { preview ->
            BuildPreviewDialog(
                preview = preview,
                isLoading = buildState.isLoading,
                onBuild = { viewModel.executeBuild() },
                onDismiss = {
                    showBuildPreview = false
                    viewModel.clearBuildState()
                },
            )
        }
    }

    if (buildState.isBuilt) {
        LaunchedEffect(Unit) {
            showBuildPreview = false
            viewModel.clearBuildState()
            viewModel.loadDeckDetail(deckId)
        }
    }

    if (showDisassembleDialog) {
        AlertDialog(
            onDismissRequest = { showDisassembleDialog = false },
            title = { Text("Disassemble deck") },
            text = { Text("Cards will be returned to their original storage locations. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disassembleDeck(deckId)
                    showDisassembleDialog = false
                }) { Text("Disassemble") }
            },
            dismissButton = {
                TextButton(onClick = { showDisassembleDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Add card from catalog
    if (showAddCardSheet) {
        AddCardSheet(
            deckId = deckId,
            onDismiss = { showAddCardSheet = false },
            onAddCard = { nameSlug, zone ->
                viewModel.addCardToDeck(deckId, nameSlug, zone)
            },
        )
    }
}

/**
 * List view card row with add/remove buttons.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeckCardRow(
    entry: DeckEntryDisplay,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    ThemedCardSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardArtwork(
                imageURL = entry.preferredImageURL,
                name = entry.displayName,
                modifier = Modifier.width(44.dp).height(62.dp),
                cornerRadius = 6,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(entry.cardType, entry.expansion, entry.rarity).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.domains.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.domains.take(3).forEach { DomainTag(domain = it) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.quantity} in deck", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    Text("${entry.availableInStorage} available", style = MaterialTheme.typography.labelSmall, color = if (entry.availableInStorage >= entry.quantity) com.riftcompanion.app.ui.theme.freeColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.inOtherDecks > 0) Text("${entry.inOtherDecks} in decks", style = MaterialTheme.typography.labelSmall, color = com.riftcompanion.app.ui.theme.usedColor())
                    if (entry.isMissing) Text("${entry.missingCount} missing", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                }
            }
            // Add/remove buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    IconButton(onClick = onRemove, enabled = entry.quantity > 0) {
                        Icon(Icons.Default.Remove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Text("${entry.quantity}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Grid view card with add/remove buttons.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeckCardGrid(
    entry: DeckEntryDisplay,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    ThemedCardSurface(
        modifier = Modifier,
        cornerRadius = 14,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(entry.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            CardArtwork(
                imageURL = entry.preferredImageURL,
                name = entry.displayName,
                modifier = Modifier.fillMaxWidth().aspectRatio(5f / 7f),
                cornerRadius = 11,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.domains.take(3).forEach { DomainTag(domain = it) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.quantity}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("in deck", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = entry.quantity > 0, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            if (entry.isMissing) {
                Text("${entry.missingCount} missing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LegalityBanner(isLegal: Boolean, isBuilt: Boolean, issues: List<String>) {
    val (color, icon, text) = when {
        isBuilt -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Build, "Built")
        isLegal -> Triple(com.riftcompanion.app.ui.theme.freeColor(), Icons.Default.CheckCircle, "Legal — ready to build")
        else -> Triple(MaterialTheme.colorScheme.error, Icons.Default.Error, "Not legal — ${issues.size} issue(s)")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
    if (!isLegal && !isBuilt && issues.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            issues.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (issues.size > 3) Text("… and ${issues.size - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BuildPreviewDialog(
    preview: DeckBuildPreview,
    isLoading: Boolean,
    onBuild: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Build \"${preview.deckName}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (preview.isNewLocation) "A new location \"${preview.deckLocationName}\" will be created."
                    else "Cards will be moved to \"${preview.deckLocationName}\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (preview.movements.isNotEmpty()) {
                    Text("Card movements:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    preview.movements.take(10).forEach { m ->
                        Text("• ${m.quantity}× ${m.displayName} ← ${m.fromLocation}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.movements.size > 10) Text("… and ${preview.movements.size - 10} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (preview.missing.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Missing cards:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    preview.missing.forEach { m ->
                        Text("• ${m.displayName}: need ${m.needed}, have ${m.available}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (isLoading) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            else { TextButton(onClick = onBuild, enabled = preview.missing.isEmpty()) { Text(if (preview.missing.isEmpty()) "Build" else "Can't build (missing cards)") } }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Bottom sheet to search the catalog and add cards to the deck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardSheet(
    deckId: String,
    onDismiss: () -> Unit,
    onAddCard: (nameSlug: String, zone: DeckZone) -> Unit,
) {
    val catalogueViewModel: CatalogueViewModel = hiltViewModel()
    val catalogueState by catalogueViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf(DeckZone.main) }

    LaunchedEffect(searchQuery) { catalogueViewModel.setSearchQuery(searchQuery) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Add Card to Deck", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))

            // Zone selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckZone.entries.forEach { zone ->
                    androidx.compose.material3.FilterChip(
                        selected = selectedZone == zone,
                        onClick = { selectedZone = zone },
                        label = { Text(zone.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search cards…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(catalogueState.cards, key = { it.id }) { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onAddCard(card.id, selectedZone)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardArtwork(
                            imageURL = card.preferredImageURL,
                            name = card.identity.displayName,
                            modifier = Modifier.width(36.dp).height(50.dp),
                            cornerRadius = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            card.identity.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
