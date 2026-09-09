package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.ChampionCandidate
import com.riftcompanion.app.ui.viewmodel.DeckViewModel
import com.riftcompanion.app.ui.viewmodel.LocationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckFromLocationScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
    locationsViewModel: LocationsViewModel = hiltViewModel(),
) {
    val locationsState by locationsViewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // Funnel state
    var step by remember { mutableStateOf(1) }
    var selectedLocation by remember { mutableStateOf<String?>(null) }
    var deckName by remember { mutableStateOf("") }
    var legendSlug by remember { mutableStateOf<String?>(null) }
    var legendDisplayName by remember { mutableStateOf<String?>(null) }
    var legendImageURL by remember { mutableStateOf<String?>(null) }
    var championCandidates by remember { mutableStateOf<List<ChampionCandidate>>(emptyList()) }
    var selectedChampion by remember { mutableStateOf<ChampionCandidate?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var legendSearchQuery by remember { mutableStateOf("") }
    var catalogLegends by remember { mutableStateOf<List<com.riftcompanion.app.domain.model.CatalogueCardSummary>>(emptyList()) }

    LaunchedEffect(importState.success) {
        importState.success?.let {
            viewModel.clearImportState()
            onCreated(it.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground(),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (step > 1) step-- else onBack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = when (step) {
                    1 -> "Create from Location"
                    2 -> "Select Legend"
                    3 -> "Select Champion"
                    else -> "Name Your Deck"
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        importState.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        when (step) {
            // Step 1: Pick location
            1 -> {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(locationsState.locations, key = { it.normalizedName }) { location ->
                            val isSelected = selectedLocation == location.normalizedName
                            ThemedCardSurface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedLocation = location.normalizedName },
                                cornerRadius = 12,
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(location.displayName, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            selectedLocation?.let { loc ->
                                selectedLocation = loc
                                step = 2
                                isAnalyzing = true
                                viewModel.analyzeLocationCards(loc) { legend, legendName, legendImg, champions ->
                                    legendSlug = legend
                                    legendDisplayName = legendName
                                    legendImageURL = legendImg
                                    championCandidates = champions
                                    isAnalyzing = false
                                    if (legend != null) {
                                        // Legend found in location — check champions
                                        if (champions.size == 1) {
                                            selectedChampion = champions[0]
                                            step = 4 // Skip to naming
                                        } else if (champions.isEmpty()) {
                                            step = 4 // No champions, skip
                                        } else {
                                            step = 3 // Multiple champions, pick
                                        }
                                    }
                                    // If no legend in location, stay on step 2 to pick from catalog
                                }
                            }
                        },
                        enabled = selectedLocation != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }
            }

            // Step 2: Select Legend (if not found in location)
            2 -> {
                if (isAnalyzing) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(8.dp))
                        Text("Analyzing…")
                    }
                } else if (legendSlug != null) {
                    // Legend was auto-detected from location
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CardArtwork(imageURL = legendImageURL, name = legendDisplayName ?: "", modifier = Modifier.width(44.dp).height(62.dp), cornerRadius = 6)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(legendDisplayName ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Legend (from location)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Button(onClick = { step = if (championCandidates.size == 1) { selectedChampion = championCandidates[0]; 4 } else if (championCandidates.isEmpty()) 4 else 3 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Confirm Legend")
                        }
                    }
                } else {
                    // No legend in location — pick from catalog
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = legendSearchQuery,
                            onValueChange = { legendSearchQuery = it },
                            placeholder = { Text("Search legends…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LaunchedEffect(Unit) { viewModel.getLegendCards { catalogLegends = it } }
                        val filteredLegends = if (legendSearchQuery.isBlank()) catalogLegends
                            else catalogLegends.filter { it.identity.displayName.contains(legendSearchQuery, ignoreCase = true) }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(filteredLegends, key = { it.id }) { legend ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                        legendSlug = legend.id
                                        legendDisplayName = legend.identity.displayName
                                        legendImageURL = legend.preferredImageURL
                                        // Get champion candidates for this legend
                                        viewModel.getChampionCandidatesForLegend(legend.id) { champions ->
                                            championCandidates = champions
                                            step = if (champions.size == 1) { selectedChampion = champions[0]; 4 } else if (champions.isEmpty()) 4 else 3
                                        }
                                    }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CardArtwork(imageURL = legend.preferredImageURL, name = legend.identity.displayName, modifier = Modifier.width(36.dp).height(50.dp), cornerRadius = 4)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(legend.identity.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val domains = legend.identity.appVisibleDomains
                                        if (domains.isNotEmpty()) {
                                            Text(domains.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Select Champion (if multiple candidates)
            3 -> {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Multiple champions share a tag with your legend. Pick one:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(championCandidates, key = { it.nameSlug }) { champion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedChampion = champion
                                        step = 4
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CardArtwork(imageURL = champion.imageURL, name = champion.displayName, modifier = Modifier.width(36.dp).height(50.dp), cornerRadius = 4)
                                Spacer(Modifier.width(8.dp))
                                Text(champion.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (selectedChampion?.nameSlug == champion.nameSlug) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { step = 4 },
                        enabled = selectedChampion != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }
            }

            // Step 4: Name and create
            4 -> {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Show legend + champion summary
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardArtwork(imageURL = legendImageURL, name = legendDisplayName ?: "", modifier = Modifier.width(44.dp).height(62.dp), cornerRadius = 6)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(legendDisplayName ?: "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Legend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            selectedChampion?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text("Chosen Champion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { deckName = it },
                        label = { Text("Deck name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.createDeckFromLocationWithLegend(
                                locationName = selectedLocation!!,
                                deckName = deckName,
                                legendNameSlug = legendSlug!!,
                                championNameSlug = selectedChampion?.nameSlug,
                            )
                        },
                        enabled = !importState.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (importState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Creating…")
                        } else {
                            Text("Create Deck")
                        }
                    }
                }
            }
        }
    }
}
