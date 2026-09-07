package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Card surface with a solid background — no gradient tint.
 * The gradient lives on the app background; cards stay clean for readability.
 */
@Composable
fun ThemedCardSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 13,
    tintStrength: Float = 0.08f, // kept for API compat, ignored
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}
