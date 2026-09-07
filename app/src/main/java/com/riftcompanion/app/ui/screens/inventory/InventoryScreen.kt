package com.riftcompanion.app.ui.screens.inventory

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.QuantityBadge
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.CardViewMode
import com.riftcompanion.app.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    onCardClick: (String, Boolean) -> Unit,
    onMenuClick: () -> Unit = {},
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableDomains by viewModel.availableDomains.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier.gradientBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // List / Grid toggle
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = uiState.viewMode == CardViewMode.LIST,
                            onClick = { viewModel.setViewMode(CardViewMode.LIST) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Icon(Icons.Default.List, contentDescription = "List") },
                        )
                        SegmentedButton(
                            selected = uiState.viewMode == CardViewMode.GRID,
                            onClick = { viewModel.setViewMode(CardViewMode.GRID) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Icon(Icons.Default.GridView, contentDescription = "Grid") },
                        )
                    }
                    // Filter button — opens bottom sheet
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Filters",
                            tint = if (uiState.selectedLocation != null || uiState.domainFilters.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Compact search bar
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            // Active filter chips
            if (uiState.selectedLocation != null || uiState.domainFilters.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.selectedLocation?.let { locName ->
                        val loc = uiState.locations.firstOrNull { it.normalizedName == locName }
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "No cards match your search."
                        else "No inventory. Sync from Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (uiState.viewMode == CardViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        InventoryGridCard(card = card, onClick = { onCardClick(card.id, true) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        InventoryListRow(card = card, onClick = { onCardClick(card.id, true) })
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            FilterSheetContent(
                locations = uiState.locations,
                selectedLocation = uiState.selectedLocation,
                onLocationSelected = { viewModel.setSelectedLocation(it) },
                availableDomains = availableDomains,
                selectedDomains = uiState.domainFilters,
                onDomainToggled = { viewModel.toggleDomainFilter(it) },
                onClearAll = {
                    viewModel.setSelectedLocation(null)
                    viewModel.clearDomainFilters()
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    locations: List<LocationPolicy>,
    selectedLocation: String?,
    onLocationSelected: (String?) -> Unit,
    availableDomains: List<String>,
    selectedDomains: Set<String>,
    onDomainToggled: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            if (selectedLocation != null || selectedDomains.isNotEmpty()) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onClearAll() },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Location section
        Text("Location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedLocation == null,
                onClick = { onLocationSelected(null) },
                label = { Text("All Locations") },
            )
            locations.forEach { location ->
                FilterChip(
                    selected = selectedLocation == location.normalizedName,
                    onClick = { onLocationSelected(location.normalizedName) },
                    label = { Text(location.displayName) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Domains section
        Text("Domains", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            availableDomains.forEach { domain ->
                FilterChip(
                    selected = selectedDomains.contains(domain),
                    onClick = { onDomainToggled(domain) },
                    label = { Text(domain) },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InventoryGridCard(card: InventoryCardSummary, onClick: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier.clickable(onClick = onClick),
        cornerRadius = 13,
        tintStrength = 0.05f,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = card.identity.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                cornerRadius = 11,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                card.identity.appVisibleDomains.forEach { DomainTag(domain = it) }
                card.identity.tags.forEach { DomainTag(domain = it) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuantityBadge(title = "Total", value = card.availability.totalOwned)
                QuantityBadge(
                    title = "Free",
                    value = card.availability.availableInStorage,
                    tint = androidx.compose.ui.graphics.Color(0xFF43A047),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InventoryListRow(card: InventoryCardSummary, onClick: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 12,
        tintStrength = 0.04f,
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .width(44.dp)
                    .height(62.dp),
                cornerRadius = 6,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.identity.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    color = androidx.compose.ui.graphics.Color(0xFF43A047),
                )
            }
        }
    }
}
