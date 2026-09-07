package com.riftcompanion.app.ui.screens.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * Lock screen shown on app launch when biometric is enabled.
 * Minimal UI, no background work — user must tap to authenticate.
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    error: String? = null,
    isAuthenticating: Boolean = false,
) {
    val themeState = currentThemeState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Biometric",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "RiftCompanion",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap to authenticate with biometrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (isAuthenticating) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onUnlock) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text("Unlock")
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
