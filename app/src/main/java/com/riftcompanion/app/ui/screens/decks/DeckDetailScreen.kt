package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
    onBuildDeck: (String) -> Unit = {},
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val detailState by viewModel.deckDetailState.collectAsStateWithLifecycle()
    val buildState by viewModel.buildState.collectAsStateWithLifecycle()

    LaunchedEffect(deckId) { viewModel.loadDeckDetail(deckId) }

    var showDisassembleDialog by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Drag & drop state for moving cards between Main and Sideboard
    var draggedCard by remember { mutableStateOf<DeckEntryDisplay?>(null) }
    var dragPositionInRoot by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val dropZoneBounds = remember { mutableStateMapOf<DeckZone, androidx.compose.ui.geometry.Rect>() }

    // Pending drop: when card has quantity > 1, show a picker
    var pendingDrop by remember { mutableStateOf<PendingDrop?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    // Auto-scroll during drag — handled synchronously in the drag callback via dispatchRawDelta
    // No LaunchedEffect loop needed — scroll happens on every drag delta

    fun handleDragEnd() {
        val card = draggedCard ?: return
        val isMainSideboard = card.zone == DeckZone.main || card.zone == DeckZone.sideboard
        if (isMainSideboard) {
            val targetZone = dropZoneBounds.entries.firstOrNull { (zone, bounds) ->
                zone != card.zone && (zone == DeckZone.main || zone == DeckZone.sideboard) && bounds.contains(dragPositionInRoot)
            }?.key
            if (targetZone != null) {
                if (card.quantity > 1) {
                    // Show quantity picker
                    pendingDrop = PendingDrop(card, targetZone)
                } else {
                    viewModel.moveCardToZone(deckId, card.nameSlug, card.zone, targetZone, 1)
                }
            }
        }
        draggedCard = null
    }

    val zonesWithCards = detailState.entries.map { it.zone }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground(),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            val deckName = detailState.deck?.name ?: "Deck"
            var editName by remember(deckName) { mutableStateOf(deckName) }
            if (isEditing && detailState.deck != null) {
                androidx.compose.foundation.text.BasicTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    viewModel.saveDeck(deckId, editName) {
                        isEditing = false
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Save deck", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Text(
                    text = deckName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            val deck = detailState.deck
            val deckBuilt = deck?.isBuilt == true
            // Edit toggle
            IconButton(onClick = {
                if (isEditing) {
                    // Exiting edit mode = discard changes
                    viewModel.discardEdits(deckId) {
                        isEditing = false
                    }
                } else {
                    // Entering edit mode = snapshot current state
                    viewModel.beginEditSession(deckId)
                    isEditing = true
                }
            }) {
                Icon(
                    if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (isEditing) "Done editing" else "Edit deck",
                    tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            // Build status / Disassemble
            if (deck != null) {
                if (deckBuilt) {
                    // Built: show checkmark + disassemble icon
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Deck is built",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    IconButton(onClick = { showDisassembleDialog = true }) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Disassemble deck", tint = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    // Not built: show build icon
                    IconButton(
                        onClick = { onBuildDeck(deckId) },
                        enabled = deck.isLegal && !deck.hasMissingCards,
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "Build deck",
                            tint = if (deck.isLegal && !deck.hasMissingCards) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // View toggle
            IconButton(onClick = { gridMode = !gridMode }) {
                Icon(
                    if (gridMode) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (gridMode) "List view" else "Grid view",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (detailState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val deck = detailState.deck
            if (deck != null) {
                LegalityBanner(deck.isLegal, deck.isBuilt, deck.hasMissingCards, deck.legalityIssues, deck.banlistWarnings)
            }

            // Edit mode indicator
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Add Card button — prominent, always visible in edit mode
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    OutlinedButton(onClick = { showAddCardSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Card")
                    }
                }
            }

            // Card entries grouped by zone
            val grouped = detailState.entries.groupBy { it.zone }

            // Zone display order: Legend, Champion, Main, Sideboard, then Runes and Battlefields at the bottom
            val zoneDisplayOrder = listOf(
                DeckZone.legend,
                DeckZone.chosenChampion,
                DeckZone.main,
                DeckZone.sideboard,
                DeckZone.rune,
                DeckZone.battlefield,
            )

            if (gridMode) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val legend = grouped[DeckZone.legend]?.firstOrNull()
                    val champion = grouped[DeckZone.chosenChampion]?.firstOrNull()
                    if (legend != null || champion != null) {
                        item {
                            ZoneHeader("Legend & Champion")
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                if (legend != null) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DeckCardGrid(legend, isEditing = isEditing, onAdd = { viewModel.addCardToDeck(deckId, legend.nameSlug, legend.zone) }, onRemove = { viewModel.removeCardFromDeck(deckId, legend.nameSlug, legend.zone) }, onClick = { onCardClick(legend.nameSlug) })
                                    }
                                } else { Spacer(modifier = Modifier.weight(1f)) }
                                if (champion != null) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        DeckCardGrid(champion, isEditing = isEditing, onAdd = { viewModel.addCardToDeck(deckId, champion.nameSlug, champion.zone) }, onRemove = { viewModel.removeCardFromDeck(deckId, champion.nameSlug, champion.zone) }, onClick = { onCardClick(champion.nameSlug) })
                                    }
                                } else { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                    zoneDisplayOrder.filter { it != DeckZone.legend && it != DeckZone.chosenChampion }.forEach { zone ->
                        val items = grouped[zone]
                        if (!items.isNullOrEmpty()) {
                            item { ZoneHeader(zone.displayName) }
                            items.sortedBy { it.displayName }.chunked(2).forEach { rowItems ->
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { entry ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                DeckCardGrid(entry, isEditing = isEditing,
                                                    onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                                    onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                                    onClick = { onCardClick(entry.nameSlug) },
                                                )
                                            }
                                        }
                                        if (rowItems.size == 1) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            listBounds = androidx.compose.ui.geometry.Rect(
                                pos.x, pos.y,
                                pos.x + coords.size.width,
                                pos.y + coords.size.height,
                            )
                        },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    zoneDisplayOrder.forEach { zone ->
                        val items = grouped[zone]
                        val isDropZone = zone == DeckZone.main || zone == DeckZone.sideboard
                        // Always render Main and Sideboard headers in edit mode (even if empty)
                        val showHeader = !items.isNullOrEmpty() || (isEditing && isDropZone)
                        if (showHeader) {
                            item {
                                if (isDropZone) {
                                    DropZoneHeader(
                                        zone = zone,
                                        draggedCard = draggedCard,
                                        onBoundsChanged = { bounds -> dropZoneBounds[zone] = bounds },
                                    )
                                } else {
                                    ZoneHeader(zone.displayName)
                                }
                            }
                        }
                        if (!items.isNullOrEmpty()) {
                            items.sortedBy { it.displayName }.forEach { entry ->
                                item {
                                    DeckCardRow(
                                        entry = entry,
                                        isEditing = isEditing,
                                        onAdd = { viewModel.addCardToDeck(deckId, entry.nameSlug, entry.zone) },
                                        onRemove = { viewModel.removeCardFromDeck(deckId, entry.nameSlug, entry.zone) },
                                        onMoveZone = { targetZone ->
                                            viewModel.moveCardToZone(deckId, entry.nameSlug, entry.zone, targetZone, 1)
                                        },
                                        onClick = { onCardClick(entry.nameSlug) },
                                        onDragStart = {
                                            if (entry.zone == DeckZone.main || entry.zone == DeckZone.sideboard) {
                                                draggedCard = entry
                                            }
                                        },
                                        onDragPosition = { dragPositionInRoot = it },
                                        onDragEnd = { handleDragEnd() },
                                        listState = listState,
                                        listBounds = listBounds,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Floating drag overlay — shows the card following the finger
    draggedCard?.let { card ->
        DragOverlay(
            card = card,
            position = dragPositionInRoot,
        )
    }

    // Build is now handled by DeckBuildScreen — no popup dialog needed
    if (buildState.isBuilt) {
        LaunchedEffect(Unit) {
            viewModel.clearBuildState()
            viewModel.loadDeckDetail(deckId)
            isEditing = false
        }
    }

    if (showDisassembleDialog) {
        AlertDialog(
            onDismissRequest = { showDisassembleDialog = false },
            title = { Text("Disassemble deck") },
            text = { Text("Cards will be returned to their original storage locations. Continue?") },
            confirmButton = {
                TextButton(onClick = { viewModel.disassembleDeck(deckId); showDisassembleDialog = false }) { Text("Disassemble") }
            },
            dismissButton = { TextButton(onClick = { showDisassembleDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAddCardSheet) {
        AddCardSheet(
            deckId = deckId,
            zonesWithCards = zonesWithCards,
            onDismiss = { showAddCardSheet = false },
            onAddCard = { nameSlug, zone -> viewModel.addCardToDeck(deckId, nameSlug, zone) },
        )
    }

    // Quantity picker for drag & drop
    pendingDrop?.let { drop ->
        MoveQuantityDialog(
            cardName = drop.card.displayName,
            maxQuantity = drop.card.quantity,
            fromZone = drop.card.zone,
            toZone = drop.targetZone,
            onConfirm = { quantity ->
                viewModel.moveCardToZone(deckId, drop.card.nameSlug, drop.card.zone, drop.targetZone, quantity)
                pendingDrop = null
            },
            onDismiss = { pendingDrop = null },
        )
    }
}

private data class PendingDrop(
    val card: DeckEntryDisplay,
    val targetZone: DeckZone,
)

@Composable
private fun MoveQuantityDialog(
    cardName: String,
    maxQuantity: Int,
    fromZone: DeckZone,
    toZone: DeckZone,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var quantity by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move cards") },
        text = {
            Column {
                Text(
                    text = cardName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "From ${fromZone.displayName} to ${toZone.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { quantity = maxOf(1, quantity - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = "$quantity",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { quantity = minOf(maxQuantity, quantity + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Max: $maxQuantity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onConfirm(quantity) }) {
                Text("Move $quantity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DragOverlay(
    card: DeckEntryDisplay,
    position: androidx.compose.ui.geometry.Offset,
) {
    ThemedCardSurface(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(
                (position.x - 160.dp.toPx()).roundToInt(),
                (position.y - 30.dp.toPx()).roundToInt(),
            ) }
            .width(300.dp),
        cornerRadius = 10,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.displayName,
                modifier = Modifier.width(36.dp).height(50.dp),
                cornerRadius = 5,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${card.quantity} in ${card.zone.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${card.quantity}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ZoneHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DropZoneHeader(
    zone: DeckZone,
    draggedCard: DeckEntryDisplay?,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val isDragging = draggedCard != null &&
        (draggedCard.zone == DeckZone.main || draggedCard.zone == DeckZone.sideboard) &&
        draggedCard.zone != zone

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                // Expand the drop zone to cover header + generous area below for easier dropping
                onBoundsChanged(
                    androidx.compose.ui.geometry.Rect(
                        pos.x - 16f,
                        pos.y - 20f,
                        pos.x + coords.size.width + 16f,
                        pos.y + coords.size.height + 200f, // extend well below the header
                    ),
                )
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (isDragging) "Drop here → ${zone.displayName}" else zone.displayName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Clean list view card row. Read-only by default, shows ± and move buttons in edit mode.
 * Drag & drop only active in edit mode for Main Deck / Sideboard cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCardRow(
    entry: DeckEntryDisplay,
    isEditing: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onMoveZone: (DeckZone) -> Unit,
    onClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragPosition: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    listBounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
) {
    val canDrag = isEditing && (entry.zone == DeckZone.main || entry.zone == DeckZone.sideboard)

    val rowBounds = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    ThemedCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                rowBounds.value = androidx.compose.ui.geometry.Rect(
                    pos.x, pos.y,
                    pos.x + coords.size.width,
                    pos.y + coords.size.height,
                )
            },
        cornerRadius = 10,
    ) {
        Row(
            modifier = Modifier
            .then(
                if (canDrag) {
                    Modifier.pointerInput(entry.nameSlug) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        ) { change, _ ->
                            change.consume()
                            // Convert local position to root position
                            val rootPos = rowBounds.value.topLeft + change.position
                            onDragPosition(rootPos)
                            // Auto-scroll: check if near edges and scroll synchronously
                            val topThreshold = listBounds.top + 100f
                            val bottomThreshold = listBounds.bottom - 100f
                            if (rootPos.y < topThreshold && rootPos.y > listBounds.top) {
                                val proximity = ((topThreshold - rootPos.y) / topThreshold).coerceIn(0f, 1f)
                                val speed = (proximity * 40f).coerceIn(8f, 40f)
                                listState.dispatchRawDelta(-speed)
                            } else if (rootPos.y > bottomThreshold && rootPos.y < listBounds.bottom) {
                                val proximity = ((rootPos.y - bottomThreshold) / (listBounds.bottom - bottomThreshold)).coerceIn(0f, 1f)
                                val speed = (proximity * 40f).coerceIn(8f, 40f)
                                listState.dispatchRawDelta(speed)
                            }
                        }
                    }
                } else if (isEditing) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardArtwork(
                imageURL = entry.preferredImageURL,
                name = entry.displayName,
                modifier = Modifier.width(36.dp).height(50.dp),
                cornerRadius = 5,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Availability: in deck / in storage / missing
                val availText = buildString {
                    val parts = mutableListOf<String>()
                    if (entry.inDeckLocation > 0) parts.add("${entry.inDeckLocation} in deck")
                    if (entry.availableInStorage > 0) parts.add("${entry.availableInStorage} in storage")
                    if (entry.inOtherDecks > 0) parts.add("${entry.inOtherDecks} in other decks")
                    if (entry.isMissing) parts.add("${entry.missingCount} missing")
                    append(parts.joinToString(" · "))
                }
                Text(
                    availText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        entry.isMissing -> MaterialTheme.colorScheme.error
                        entry.inDeckLocation >= entry.quantity -> com.riftcompanion.app.ui.theme.freeColor()
                        entry.availableInStorage + entry.inDeckLocation >= entry.quantity -> com.riftcompanion.app.ui.theme.freeColor()
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isEditing) {
                // Edit mode: ± buttons, quantity, and move button
                val canMoveToSideboard = entry.zone == DeckZone.main
                val canMoveToMain = entry.zone == DeckZone.sideboard

                IconButton(onClick = onRemove, enabled = entry.quantity > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove one", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${entry.quantity}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add one", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }

                // Move between main and sideboard
                if (canMoveToSideboard) {
                    IconButton(
                        onClick = { onMoveZone(DeckZone.sideboard) },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Move to Sideboard" },
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                } else if (canMoveToMain) {
                    IconButton(
                        onClick = { onMoveZone(DeckZone.main) },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Move to Main Deck" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                // Read-only: just show quantity
                Text(
                    text = "${entry.quantity}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Grid view card.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeckCardGrid(
    entry: DeckEntryDisplay,
    isEditing: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    ThemedCardSurface(modifier = Modifier, cornerRadius = 14) {
        Column(modifier = Modifier.padding(12.dp).clickable(onClick = onClick)) {
            Text(entry.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            CardArtwork(
                imageURL = entry.preferredImageURL,
                name = entry.displayName,
                modifier = Modifier.fillMaxWidth().aspectRatio(5f / 7f),
                cornerRadius = 11,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.quantity}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("in deck", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (isEditing) {
                    IconButton(onClick = onRemove, enabled = entry.quantity > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (entry.isMissing) {
                Text("${entry.missingCount} missing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LegalityBanner(isLegal: Boolean, isBuilt: Boolean, hasMissingCards: Boolean, issues: List<String>, banlistWarnings: List<String>) {
    if (isBuilt) return
    var showAllIssues by remember { mutableStateOf(false) }
    var showAllWarnings by remember { mutableStateOf(false) }

    // Errors
    if (!isLegal) {
        val (color, icon, text) = Triple(MaterialTheme.colorScheme.error, Icons.Default.Error, "Not legal — ${issues.size} issue(s)")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAllIssues = !showAllIssues }) {
                Text(if (showAllIssues) "Hide" else "Show all", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (showAllIssues) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    } else if (hasMissingCards) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Legal — missing cards", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = com.riftcompanion.app.ui.theme.freeColor(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Legal — ready to build", style = MaterialTheme.typography.bodyMedium, color = com.riftcompanion.app.ui.theme.freeColor(), fontWeight = FontWeight.Medium)
        }
    }

    // Banlist warnings (always shown if present)
    if (banlistWarnings.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("${banlistWarnings.size} banned card(s) — legal in 2v2 only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showAllWarnings = !showAllWarnings }) {
                Text(if (showAllWarnings) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (showAllWarnings) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                banlistWarnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

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
    val detailState by deckViewModel.deckDetailState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var legendDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var legendTags by remember { mutableStateOf<List<String>>(emptyList()) }

    val zonePriority = if (DeckZone.legend in zonesWithCards) {
        listOf(DeckZone.chosenChampion, DeckZone.main, DeckZone.rune, DeckZone.battlefield, DeckZone.sideboard)
    } else {
        listOf(DeckZone.legend, DeckZone.chosenChampion, DeckZone.main, DeckZone.rune, DeckZone.battlefield, DeckZone.sideboard)
    }
    var selectedZone by remember {
        mutableStateOf(zonePriority.firstOrNull { it !in zonesWithCards } ?: DeckZone.main)
    }
    var zoneDropdownExpanded by remember { mutableStateOf(false) }

    val deckQuantities = remember(detailState.entries, selectedZone) {
        val totalBySlug = detailState.entries.groupBy { it.nameSlug }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
        val zoneBySlug = detailState.entries
            .filter { it.zone == selectedZone }
            .associate { it.nameSlug to it.quantity }
        totalBySlug to zoneBySlug
    }

    LaunchedEffect(deckId) {
        deckViewModel.getLegendDomains(deckId) { legendDomains = it }
        deckViewModel.getLegendTags(deckId) { legendTags = it }
    }

    LaunchedEffect(searchQuery) { catalogueViewModel.setSearchQuery(searchQuery) }

    val filteredCards = catalogueState.cards.filter { card ->
        val cardType = card.identity.cardType?.lowercase() ?: ""
        val cardTags = card.identity.tags.map { it.lowercase().trim() }
        when (selectedZone) {
            DeckZone.legend -> cardType.contains("legend")
            DeckZone.chosenChampion -> {
                (cardType.contains("champion") || cardType.contains("unit")) &&
                    (legendTags.isEmpty() || cardTags.any { it in legendTags })
            }
            DeckZone.rune -> {
                if (!cardType.contains("rune")) false
                else if (legendDomains.isEmpty()) true
                else {
                    val cardDomains = card.identity.appVisibleDomains
                    cardDomains.isEmpty() || cardDomains.any { it in legendDomains }
                }
            }
            DeckZone.battlefield -> cardType.contains("battlefield")
            DeckZone.main, DeckZone.sideboard -> {
                if (cardType.contains("legend") || cardType.contains("rune") || cardType.contains("battlefield")) false
                else if (legendDomains.isEmpty()) true
                else {
                    val cardDomains = card.identity.appVisibleDomains
                    cardDomains.isEmpty() || cardDomains.any { it in legendDomains }
                }
            }
            else -> true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Add Card to Deck", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zone: ", style = MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(onClick = { zoneDropdownExpanded = true }) {
                        Text(selectedZone.displayName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = zoneDropdownExpanded, onDismissRequest = { zoneDropdownExpanded = false }) {
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
                                onClick = { selectedZone = zone; zoneDropdownExpanded = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (legendDomains.isNotEmpty() && selectedZone != DeckZone.battlefield) {
                Text("Only cards from: ${legendDomains.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                items(filteredCards, key = { it.id }) { card ->
                    val totalInDeck = deckQuantities.first[card.id] ?: 0
                    val inZone = deckQuantities.second[card.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddCard(card.id, selectedZone) }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.identity.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (totalInDeck > 0 || inZone > 0) {
                                Text(
                                    buildString {
                                        if (inZone > 0) append("$inZone in zone")
                                        if (inZone > 0 && totalInDeck > 0) append(" · ")
                                        if (totalInDeck > 0) append("$totalInDeck in deck")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
