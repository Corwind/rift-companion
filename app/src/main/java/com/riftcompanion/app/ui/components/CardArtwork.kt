package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Card artwork with gradient placeholder. Uses Coil's persistent disk cache
 * so card images are loaded from disk on subsequent views without network I/O.
 */
@Composable
fun CardArtwork(
    imageURL: String?,
    name: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
) {
    val themeState = currentThemeState()
    val placeholderBrush = Brush.linearGradient(themeState.gradientColors)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(placeholderBrush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerRadius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageURL != null) {
            AsyncImage(
                model = imageURL,
                contentDescription = "Artwork for $name",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "No artwork available",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
