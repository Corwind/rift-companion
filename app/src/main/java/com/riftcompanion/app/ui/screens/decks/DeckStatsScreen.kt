package com.riftcompanion.app.ui.screens.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.DeckStats
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.DeckViewModel

@Composable
fun DeckStatsScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    val detailState by viewModel.deckDetailState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(deckId) {
        viewModel.loadDeckDetail(deckId)
    }

    val entries = detailState.entries
    val stats = DeckStats.compute(
        entries.map { e ->
            DeckStats.EntryInfo(
                nameSlug = e.nameSlug,
                displayName = e.displayName,
                quantity = e.quantity,
                zone = e.zone,
                cardType = e.cardType,
                superType = e.superType,
                domains = e.domains,
                energyCost = e.energyCost,
                might = e.might,
                rarity = e.rarity,
                priceEur = e.priceEur,
                priceUsd = e.priceUsd,
            )
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .gradientBackground()
            .verticalScroll(rememberScrollState()),
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
                text = "Deck Stats",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        // Summary cards
        SummaryRow(stats)

        // Card type breakdown
        if (stats.cardTypeBreakdown.isNotEmpty()) {
            SectionTitle("Card Types")
            stats.cardTypeBreakdown.forEach { item ->
                BarRow(label = item.cardType, count = item.count, max = stats.mainDeckCount)
            }
        }

        // Domain breakdown
        if (stats.domainBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Domains")
            stats.domainBreakdown.forEach { item ->
                BarRow(label = item.domain, count = item.count, max = stats.mainDeckCount)
            }
        }

        // Energy curve
        if (stats.energyCurve.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Energy Curve")
            EnergyCurveChart(stats.energyCurve)
        }

        // Rarity breakdown
        if (stats.rarityBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Rarity")
            stats.rarityBreakdown.forEach { item ->
                BarRow(label = item.rarity, count = item.count, max = stats.mainDeckCount + stats.sideboardCount)
            }
        }

        // Average might
        if (stats.averageMight != null) {
            Spacer(Modifier.height(16.dp))
            StatCard(
                icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                label = "Average Might",
                value = "%.1f".format(stats.averageMight),
            )
        }

        // Deck value
        if (stats.totalValueEur != null || stats.totalValueUsd != null) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Deck Value")
            stats.totalValueEur?.let {
                StatCard(
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    label = "Cardmarket",
                    value = "€%.2f".format(it),
                )
            }
            stats.totalValueUsd?.let {
                StatCard(
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    label = "TCGplayer",
                    value = "\$%.2f".format(it),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryRow(stats: DeckStats.Stats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryChip("Main", "${stats.mainDeckCount}", Modifier.weight(1f))
        SummaryChip("Side", "${stats.sideboardCount}", Modifier.weight(1f))
        SummaryChip("Runes", "${stats.runeCount}", Modifier.weight(1f))
        SummaryChip("Unique", "${stats.uniqueCardCount}", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, value: String, modifier: Modifier = Modifier) {
    ThemedCardSurface(modifier = modifier, cornerRadius = 10) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun BarRow(label: String, count: Int, max: Int) {
    val fraction = if (max > 0) count.toFloat() / max else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text("$count", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
private fun EnergyCurveChart(curve: List<DeckStats.CostBucket>) {
    val maxCount = curve.maxOfOrNull { it.count } ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        curve.forEach { bucket ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${bucket.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((bucket.count.toFloat() / maxCount) * 80).dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${bucket.cost}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    ThemedCardSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), cornerRadius = 12) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
