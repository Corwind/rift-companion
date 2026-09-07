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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.riftcompanion.app.domain.model.InventoryCardSummary
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.QuantityBadge
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.viewmodel.CardViewMode
import com.riftcompanion.app.ui.viewmodel.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    onCardClick: (String, Boolean) -> Unit, // nameSlug, isFromInventory
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableDomains by viewModel.availableDomains.collectAsStateWithLifecycle()

    var showLocationMenu by remember { mutableStateOf(false) }
    var showDomainMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                actions = {
                    // View mode toggle
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
                    // Location filter
                    Box {
                        IconButton(onClick = { showLocationMenu = true }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Filter by location")
                        }
                        DropdownMenu(expanded = showLocationMenu, onDismissRequest = { showLocationMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All Locations (${uiState.totalCards})") },
                                onClick = { viewModel.setSelectedLocation(null); showLocationMenu = false },
                            )
                            uiState.locations.forEach { location ->
                                val count = uiState.locations.let { locs ->
                                    // card count for this location
                                    0 // will be computed from uiState
                                }
                                DropdownMenuItem(
                                    text = { Text("${location.displayName}") },
                                    onClick = { viewModel.setSelectedLocation(location.normalizedName); showLocationMenu = false },
                                )
                            }
                        }
                    }
                    // Domain filter
                    Box {
                        IconButton(onClick = { showDomainMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Filter by domain")
                        }
                        DropdownMenu(expanded = showDomainMenu, onDismissRequest = { showDomainMenu = false }) {
                            if (uiState.domainFilters.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear Domain Filters") },
                                    onClick = { viewModel.clearDomainFilters(); showDomainMenu = false },
                                )
                            }
                            availableDomains.forEach { domain ->
                                DropdownMenuItem(
                                    text = {
                                        Row {
                                            Checkbox(
                                                checked = uiState.domainFilters.contains(domain),
                                                onCheckedChange = { viewModel.toggleDomainFilter(domain) },
                                            )
                                            Text(domain)
                                        }
                                    },
                                    onClick = { viewModel.toggleDomainFilter(domain) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Names, descriptions, domains, types…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Active filter chips
            if (uiState.selectedLocation != null || uiState.domainFilters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "No cards match your search."
                        else "No inventory. Pull to sync from CardNexus.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else if (uiState.viewMode == CardViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        InventoryGridCard(card = card, onClick = { onCardClick(card.id, true) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        InventoryListRow(card = card, onClick = { onCardClick(card.id, true) })
                    }
                }
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
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = card.identity.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                cornerRadius = 11,
            )
            Spacer(Modifier.height(8.dp))
            // Domain tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                card.identity.appVisibleDomains.forEach { domain ->
                    DomainTag(domain = domain)
                }
                card.identity.tags.forEach { tag ->
                    DomainTag(domain = tag)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Quantity badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CardArtwork(
                imageURL = card.preferredImageURL,
                name = card.identity.displayName,
                modifier = Modifier
                    .width(48.dp)
                    .height(67.dp),
                cornerRadius = 6,
            )
            Spacer(Modifier.width(12.dp))
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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

@Composable
private fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
}
