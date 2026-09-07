package com.riftcompanion.app.ui.screens.carddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.firstDisplayValue
import com.riftcompanion.app.domain.model.firstText
import com.riftcompanion.app.ui.components.CardArtwork
import com.riftcompanion.app.ui.components.DomainTag
import com.riftcompanion.app.ui.components.QuantityBadge
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.viewmodel.CardDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(
    cardNameSlug: String,
    isFromInventory: Boolean,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(cardNameSlug) {
        viewModel.loadCard(cardNameSlug, isFromInventory)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val card = uiState.card
        if (card == null) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val identity = card.identity
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            ) {
                // Header: artwork + name + type
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardArtwork(
                        imageURL = card.imageURL,
                        name = identity.displayName,
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(5f / 7f),
                        cornerRadius = 14,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = identity.displayName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val typeLine = listOfNotNull(identity.superType, identity.cardType)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · ")
                        if (typeLine.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(typeLine, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (identity.appVisibleDomains.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Domains", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                identity.appVisibleDomains.forEach { DomainTag(domain = it) }
                                identity.tags.forEach { DomainTag(domain = it) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Stats
                val isLegend = identity.cardType?.equals("Legend", ignoreCase = true) == true
                if (!isLegend) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        identity.energyCost?.let { StatCard("Energy Cost", it.toString()) }
                        identity.mightCost?.let { StatCard("Might", it.toString()) }
                        identity.attributes.firstDisplayValue(listOf("power", "attack", "strength"))?.let { StatCard("Power", it) }
                        identity.attributes.firstDisplayValue(listOf("health", "hp"))?.let { StatCard("Health", it) }
                        identity.attributes.firstDisplayValue(listOf("durability"))?.let { StatCard("Durability", it) }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Rules text
                identity.attributes.firstText(listOf("rulesText", "rules_text", "rules", "effectText", "effect_text", "effect", "abilityText", "ability_text", "text"))?.let { rules ->
                    DetailSection("Rules") {
                        Text(rules, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Flavor text
                identity.attributes.firstText(listOf("flavorText", "flavor_text", "flavourText", "flavour_text"))?.let { flavor ->
                    Text(flavor, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                }

                // Metadata
                DetailSection("Printing") {
                    MetadataRow("Set", card.expansionSlugs.joinToString(", "))
                    MetadataRow("Rarity", card.rarities.joinToString(", "))
                    MetadataRow("Riot ID", identity.attributes.firstDisplayValue(listOf("riotId", "riot_id")))
                    MetadataRow("Card number", card.preferredPrinting?.printNumber)
                    MetadataRow("Printing", card.preferredPrinting?.printingSlug)
                    MetadataRow("Finish", card.finish)
                    MetadataRow("Language", card.language?.uppercase())
                    card.printingCount?.let { MetadataRow("Known printings", it.toString()) }
                }
                Spacer(Modifier.height(16.dp))

                // Availability
                card.availability?.let { avail ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuantityBadge(title = "Total", value = avail.totalOwned)
                        QuantityBadge(title = "Free", value = avail.availableInStorage, tint = androidx.compose.ui.graphics.Color(0xFF43A047))
                        val used = avail.inTargetDeck + avail.inOtherDecks
                        if (used > 0) QuantityBadge(title = "Used", value = used, tint = androidx.compose.ui.graphics.Color(0xFFFB8C00))
                        if (avail.otherwiseUnavailable > 0) QuantityBadge(title = "Unavailable", value = avail.otherwiseUnavailable, tint = androidx.compose.ui.graphics.Color(0xFFFB8C00))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Locations
                if (card.locations.isNotEmpty()) {
                    DetailSection("Locations") {
                        card.locations.forEach { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = location.displayName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${location.quantity}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
private fun StatCard(title: String, value: String) {
    ThemedCardSurface(cornerRadius = 8, tintStrength = 0.06f) {
        Column(modifier = Modifier.padding(9.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    ThemedCardSurface(cornerRadius = 10, tintStrength = 0.04f) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetadataRow(title: String, value: String?) {
    if (value != null && value.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
