package com.riftcompanion.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.ui.components.DataWarningDialog
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.theme.AppAccentPalette
import com.riftcompanion.app.ui.theme.AppAppearance
import com.riftcompanion.app.ui.theme.currentThemeState
import com.riftcompanion.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeState = currentThemeState()
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
        modifier = Modifier.gradientBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
            )
        },
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
                // Appearance mode
                Text("Appearance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
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

                Spacer(Modifier.height(20.dp))

                // Primary color
                Text("Primary color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppAccentPalette.allEntries.forEach { palette ->
                        ColorSwatchChip(
                            label = palette.title,
                            color = if (themeState.isDark) palette.colors.dark else palette.colors.light,
                            selected = uiState.accent == palette,
                            onClick = { viewModel.setAccent(palette) },
                        )
                    }
                }

                // Secondary color
                if (uiState.accent.supportsCombination) {
                    Spacer(Modifier.height(20.dp))
                    Text("Second color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorSwatchChip(
                            label = "Single color",
                            color = if (themeState.isDark) uiState.accent.colors.dark else uiState.accent.colors.light,
                            selected = uiState.secondaryAccent == null,
                            onClick = { viewModel.setSecondaryAccent(null) },
                            isSingleColor = true,
                        )
                        AppAccentPalette.allEntries
                            .filter { it.supportsCombination && it != uiState.accent }
                            .forEach { palette ->
                                ColorSwatchChip(
                                    label = palette.title,
                                    color = if (themeState.isDark) palette.colors.dark else palette.colors.light,
                                    selected = uiState.secondaryAccent == palette,
                                    onClick = { viewModel.setSecondaryAccent(palette) },
                                )
                            }
                    }

                    // Gradient preview bar
                    Spacer(Modifier.height(12.dp))
                    val gradientColors = themeState.gradientColors
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(gradientColors))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                            ),
                    )
                }
            }

            // ── CardNexus Account ────────────────────────────────────────
            SettingsCard("CardNexus Account") {
                if (uiState.hasApiKey && !showApiKeyField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF43A047))
                        Spacer(Modifier.size(8.dp))
                        Text("API key stored", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showApiKeyField = true; apiKey = "" }) { Text("Replace") }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = { showDeleteConfirm = true }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your API key is stored encrypted on this device. Biometric authentication is required to access it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Paste your cnk_live_… key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
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
                                Spacer(Modifier.size(8.dp))
                                TextButton(onClick = { showApiKeyField = false; apiKey = "" }) { Text("Cancel") }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Create a CardNexus key with inventory:read and inventory:write access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            Spacer(Modifier.size(8.dp))
                            Text("Synchronize Now")
                        }
                    }
                }
                uiState.lastSyncTimestamp?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Last sync: ${java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.isMeteredConnection && !uiState.dataWarningAcked) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
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
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric lock", style = MaterialTheme.typography.bodyMedium)
                        Text("Require biometric authentication to access the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * A color swatch chip: a colored circle next to a label, with a selection border.
 */
@Composable
private fun ColorSwatchChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    isSingleColor: Boolean = false,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val borderWidth = if (selected) 2.dp else 1.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (isSingleColor) {
            // Show a "no gradient" icon — a circle with a slash
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
