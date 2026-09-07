package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Card surface with a solid background — no gradient tint.
 * The gradient lives on the app background; cards stay clean for readability.
 * Sets LocalContentColor so children inherit proper text/icon colors
 * matching the surface (onSurface in dark = near-white, in light = near-black).
 */
@Composable
fun ThemedCardSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 13,
    tintStrength: Float = 0.08f, // kept for API compat, ignored
    content: @Composable () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val contentColor = androidx.compose.material3.contentColorFor(containerColor)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(containerColor),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
