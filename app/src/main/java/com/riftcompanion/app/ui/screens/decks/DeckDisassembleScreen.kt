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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.CardMovement
import com.riftcompanion.app.ui.viewmodel.DeckViewModel
import com.riftcompanion.app.ui.viewmodel.DisassembleCardInfo

@Composable
fun DeckDisassembleScreen(
    deckId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) {
        viewModel.previewDisassemble(deckId)
    }

    val disassembleState by viewModel.disassembleState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "Disassemble Deck",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            disassembleState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading cards…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            disassembleState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(disassembleState.error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            disassembleState.isDone -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Deck disassembled!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDone) { Text("Done") }
                    }
                }
            }
            disassembleState.cards.isNotEmpty() -> {
                DisassembleContent(
                    cards = disassembleState.cards,
                    availableLocations = disassembleState.availableLocations,
                    deckLocationName = disassembleState.deckLocationName,
                    isLoading = disassembleState.isExecuting,
                    onConfirm = { overrides ->
                        viewModel.executeDisassemble(deckId, overrides)
                    },
                    onBack = onBack,
                )
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.Unarchive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No cards at the deck location", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisassembleContent(
    cards: List<DisassembleCardInfo>,
    availableLocations: List<LocationPolicy>,
    deckLocationName: String,
    isLoading: Boolean,
    onConfirm: (Map<String, String>) -> Unit,
    onBack: () -> Unit,
) {
    // Default destination: first storage location, or first non-deck location
    val defaultLocation = remember(availableLocations) {
        availableLocations.firstOrNull { it.name != deckLocationName }
            ?: availableLocations.firstOrNull()
    }
    var defaultDest by remember { mutableStateOf(defaultLocation?.name ?: "") }

    // Per-card overrides: nameSlug → destination location name
    val overrides = remember { mutableStateMapOf<String, String>() }

    fun destFor(card: DisassembleCardInfo): String {
        return overrides[card.nameSlug] ?: defaultDest
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Default destination selector
            item {
                ThemedCardSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 12) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default destination",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LocationDropdown(
                                selected = defaultDest,
                                locations = availableLocations.filter { it.name != deckLocationName },
                                onSelect = { defaultDest = it; overrides.clear() },
                            )
                        }
                    }
                }
            }

            // Card list
            items(cards) { card ->
                DisassembleCardRow(
                    card = card,
                    currentDest = destFor(card),
                    availableLocations = availableLocations.filter { it.name != deckLocationName },
                    onOverride = { loc -> overrides[card.nameSlug] = loc },
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
            ) { Text("Cancel") }
            Button(
                onClick = { onConfirm(overrides.toMap()) },
                modifier = Modifier.weight(2f),
                enabled = !isLoading && defaultDest.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Disassemble")
                }
            }
        }
    }
}

@Composable
private fun DisassembleCardRow(
    card: DisassembleCardInfo,
    currentDest: String,
    availableLocations: List<LocationPolicy>,
    onOverride: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }

    ThemedCardSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 10) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${card.quantity}× ${card.displayName}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = card.fromLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(start = 4.dp))
                    Box {
                        Text(
                            text = availableLocations.find { it.name == currentDest }?.displayName ?: currentDest.ifBlank { "Select…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showDropdown = true },
                        )
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                            availableLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc.displayName) },
                                    onClick = {
                                        onOverride(loc.name)
                                        showDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationDropdown(
    selected: String,
    locations: List<LocationPolicy>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplay = locations.find { it.name == selected }?.displayName ?: selected.ifBlank { "Select…" }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(loc.displayName) },
                    onClick = {
                        onSelect(loc.name)
                        expanded = false
                    },
                )
            }
        }
    }
}
