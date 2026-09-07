package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.ui.theme.domainColor

@Composable
fun DomainTag(
    domain: String,
    modifier: Modifier = Modifier,
) {
    val color = domainColor(domain)
    Text(
        text = domain,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
fun QuantityBadge(
    title: String,
    value: Int,
    tint: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$title ${value}",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}
