package com.riftcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.riftcompanion.app.ui.theme.currentThemeState

@Composable
fun CardArtwork(
    imageURL: String?,
    name: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
    isBanned: Boolean = false,
) {
    val themeState = currentThemeState()
    val placeholderBrush = Brush.linearGradient(themeState.gradientColors)
    val shape = RoundedCornerShape(cornerRadius.dp)
    val bandColor = Color(0xFFD32F2F)

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(placeholderBrush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val cardW = with(density) { maxWidth.toPx() }
        val cardH = with(density) { maxHeight.toPx() }

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

        if (isBanned) {
            // 45° diagonal band: from past-left-edge to past-top-edge.
            // Using equal x/y deltas guarantees a perfect 45° angle
            // regardless of card aspect ratio. Both ends extend past
            // the card edges so they get clipped to the rounded shape.
            val d = cardW * 0.6f       // band length along each axis
            val margin = cardW * 0.15f  // how far past each edge to extend
            val midX = d * 0.5f       // midpoint of the band
            val midY = d * 0.5f

            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = bandColor,
                    start = Offset(-margin, d + margin),
                    end = Offset(d + margin, -margin),
                    strokeWidth = cardH * 0.11f,
                    cap = StrokeCap.Butt,
                )
            }

            // Text at the band midpoint, rotated -45° to match the diagonal
            Text(
                text = "BANNED",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        rotationZ = -45f
                        translationX = midX - size.width * 0.5f
                        translationY = midY - size.height * 0.5f
                    },
            )
        }
    }
}
