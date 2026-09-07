package com.riftcompanion.app.ui.screens.catalogue

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.CatalogueCardSummary
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.QuantityBadge
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.viewmodel.CardViewMode
import com.riftcompanion.app.ui.viewmodel.CatalogueViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CatalogueScreen(
    onCardClick: (String, Boolean) -> Unit,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalogue") },
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
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search every Riftbound card") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "No cards match your search."
                        else "No catalogue loaded. Sync from Settings.",
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
                        CatalogueGridCard(card = card, onClick = { onCardClick(card.id, false) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        CatalogueListRow(card = card, onClick = { onCardClick(card.id, false) })
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CatalogueGridCard(card: CatalogueCardSummary, onClick: () -> Unit) {
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                card.identity.appVisibleDomains.forEach { DomainTag(domain = it) }
                card.identity.tags.forEach { DomainTag(domain = it) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuantityBadge(title = "Owned", value = card.identity.let { 0 }) // will be from availability
                Text(
                    text = "${card.printingCount} printing${if (card.printingCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CatalogueListRow(card: CatalogueCardSummary, onClick: () -> Unit) {
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
                    text = listOfNotNull(
                        card.identity.cardType,
                        card.preferredPrinting?.expansionSlug,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${card.printingCount}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "printings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
