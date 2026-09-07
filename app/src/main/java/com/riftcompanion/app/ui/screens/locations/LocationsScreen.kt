package com.riftcompanion.app.ui.screens.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.ui.components.ThemedCardSurface
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.viewmodel.LocationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onMenuClick: () -> Unit = {},
    onLocationClick: (String) -> Unit = {},
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.gradientBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create location")
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.locations.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("No Locations", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Create one or sync from CardNexus.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Summary with icon+count chips
                item {
                    val storageCount = uiState.locations.count { it.kind == LocationKind.Storage }
                    val deckCount = uiState.locations.count { it.kind == LocationKind.Deck }
                    val unavailCount = uiState.locations.count { it.kind == LocationKind.Unavailable }
                    ThemedCardSurface(cornerRadius = 12) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                            SummaryChip(Icons.Default.Inventory2, "$storageCount", "Storage", com.riftcompanion.app.ui.theme.freeColor())
                            SummaryChip(Icons.Default.Style, "$deckCount", "Decks", MaterialTheme.colorScheme.primary)
                            SummaryChip(Icons.Default.Block, "$unavailCount", "Unavailable", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(uiState.locations, key = { it.id }) { location ->
                    LocationRow(
                        location = location,
                        cardCount = uiState.cardCountsByLocation[location.normalizedName] ?: 0,
                        onClick = { onLocationClick(location.normalizedName) },
                        onEdit = { viewModel.startEdit(location, uiState.cardCountsByLocation[location.normalizedName] ?: 0) },
                        onToggleHidden = { viewModel.toggleHidden(location) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateLocationDialog(
            onCreate = { name, color, icon ->
                viewModel.createLocation(name, color, icon)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (editState.isEditing) {
        EditLocationDialog(
            state = editState,
            onUpdateName = viewModel::updateName,
            onUpdateColor = viewModel::updateColor,
            onUpdateIcon = viewModel::updateIcon,
            onUpdateKind = viewModel::updateKind,
            onUpdateHidden = viewModel::updateHidden,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::showDeleteConfirm,
            onCancel = viewModel::cancelEdit,
        )
    }

    if (editState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            title = { Text("Delete ${editState.location?.displayName}?") },
            text = {
                Text(
                    if (editState.cardCount == 0)
                        "This will remove the location from CardNexus. This cannot be undone."
                    else
                        "This location contains ${editState.cardCount} cards. Move all cards elsewhere before deleting."
                )
            },
            confirmButton = {
                if (editState.cardCount == 0) {
                    TextButton(onClick = { viewModel.deleteLocation() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("OK") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("$count $label", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LocationRow(
    location: LocationPolicy,
    cardCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val borderColor = location.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outlineVariant
    ThemedCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 12,
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored left border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(borderColor),
            )
            Spacer(Modifier.width(12.dp))
            // Kind icon
            Icon(
                imageVector = when (location.kind) {
                    LocationKind.Storage -> Icons.Default.Inventory2
                    LocationKind.Deck -> Icons.Default.Style
                    LocationKind.Unavailable -> Icons.Default.Block
                },
                contentDescription = null,
                tint = if (location.kind == LocationKind.Storage) com.riftcompanion.app.ui.theme.freeColor()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(location.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = when (location.kind) {
                        LocationKind.Storage -> "Storage"
                        LocationKind.Deck -> "Deck"
                        LocationKind.Unavailable -> "Unavailable"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$cardCount card${if (cardCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Hidden toggle with visibility icon
            if (location.kind != LocationKind.Unavailable) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Hide", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = location.hidden, onCheckedChange = { onToggleHidden() })
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit location")
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "View", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateLocationDialog(
    onCreate: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Location") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Location name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color (e.g. blue, #FF5733)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("Icon name (optional)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onCreate(name.trim(), color.ifBlank { null }, icon.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLocationDialog(
    state: com.riftcompanion.app.ui.viewmodel.LocationEditState,
    onUpdateName: (String) -> Unit,
    onUpdateColor: (String?) -> Unit,
    onUpdateIcon: (String?) -> Unit,
    onUpdateKind: (LocationKind) -> Unit,
    onUpdateHidden: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit Location") },
        text = {
            Column {
                OutlinedTextField(value = state.name, onValueChange = { onUpdateName(it) }, label = { Text("Location name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.color ?: "", onValueChange = { onUpdateColor(it.ifBlank { null }) }, label = { Text("Color") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Type", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow {
                    LocationKind.entries.forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = state.kind == kind,
                            onClick = { onUpdateKind(kind) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = LocationKind.entries.size),
                            label = { Text(kind.title, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.hidden, onCheckedChange = { onUpdateHidden(it) })
                    Text("Hide from inventory", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text("Cards in this location: ${state.cardCount}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = state.name.isNotBlank() && !state.isSaving) { Text(if (state.isSaving) "Saving…" else "Save") } },
        dismissButton = {
            Row {
                if (state.cardCount == 0) { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

private fun parseColor(value: String): Color {
    val namedColors = mapOf(
        "black" to Color.Black, "blue" to Color.Blue, "brown" to Color(0xFF795548),
        "cyan" to Color.Cyan, "gray" to Color.Gray, "green" to Color.Green,
        "indigo" to Color(0xFF3F51B5), "mint" to Color(0xFF4DB6AC), "orange" to Color(0xFFFF9800),
        "pink" to Color(0xFFE91E63), "purple" to Color(0xFF9C27B0), "red" to Color.Red,
        "teal" to Color(0xFF009688), "white" to Color.White, "yellow" to Color.Yellow,
    )
    val lower = value.trim().lowercase()
    namedColors[lower]?.let { return it }
    val hex = if (lower.startsWith("#")) lower.drop(1) else lower
    val expanded = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
    return try {
        val num = expanded.toLong(16)
        Color(red = ((num shr 16) and 0xFF) / 255f, green = ((num shr 8) and 0xFF) / 255f, blue = (num and 0xFF) / 255f)
    } catch (_: Exception) { Color.Gray }
}

private fun Modifier.androidx_fill(color: Color): Modifier = this.then(background(color))
