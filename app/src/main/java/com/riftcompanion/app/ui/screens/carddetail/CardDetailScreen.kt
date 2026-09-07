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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
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
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.CardDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardDetailScreen(
    cardNameSlug: String,
    isFromInventory: Boolean,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(cardNameSlug) { viewModel.loadCard(cardNameSlug, isFromInventory) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.gradientBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Card Details") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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
                // Hero header
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardArtwork(
                        imageURL = card.imageURL,
                        name = identity.displayName,
                        modifier = Modifier.width(160.dp).aspectRatio(5f / 7f),
                        cornerRadius = 14,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = identity.displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val typeLine = listOfNotNull(identity.superType, identity.cardType)
                            .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                        if (typeLine.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(typeLine, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (identity.appVisibleDomains.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Domains", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                identity.appVisibleDomains.forEach { DomainTag(domain = it) }
                            }
                        }
                        if (identity.tags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Tags", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                identity.tags.forEach { tag ->
                                    DomainTag(domain = tag)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Stats
                val isLegend = identity.cardType?.equals("Legend", ignoreCase = true) == true
                val isUnit = identity.cardType?.equals("Unit", ignoreCase = true) == true
                val isSpell = identity.cardType?.equals("Spell", ignoreCase = true) == true
                if (!isLegend) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Energy is a cost for all non-legend cards
                        identity.energyCost?.let { StatCard("Energy", it.toString(), Icons.Default.Bolt) }
                        // Might is a strength stat for units only, spells don't have it
                        if (isUnit) {
                            identity.mightCost?.let { StatCard("Might", it.toString(), Icons.Default.FitnessCenter) }
                        }
                        identity.attributes.firstDisplayValue(listOf("power", "attack", "strength"))?.let { StatCard("Power", it, Icons.Default.Shield) }
                        identity.attributes.firstDisplayValue(listOf("health", "hp"))?.let { StatCard("Health", it, Icons.Default.Shield) }
                        identity.attributes.firstDisplayValue(listOf("durability"))?.let { StatCard("Durability", it, Icons.Default.Shield) }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Rules text
                identity.attributes.firstText(listOf("rulesText", "rules_text", "rules", "effectText", "effect_text", "effect", "abilityText", "ability_text", "text"))?.let { rules ->
                    DetailSection("Rules", Icons.Default.Description) {
                        Text(rules, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Flavor text
                identity.attributes.firstText(listOf("flavorText", "flavor_text", "flavourText", "flavour_text"))?.let { flavor ->
                    Text(flavor, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                }

                // Metadata
                DetailSection("Printing Info", Icons.Default.Info) {
                    MetadataRow("Set", card.expansionSlugs.joinToString(", "))
                    MetadataRow("Rarity", card.rarities.joinToString(", "))
                    MetadataRow("Riot ID", identity.attributes.firstDisplayValue(listOf("riotId", "riot_id")))
                    MetadataRow("Card number", card.preferredPrinting?.printNumber)
                    MetadataRow("Printing", card.preferredPrinting?.printingSlug)
                    MetadataRow("Finish", card.finish)
                    MetadataRow("Language", card.language?.uppercase())
                    card.printingCount?.let { MetadataRow("Known printings", it.toString()) }
                }
                Spacer(Modifier.height(20.dp))

                // Availability
                card.availability?.let { avail ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuantityBadge(title = "Total", value = avail.totalOwned)
                        QuantityBadge(title = "Free", value = avail.availableInStorage, tint = com.riftcompanion.app.ui.theme.freeColor())
                        val used = avail.inTargetDeck + avail.inOtherDecks
                        if (used > 0) QuantityBadge(title = "Used", value = used, tint = com.riftcompanion.app.ui.theme.usedColor())
                        if (avail.otherwiseUnavailable > 0) QuantityBadge(title = "Unavailable", value = avail.otherwiseUnavailable, tint = com.riftcompanion.app.ui.theme.usedColor())
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Locations
                if (card.locations.isNotEmpty()) {
                    DetailSection("Locations", Icons.Default.LocationOn) {
                        card.locations.forEach { location ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = when (location.kind) {
                                        "storage" -> Icons.Default.LocationOn
                                        "deck" -> Icons.Default.MenuBook
                                        else -> Icons.Default.Block
                                    },
                                    contentDescription = null,
                                    tint = if (location.isAvailable) com.riftcompanion.app.ui.theme.freeColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(location.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${location.quantity}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ThemedCardSurface(cornerRadius = 10) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DetailSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    ThemedCardSurface(cornerRadius = 12) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MetadataRow(title: String, value: String?) {
    if (value != null && value.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
