package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            .background(placeholderBrush),
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
