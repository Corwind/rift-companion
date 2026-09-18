package com.riftcompanion.app.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.QuantityBadge
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.CardViewMode
import com.riftcompanion.app.ui.viewmodel.InventoryQuantityDraftKey
import com.riftcompanion.app.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    onCardClick: (String, Boolean) -> Unit,
    initialLocationFilter: String? = null,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableDomains by viewModel.availableDomains.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Apply initial location filter when navigated from Locations screen
    androidx.compose.runtime.LaunchedEffect(initialLocationFilter) {
        initialLocationFilter?.let { viewModel.setSelectedLocation(it) }
    }

    // Show save success/error as snackbar-like
    androidx.compose.runtime.LaunchedEffect(uiState.saveSuccess) {
        uiState.saveSuccess?.let {
            // Auto-clear after showing
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0,0,0,0),
    ) { padding ->
        Column(modifier = Modifier.padding(padding).statusBarsPadding()) {
            if (uiState.isEditing) {
                // Edit mode: search bar + Cancel + Save on one row
                EditModeHeader(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    filteredCount = uiState.filteredCount,
                    drafts = viewModel.drafts.collectAsStateWithLifecycle(),
                    isSaving = uiState.isSaving,
                    onSave = { viewModel.saveChanges() },
                    onCancel = {
                        if (viewModel.hasChanges) {
                            showDiscardDialog = true
                        } else {
                            viewModel.cancelEditing()
                        }
                    },
                )
                // Save error inline
                uiState.saveError?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.cards.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "No Cards",
                        subtitle = "Sync from Settings to load your cards.",
                    )
                } else {
                    InventoryEditModeContent(
                        cards = uiState.cards,
                        locations = uiState.locations,
                        viewModel = viewModel,
                    )
                }
            } else {
                // Normal mode: search bar + filters + grid/list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("${uiState.filteredCount} cards", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        textStyle = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = {
                        viewModel.setViewMode(
                            if (uiState.viewMode == CardViewMode.GRID) CardViewMode.LIST else CardViewMode.GRID
                        )
                    }) {
                        Icon(
                            if (uiState.viewMode == CardViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = if (uiState.viewMode == CardViewMode.GRID) "List view" else "Grid view",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Filters",
                            tint = if (uiState.selectedLocation != null || uiState.domainFilters.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.startEditing() },
                        enabled = uiState.cards.isNotEmpty() && !uiState.isSaving,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit inventory",
                            tint = if (uiState.cards.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (uiState.selectedLocation != null || uiState.domainFilters.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        uiState.selectedLocation?.let { locName ->
                            val loc = uiState.locations.firstOrNull { it.name == locName }
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.setSelectedLocation(null) },
                                label = { Text(loc?.displayName ?: locName) },
                            )
                        }
                        uiState.domainFilters.forEach { domain ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleDomainFilter(domain) },
                                label = { Text(domain) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.cards.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Inventory2,
                        title = if (uiState.searchQuery.isNotBlank()) "No Results" else "No Inventory",
                        subtitle = if (uiState.searchQuery.isNotBlank()) "No cards match your search."
                        else "Sync from Settings to load your cards.",
                    )
                } else if (uiState.viewMode == CardViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.cards, key = { it.id }) { card ->
                            InventoryGridCard(card = card, isBanned = viewModel.isCardBanned(card.identity.displayName), onClick = { onCardClick(card.id, true) })
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.cards, key = { it.id }) { card ->
                            InventoryListRow(card = card, isBanned = viewModel.isCardBanned(card.identity.displayName), onClick = { onCardClick(card.id, true) })
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            dragHandle = null,
        ) {
            FilterSheetContent(
                locations = uiState.locations,
                cardCountsByLocation = uiState.cardCountsByLocation,
                totalCardCount = uiState.allLocationsCount,
                selectedLocation = uiState.selectedLocation,
                onLocationSelected = { viewModel.setSelectedLocation(it) },
                availableDomains = availableDomains,
                selectedDomains = uiState.domainFilters,
                onDomainToggled = { viewModel.toggleDomainFilter(it) },
                onClearAll = {
                    viewModel.setSelectedLocation(null)
                    viewModel.clearDomainFilters()
                },
                onApply = { showFilterSheet = false },
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard inventory changes?") },
            text = { Text("Your unsaved location quantities will be lost. CardNexus has not been changed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.cancelEditing()
                }) { Text("Discard Changes", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep Editing") }
            },
        )
    }
}

@Composable
private fun EditModeHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredCount: Int,
    drafts: androidx.compose.runtime.State<Map<InventoryQuantityDraftKey, Int>>,
    isSaving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val changedCount = remember(drafts.value) {
        drafts.value.keys.map { it.cardID }.toSet().size
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("$filteredCount cards", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            textStyle = MaterialTheme.typography.labelMedium,
        )
        if (changedCount > 0) {
            Text(
                text = "$changedCount",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "$changedCount cards have changes" },
            )
        }
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        TextButton(onClick = onCancel, enabled = !isSaving) {
            Text("Cancel")
        }
        androidx.compose.material3.Button(
            onClick = onSave,
            enabled = changedCount > 0 && !isSaving,
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun InventoryEditModeContent(
    cards: List<InventoryCardSummary>,
    locations: List<LocationPolicy>,
    viewModel: InventoryViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            InventoryCardEditor(
                card = card,
                locations = locations,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun InventoryCardEditor(
    card: InventoryCardSummary,
    locations: List<LocationPolicy>,
    viewModel: InventoryViewModel,
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()

    // Only show locations where the card already has copies, or where the user has added a draft
    val cardLocationNames = card.locations.map { it.locationName }.toSet()
    val draftLocationsForCard = drafts.keys
        .filter { it.cardID == card.id }
        .map { it.locationKey }
        .toSet()
    val visibleLocationNames = cardLocationNames + draftLocationsForCard

    val allEditableLocations = locations.filter { it.kind != LocationKind.Unavailable }
        .sortedWith(
            compareByDescending<LocationPolicy> { it.name == "Unlocated" }
                .thenBy { it.displayName.lowercase() }
        )

    val visibleLocations = allEditableLocations.filter { it.name in visibleLocationNames }
    val addableLocations = allEditableLocations.filter { it.name !in visibleLocationNames }

    val hasChanges = visibleLocations.any { location ->
        val original = card.locations.filter { it.locationName == location.name }.sumOf { it.quantity }
        val draft = drafts[InventoryQuantityDraftKey(card.id, location.name)] ?: original
        draft != original
    }

    var showAddLocationDialog by remember { mutableStateOf(false) }

    ThemedCardSurface(cornerRadius = 12) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Card header - compact, single line
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardArtwork(
                    imageURL = card.preferredImageURL,
                    name = card.identity.displayName,
                    modifier = Modifier.width(32.dp).height(44.dp),
                    cornerRadius = 5,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = card.identity.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (hasChanges) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasChanges) {
                    Text(
                        text = "\u2022",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Only show locations where the card already has copies (or drafts)
            visibleLocations.forEachIndexed { index, location ->
                val originalQty = card.locations
                    .filter { it.locationName == location.name }
                    .sumOf { it.quantity }
                val draftQty = drafts[InventoryQuantityDraftKey(card.id, location.name)] ?: originalQty
                val isReadOnly = location.name == "Unlocated"
                val isNewLocation = originalQty == 0 && !isReadOnly

                LocationQuantityControl(
                    locationName = location.displayName,
                    locationKind = location.kind,
                    color = location.color,
                    quantity = draftQty,
                    originalQuantity = originalQty,
                    isReadOnly = isReadOnly,
                    isNewLocation = isNewLocation,
                    onDecrement = { viewModel.setDraftQuantity(card.id, location.name, draftQty - 1) },
                    onIncrement = { viewModel.setDraftQuantity(card.id, location.name, draftQty + 1) },
                )
                if (index < visibleLocations.lastIndex) Spacer(Modifier.height(4.dp))
            }

            // Add to location button
            if (addableLocations.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAddLocationDialog = true }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Add to location",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showAddLocationDialog) {
        AddLocationDialog(
            cardName = card.identity.displayName,
            availableLocations = addableLocations,
            onDismiss = { showAddLocationDialog = false },
            onConfirm = { locationName, quantity ->
                viewModel.setDraftQuantity(card.id, locationName, quantity)
                showAddLocationDialog = false
            },
        )
    }
}

@Composable
private fun AddLocationDialog(
    cardName: String,
    availableLocations: List<LocationPolicy>,
    onDismiss: () -> Unit,
    onConfirm: (locationName: String, quantity: Int) -> Unit,
) {
    var selectedLocation by remember { mutableStateOf(availableLocations.firstOrNull()) }
    var quantityText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to location") },
        text = {
            Column {
                Text(
                    text = cardName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Location selector
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                availableLocations.forEach { location ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedLocation == location)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .clickable { selectedLocation = location }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .semantics {
                                contentDescription = if (selectedLocation == location)
                                    "${location.displayName}, selected" else location.displayName
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    location.color?.let { com.riftcompanion.app.ui.theme.parseColor(it) }
                                        ?: MaterialTheme.colorScheme.outline
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = location.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedLocation == location) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(12.dp))

                // Quantity input
                Text(
                    text = "Quantity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        quantityText = value.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    if (qty > 0 && selectedLocation != null) {
                        onConfirm(selectedLocation!!.name, qty)
                    }
                },
                enabled = selectedLocation != null && (quantityText.toIntOrNull() ?: 0) > 0,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LocationQuantityControl(
    locationName: String,
    locationKind: LocationKind,
    color: String?,
    quantity: Int,
    originalQuantity: Int,
    isReadOnly: Boolean,
    isNewLocation: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val locationColor = color?.let { com.riftcompanion.app.ui.theme.parseColor(it) } ?: MaterialTheme.colorScheme.outline
    val icon = when (locationKind) {
        LocationKind.Storage -> Icons.Default.Inventory2
        LocationKind.Deck -> Icons.Default.Menu
        LocationKind.Unavailable -> Icons.Default.Block
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isNewLocation) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "$locationName: $quantity copies" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(locationColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = locationName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isNewLocation) {
            Text(
                text = "new",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .semantics { contentDescription = "New location for this card" },
            )
            Spacer(Modifier.width(8.dp))
        }
        // Minus button
        IconButton(
            onClick = onDecrement,
            enabled = quantity > 0 && !isReadOnly,
            modifier = Modifier.semantics { contentDescription = "Remove one from $locationName" },
        ) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        // Quantity
        Text(
            text = "$quantity",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Plus button
        IconButton(
            onClick = onIncrement,
            enabled = !isReadOnly,
            modifier = Modifier.semantics { contentDescription = "Add one to $locationName" },
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    locations: List<LocationPolicy>,
    cardCountsByLocation: Map<String, Int>,
    totalCardCount: Int,
    selectedLocation: String?,
    onLocationSelected: (String?) -> Unit,
    availableDomains: List<String>,
    selectedDomains: Set<String>,
    onDomainToggled: (String) -> Unit,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedLocation != null || selectedDomains.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClearAll() },
                    )
                }
                IconButton(onClick = onApply) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedLocation == null,
                onClick = { onLocationSelected(null) },
                label = { Text("All Locations ($totalCardCount)") },
            )
            locations.forEach { location ->
                val count = cardCountsByLocation[location.name] ?: 0
                FilterChip(
                    selected = selectedLocation == location.name,
                    onClick = { onLocationSelected(location.name) },
                    label = { Text("${location.displayName} ($count)") },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Domains", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            availableDomains.forEach { domain ->
                val isSelected = selectedDomains.contains(domain)
                DomainFilterTag(
                    domain = domain,
                    selected = isSelected,
                    onClick = { onDomainToggled(domain) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        androidx.compose.material3.Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun DomainFilterTag(
    domain: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val baseColor = com.riftcompanion.app.ui.theme.domainColor(domain)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = if (isDark) baseColor.copy(
        red = (baseColor.red * 0.5f + 0.5f).coerceIn(0f, 1f),
        green = (baseColor.green * 0.5f + 0.5f).coerceIn(0f, 1f),
        blue = (baseColor.blue * 0.5f + 0.5f).coerceIn(0f, 1f),
    ) else baseColor
    val bgAlpha = if (isDark) 0.22f else 0.14f
    val effectiveAlpha = if (selected) bgAlpha else bgAlpha * 0.3f
    val textColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = effectiveAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (selected) 1f else 0.4f)),
        )
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InventoryGridCard(card: InventoryCardSummary, isBanned: Boolean, onClick: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier.clickable(onClick = onClick),
        cornerRadius = 14,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.identity.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isBanned) {
                    // Banned band is rendered by CardArtwork below
                }
            }
            Spacer(Modifier.height(8.dp))
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                cornerRadius = 11,
                isBanned = isBanned,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                card.identity.appVisibleDomains.forEach { DomainTag(domain = it) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuantityBadge(title = "Total", value = card.availability.totalOwned)
                QuantityBadge(
                    title = "Free",
                    value = card.availability.availableInStorage,
                    tint = com.riftcompanion.app.ui.theme.freeColor(),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InventoryListRow(card: InventoryCardSummary, isBanned: Boolean, onClick: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 12,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .width(44.dp)
                    .height(62.dp),
                cornerRadius = 6,
                isBanned = isBanned,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.identity.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = listOfNotNull(card.identity.cardType, card.expansion, card.rarity).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    card.identity.appVisibleDomains.forEach { DomainTag(domain = it) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${card.availability.totalOwned}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "${card.availability.availableInStorage} free",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.riftcompanion.app.ui.theme.freeColor(),
                )
                card.priceEur?.let {
                    Text(
                        text = "€${String.format("%.2f", it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
