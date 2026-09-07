package com.riftcompanion.app.ui.screens.inventory

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
                title = { Text("Inventory (${uiState.filteredCount})") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
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
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

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
                EmptyState(
                    icon = Icons.Default.Inventory2,
                    title = if (uiState.searchQuery.isNotBlank()) "No Results" else "No Inventory",
                    subtitle = if (uiState.searchQuery.isNotBlank()) "No cards match your search."
                    else "Sync from Settings to load your cards.",
                )
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

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
            .fillMaxWidth()
            .gradientBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
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
                val count = cardCountsByLocation[location.normalizedName] ?: 0
                FilterChip(
                    selected = selectedLocation == location.normalizedName,
                    onClick = { onLocationSelected(location.normalizedName) },
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

        Button(
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
private fun InventoryGridCard(card: InventoryCardSummary, onClick: () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier.clickable(onClick = onClick),
        cornerRadius = 14,
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
private fun InventoryListRow(card: InventoryCardSummary, onClick: () -> Unit) {
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
