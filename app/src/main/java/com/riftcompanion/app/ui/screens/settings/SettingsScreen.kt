package com.riftcompanion.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width as layoutWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.ui.components.DataWarningDialog
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.theme.AppAccentPalette
import com.riftcompanion.app.ui.theme.AppAppearance
import com.riftcompanion.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showApiKeyField by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }
    LaunchedEffect(uiState.syncError) {
        uiState.syncError?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Appearance ──────────────────────────────────────────────
            SettingsCard("Appearance") {
                Text("Appearance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                SingleChoiceSegmentedButtonRow {
                    AppAppearance.entries.forEachIndexed { index, appearance ->
                        SegmentedButton(
                            selected = uiState.appearance == appearance,
                            onClick = { viewModel.setAppearance(appearance) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AppAppearance.entries.size),
                            label = { Text(appearance.title) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Primary color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppAccentPalette.allEntries.forEach { palette ->
                        val color = if (uiState.appearance == AppAppearance.Dark) palette.colors.dark else palette.colors.light
                        OutlinedButton(
                            onClick = { viewModel.setAccent(palette) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (uiState.accent == palette) color.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .height(16.dp)
                                        .width(16.dp)
                                        .androidx_background(color, androidx.compose.foundation.shape.CircleShape),
                                )
                                Text(palette.title, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (uiState.accent.supportsCombination) {
                    Spacer(Modifier.height(8.dp))
                    Text("Second color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.setSecondaryAccent(null) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (uiState.secondaryAccent == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        ) { Text("Single", style = MaterialTheme.typography.labelSmall) }
                        AppAccentPalette.allEntries
                            .filter { it.supportsCombination && it != uiState.accent }
                            .forEach { palette ->
                                val color = if (uiState.appearance == AppAppearance.Dark) palette.colors.dark else palette.colors.light
                                OutlinedButton(
                                    onClick = { viewModel.setSecondaryAccent(palette) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (uiState.secondaryAccent == palette) color.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .height(16.dp)
                                            .width(16.dp)
                                            .androidx_background(color, androidx.compose.foundation.shape.CircleShape),
                                    )
                                    Text(palette.title, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Transparency: ${if (uiState.backgroundTransparency <= 0.001f) "Matte" else "Frosted"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Slider(
                    value = uiState.backgroundTransparency,
                    onValueChange = { viewModel.setBackgroundTransparency(it) },
                    valueRange = 0f..1f,
                )
            }

            // ── CardNexus Account ────────────────────────────────────────
            SettingsCard("CardNexus Account") {
                if (uiState.hasApiKey && !showApiKeyField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF43A047))
                        Spacer(Modifier.height(8.dp))
                        Text("API key stored", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.fillMaxWidth().weight(1f))
                        OutlinedButton(onClick = { showApiKeyField = true; apiKey = "" }) { Text("Replace") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showDeleteConfirm = true }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    Text(
                        "Your API key is stored encrypted on this device. Biometric authentication is required to access it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    Column {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Paste your cnk_live_… key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    viewModel.saveApiKey(apiKey) { success, error ->
                                        if (success) {
                                            showApiKeyField = false
                                            apiKey = ""
                                        }
                                    }
                                    apiKey = ""
                                },
                                enabled = apiKey.isNotBlank(),
                            ) { Text("Verify and Save") }
                            if (showApiKeyField) {
                                TextButton(onClick = { showApiKeyField = false; apiKey = "" }) { Text("Cancel") }
                            }
                        }
                        Text(
                            "Create a CardNexus key with inventory:read and inventory:write access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // ── Synchronization ─────────────────────────────────────────
            SettingsCard("Synchronization") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("Synchronizing…")
                    } else {
                        Button(
                            onClick = { viewModel.synchronize() },
                            enabled = uiState.hasApiKey && !uiState.isSyncing,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text("Synchronize Now")
                        }
                    }
                }
                uiState.lastSyncTimestamp?.let {
                    Text(
                        "Last sync: ${java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                if (uiState.isMeteredConnection && !uiState.dataWarningAcked) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You are on a metered connection. Syncing will consume data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── Security ─────────────────────────────────────────────────
            SettingsCard("Security") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric lock", style = MaterialTheme.typography.bodyMedium)
                        Text("Require biometric authentication to access the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = uiState.biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                    )
                }
            }
        }
    }

    // Data warning dialog
    if (uiState.showDataWarning) {
        DataWarningDialog(
            onAccept = { dontShowAgain -> viewModel.acceptDataWarning(dontShowAgain) },
            onDismiss = { viewModel.dismissDataWarning() },
        )
    }

    // Delete API key confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove API Key") },
            text = { Text("Cached cards will remain on this device. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApiKey()
                    showDeleteConfirm = false
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    ThemedCardSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18,
        tintStrength = 0.075f,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// Helper extension for background
private fun Modifier.androidx_background(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(background(color, shape))

private fun Modifier.width(dp: androidx.compose.ui.unit.Dp): Modifier =
    this.then(layoutWidth(dp))
