package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.ui.theme.domainColor

private fun Color.brightenForDark(): Color = copy(
    red = (red * 0.5f + 0.5f).coerceIn(0f, 1f),
    green = (green * 0.5f + 0.5f).coerceIn(0f, 1f),
    blue = (blue * 0.5f + 0.5f).coerceIn(0f, 1f),
)

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun Color.adaptForTheme(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) brightenForDark() else this
}

@Composable
fun DomainTag(
    domain: String,
    modifier: Modifier = Modifier,
) {
    val color = domainColor(domain).adaptForTheme()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgAlpha = if (isDark) 0.22f else 0.14f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = bgAlpha))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = domain,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
fun QuantityBadge(
    title: String,
    value: Int,
    tint: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
) {
    val adaptedTint = tint.adaptForTheme()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgAlpha = if (isDark) 0.22f else 0.16f

    Text(
        text = "$title $value",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = adaptedTint,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(adaptedTint.copy(alpha = bgAlpha))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
