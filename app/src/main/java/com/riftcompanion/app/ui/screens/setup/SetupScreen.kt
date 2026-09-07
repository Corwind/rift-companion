package com.riftcompanion.app.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.theme.currentThemeState

/**
 * First-time setup screen. Guides the user through:
 * 1. Enabling biometric authentication (required)
 * 2. Entering their CardNexus API key
 */
@Composable
fun SetupScreen(
    biometricAvailable: Boolean,
    onEnableBiometric: () -> Unit,
    biometricEnabled: Boolean,
    onSaveApiKey: (String, (Boolean, String?) -> Unit) -> Unit,
    onComplete: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val themeState = currentThemeState()
    val gradientBrush = Brush.linearGradient(themeState.gradientColors)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Welcome to RiftCompanion",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            "Let's set up your app in two quick steps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Step 1: Biometric
        ThemedCardSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepBadge(number = 1, gradientBrush = gradientBrush)
                    Spacer(Modifier.size(12.dp))
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Biometric Security", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "RiftCompanion requires biometric authentication to protect your CardNexus credentials. " +
                        "Your fingerprint or face will be needed each time you open the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (!biometricAvailable) {
                    Text(
                        "Biometric hardware is not available on this device. You can still proceed, but your credentials will not be biometrically protected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (biometricEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(6.dp))
                        Text("Biometric authentication enabled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Button(onClick = onEnableBiometric) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Enable Biometrics")
                    }
                }
            }
        }

        // Step 2: API Key
        ThemedCardSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepBadge(number = 2, gradientBrush = gradientBrush)
                    Spacer(Modifier.size(12.dp))
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Connect CardNexus", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Enter your CardNexus API key. Create one at cardnexus.com with inventory:read and inventory:write scopes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; error = null },
                    label = { Text("cnk_live_… key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (isVerifying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Verifying with CardNexus…")
                    }
                } else {
                    Button(
                        onClick = {
                            isVerifying = true
                            error = null
                            onSaveApiKey(apiKey) { success, err ->
                                isVerifying = false
                                if (success) {
                                    onComplete()
                                } else {
                                    error = err ?: "Verification failed"
                                }
                            }
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Verify and Save")
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Skip option
        Text(
            "You can complete setup later from Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onComplete) { Text("Skip for now") }
    }
}

@Composable
private fun StepBadge(number: Int, gradientBrush: Brush) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(gradientBrush)
            .border(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
