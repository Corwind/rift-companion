package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    onCardClick: (String) -> Unit = {},
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

    // Drag & drop state: which card is being dragged
    var draggedCard by remember { mutableStateOf<DeckEntryDisplay?>(null) }

    // Track which zones have entries (for auto-selecting missing zones in AddCardSheet)
    val zonesWithCards = detailState.entries.map { it.zone }.toSet()

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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Legend + Champion shown side by side
                    val legend = grouped[DeckZone.legend]?.firstOrNull()
                    val champion = grouped[DeckZone.chosenChampion]?.firstOrNull()
                    if (legend != null || champion != null) {
                        item {
                            Text(
                                "Legend & Champion",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (legend != null) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DeckCardGrid(legend,
                                            onAdd = { viewModel.addCardToDeck(deckId, legend.nameSlug, legend.zone) },
                                            onRemove = { viewModel.removeCardFromDeck(deckId, legend.nameSlug, legend.zone) },
                                            onClick = { onCardClick(legend.nameSlug) },
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (champion != null) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DeckCardGrid(champion,
                                            onAdd = { viewModel.addCardToDeck(deckId, champion.nameSlug, champion.zone) },
                                            onRemove = { viewModel.removeCardFromDeck(deckId, champion.nameSlug, champion.zone) },
                                            onClick = { onCardClick(champion.nameSlug) },
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Remaining zones (skip legend and champion, already shown above)
                    DeckZone.entries.filter { it != DeckZone.legend && it != DeckZone.chosenChampion }.forEach { zone ->
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
                            items.sortedBy { it.displayName }.chunked(2).forEach { rowItems ->
                                item {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        rowItems.forEach { entry ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                DeckCardGrid(entry,
                                                    onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                                    onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                                    onClick = { onCardClick(entry.nameSlug) },
                                                )
                                            }
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeckZone.entries.forEach { zone ->
                        val items = grouped[zone]
                        if (!items.isNullOrEmpty()) {
                            item {
                                // Zone header as a drop target
                                DropZoneHeader(
                                    zone = zone,
                                    draggedCard = draggedCard,
                                    onDrop = { card ->
                                        if (card.zone != zone) {
                                            viewModel.moveCardToZone(deckId, card.nameSlug, card.zone, zone, 1)
                                        }
                                        draggedCard = null
                                    },
                                )
                            }
                            listItems(items.sortedBy { it.displayName }) { entry ->
                                DeckCardRow(
                                    entry,
                                    onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                    onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                    onClick = { onCardClick(entry.nameSlug) },
                                    onDragStart = { draggedCard = entry },
                                    onDragEnd = { draggedCard = null },
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
            zonesWithCards = zonesWithCards,
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
    onClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    ThemedCardSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12,
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .clickable(onClick = onClick)
                .pointerInput(entry.nameSlug) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    ) { _, _ -> }
                },
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
 * Zone header that acts as a drop target for drag & drop.
 * Highlights when a card is being dragged over it.
 */
@Composable
private fun DropZoneHeader(
    zone: DeckZone,
    draggedCard: DeckEntryDisplay?,
    onDrop: (DeckEntryDisplay) -> Unit,
) {
    val isHighlighted = draggedCard != null && draggedCard.zone != zone
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .pointerInput(zone, draggedCard?.nameSlug) {
                // When a card is being dragged and the pointer is released over
                // this header, move the card to this zone.
                if (draggedCard != null && draggedCard.zone != zone) {
                    awaitEachGesture {
                        awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            val released = event.changes.all { !it.pressed }
                            if (released) {
                                onDrop(draggedCard)
                                break
                            }
                        } while (true)
                    }
                }
            },
    ) {
        Text(
            text = if (isHighlighted) "Drop here → ${zone.displayName}" else zone.displayName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
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
    onClick: () -> Unit,
) {
    ThemedCardSurface(
        modifier = Modifier,
        cornerRadius = 14,
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .clickable(onClick = onClick),
        ) {
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
 * Zone selector is a dropdown that auto-selects the first missing zone
 * in priority order: legend -> champion -> main -> runes -> battlefields -> sideboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardSheet(
    deckId: String,
    zonesWithCards: Set<DeckZone>,
    onDismiss: () -> Unit,
    onAddCard: (nameSlug: String, zone: DeckZone) -> Unit,
    deckViewModel: DeckViewModel = hiltViewModel(),
) {
    val catalogueViewModel: CatalogueViewModel = hiltViewModel()
    val catalogueState by catalogueViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var legendDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var legendTags by remember { mutableStateOf<List<String>>(emptyList()) }

    // Auto-select first missing zone in priority order (skip legend if already has one)
    val zonePriority = if (DeckZone.legend in zonesWithCards) {
        listOf(DeckZone.chosenChampion, DeckZone.main, DeckZone.rune, DeckZone.battlefield, DeckZone.sideboard)
    } else {
        listOf(DeckZone.legend, DeckZone.chosenChampion, DeckZone.main, DeckZone.rune, DeckZone.battlefield, DeckZone.sideboard)
    }
    var selectedZone by remember {
        mutableStateOf(zonePriority.firstOrNull { it !in zonesWithCards } ?: DeckZone.main)
    }
    var zoneDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        deckViewModel.getLegendDomains(deckId) { legendDomains = it }
        deckViewModel.getLegendTags(deckId) { legendTags = it }
    }

    LaunchedEffect(searchQuery) { catalogueViewModel.setSearchQuery(searchQuery) }

    // Filter cards by zone type
    val filteredCards = catalogueState.cards.filter { card ->
        val cardType = card.identity.cardType?.lowercase() ?: ""
        val cardTags = card.identity.tags.map { it.lowercase().trim() }
        when (selectedZone) {
            DeckZone.legend -> cardType.contains("legend")
            DeckZone.chosenChampion -> {
                // Champions/units that share a tag with the legend
                (cardType.contains("champion") || cardType.contains("unit")) &&
                    (legendTags.isEmpty() || cardTags.any { it in legendTags })
            }
            DeckZone.rune -> {
                // Runes that match the legend's domains
                if (!cardType.contains("rune")) {
                    false
                } else if (legendDomains.isEmpty()) {
                    true
                } else {
                    val cardDomains = card.identity.appVisibleDomains
                    cardDomains.isEmpty() || cardDomains.any { it in legendDomains }
                }
            }
            DeckZone.battlefield -> cardType.contains("battlefield")
            DeckZone.main, DeckZone.sideboard -> {
                // Exclude legends, runes, battlefields
                if (cardType.contains("legend") || cardType.contains("rune") || cardType.contains("battlefield")) {
                    false
                } else if (legendDomains.isEmpty()) {
                    true // No legend set yet, allow all
                } else {
                    // Must match legend's domains
                    val cardDomains = card.identity.appVisibleDomains
                    cardDomains.isEmpty() || cardDomains.any { it in legendDomains }
                }
            }
            else -> true
        }
    }

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
            Spacer(Modifier.height(12.dp))

            // Zone dropdown selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zone: ", style = MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(onClick = { zoneDropdownExpanded = true }) {
                        Text(selectedZone.displayName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = zoneDropdownExpanded,
                        onDismissRequest = { zoneDropdownExpanded = false },
                    ) {
                        zonePriority.forEach { zone ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(zone.displayName)
                                        if (zone in zonesWithCards) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedZone = zone
                                    zoneDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Domain hint
            if (legendDomains.isNotEmpty() && selectedZone != DeckZone.battlefield) {
                Text(
                    "Only cards from: ${legendDomains.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

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
                listItems(filteredCards, key = { it.id }) { card ->
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
