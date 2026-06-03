/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nexflow.ui.flows.detail

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.flows.detail.config.ActionInfo
import com.nexflow.ui.flows.detail.config.CONTROL_FLOW_ACTIONS
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.TriggerInfo
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import java.util.UUID

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDetailScreen(
    vm: FlowDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val flow by vm.flow.collectAsState()

    if (flow == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val f = flow!!

    // --- Dialog states ---
    var showTriggerPicker by rememberSaveable { mutableStateOf(false) }
    var showActionPicker by rememberSaveable { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<PendingConfig?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (f.enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = f.enabled, onCheckedChange = { vm.setEnabled(it) })
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            vm.runNow()
                            scope.launch { snackbarHostState.showSnackbar("Flow started") }
                        },
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run flow")
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {

            // ---- TRIGGERS ----
            item {
                SectionHeader(
                    title = "WHEN",
                    trailing = if (f.triggers.size > 1) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = f.triggerLogic == TriggerLogic.ANY,
                                    onClick = { vm.setTriggerLogic(TriggerLogic.ANY) },
                                    label = { Text("ANY") },
                                )
                                FilterChip(
                                    selected = f.triggerLogic == TriggerLogic.ALL,
                                    onClick = { vm.setTriggerLogic(TriggerLogic.ALL) },
                                    label = { Text("ALL") },
                                )
                            }
                        }
                    } else null,
                )
            }

            if (f.triggers.isEmpty()) {
                item {
                    Text(
                        "No triggers yet. Add one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            items(f.triggers, key = { it.id }) { trigger ->
                val ti = trigger.type.info
                TriggerOrActionRow(
                    icon = { Icon(ti.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    headline = ti.label,
                    supporting = trigger.type.configSummary(trigger.config),
                    onEdit = { pendingConfig = PendingConfig.EditTrigger(trigger) },
                    onDelete = { vm.removeTrigger(trigger.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }

            item {
                OutlinedButton(
                    onClick = { showTriggerPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Trigger")
                }
            }

            // ---- DIVIDER ----
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ---- ACTIONS ----
            item { SectionHeader("THEN") }

            if (f.actions.isEmpty()) {
                item {
                    Text(
                        "No actions yet. Add one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            items(f.actions.sortedBy { it.order }, key = { it.id }) { action ->
                val ai = action.type.info
                TriggerOrActionRow(
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(ai.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    },
                    headline = ai.label,
                    supporting = action.type.configSummary(action.config),
                    onEdit = { pendingConfig = PendingConfig.EditAction(action) },
                    onDelete = { vm.removeAction(action.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }

            item {
                OutlinedButton(
                    onClick = { showActionPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Action")
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // --- Trigger picker ---
    if (showTriggerPicker) {
        TypePickerSheet(
            title = "Choose Trigger",
            items = TriggerType.entries.map { it to it.info },
            onSelect = { type ->
                showTriggerPicker = false
                val ti = type.info
                if (ti.fields.isEmpty()) {
                    vm.addTrigger(Trigger(UUID.randomUUID().toString(), type, emptyMap()))
                } else {
                    pendingConfig = PendingConfig.NewTrigger(type)
                }
            },
            onDismiss = { showTriggerPicker = false },
        )
    }

    // --- Action picker ---
    if (showActionPicker) {
        TypePickerSheet(
            title = "Choose Action",
            items = ActionType.entries
                .filter { it !in CONTROL_FLOW_ACTIONS }
                .map { it to it.info },
            onSelect = { type ->
                showActionPicker = false
                val ai = type.info
                if (ai.fields.isEmpty()) {
                    vm.addAction(Action(UUID.randomUUID().toString(), type, emptyMap(), f.actions.size, true))
                } else {
                    pendingConfig = PendingConfig.NewAction(type)
                }
            },
            onDismiss = { showActionPicker = false },
        )
    }

    // --- Config dialog ---
    pendingConfig?.let { cfg ->
        when (cfg) {
            is PendingConfig.NewTrigger -> ConfigDialog(
                title = cfg.type.info.label,
                fields = cfg.type.info.fields,
                initialValues = emptyMap(),
                onConfirm = { values ->
                    vm.addTrigger(Trigger(UUID.randomUUID().toString(), cfg.type, values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.EditTrigger -> ConfigDialog(
                title = cfg.trigger.type.info.label,
                fields = cfg.trigger.type.info.fields,
                initialValues = cfg.trigger.config,
                onConfirm = { values ->
                    vm.updateTrigger(cfg.trigger.copy(config = values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.NewAction -> ConfigDialog(
                title = cfg.type.info.label,
                fields = cfg.type.info.fields,
                initialValues = emptyMap(),
                onConfirm = { values ->
                    vm.addAction(Action(UUID.randomUUID().toString(), cfg.type, values, f.actions.size, true))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.EditAction -> ConfigDialog(
                title = cfg.action.type.info.label,
                fields = cfg.action.type.info.fields,
                initialValues = cfg.action.config,
                onConfirm = { values ->
                    vm.updateAction(cfg.action.copy(config = values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
        }
    }

    // --- Rename dialog ---
    if (showRenameDialog) {
        RenameDialog(
            initialName = f.name,
            initialDescription = f.description,
            onConfirm = { name, desc ->
                vm.rename(name, desc)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Sealed state for pending config dialogs
// ---------------------------------------------------------------------------

private sealed class PendingConfig {
    data class NewTrigger(val type: TriggerType) : PendingConfig()
    data class EditTrigger(val trigger: Trigger) : PendingConfig()
    data class NewAction(val type: ActionType) : PendingConfig()
    data class EditAction(val action: Action) : PendingConfig()
}

// ---------------------------------------------------------------------------
// Reusable row for a single trigger or action
// ---------------------------------------------------------------------------

@Composable
private fun TriggerOrActionRow(
    icon: @Composable () -> Unit,
    headline: String,
    supporting: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = icon,
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------
// Type picker bottom sheet (shared for triggers and actions)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> TypePickerSheet(
    title: String,
    items: List<Pair<T, Any>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items) { (type, info) ->
                val (label, icon) = when (info) {
                    is TriggerInfo -> info.label to info.icon
                    is ActionInfo -> info.label to info.icon
                    else -> "" to Icons.Default.Add
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(type) },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Generic config dialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigDialog(
    title: String,
    fields: List<ConfigField>,
    initialValues: Map<String, String>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(initialValues) {
        mutableStateMapOf<String, String>().also { it.putAll(initialValues) }
    }
    // Plain map (not state) — stores TimePickerState refs so we can read them at Save time.
    // Writing here during composition is safe: it's not a state object and we always write
    // the same remembered instance, so no recomposition is triggered.
    val timePickerStates = remember { mutableMapOf<String, TimePickerState>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (fields.isEmpty()) {
                Text("No configuration needed for this type.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    fields.forEach { field ->
                        when (field) {
                            is ConfigField.TextInput -> OutlinedTextField(
                                value = values[field.key] ?: "",
                                onValueChange = { values[field.key] = it },
                                label = { Text(field.label) },
                                placeholder = if (field.hint.isNotBlank()) {
                                    { Text(field.hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                } else null,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = if (field.multiline) 3 else 1,
                                maxLines = if (field.multiline) 5 else 1,
                            )
                            is ConfigField.Dropdown -> DropdownConfigField(
                                field = field,
                                value = values[field.key] ?: field.options.firstOrNull()?.first ?: "",
                                onValueChange = { values[field.key] = it },
                            )
                            is ConfigField.Slider -> {
                                val current = values[field.key]?.toFloatOrNull() ?: field.min.toFloat()
                                Column {
                                    Row {
                                        Text(field.label, modifier = Modifier.weight(1f))
                                        Text(
                                            "${current.toInt()}${field.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = current,
                                        onValueChange = { values[field.key] = it.toInt().toString() },
                                        valueRange = field.min.toFloat()..field.max.toFloat(),
                                        steps = 0,
                                    )
                                }
                            }
                            is ConfigField.TimePicker -> {
                                val stored = values[field.key] ?: "08:00"
                                val parts = stored.split(":").map { it.toIntOrNull() ?: 0 }
                                val timeState = rememberTimePickerState(
                                    initialHour = parts.getOrElse(0) { 8 },
                                    initialMinute = parts.getOrElse(1) { 0 },
                                    is24Hour = true,
                                )
                                // Store ref so the confirm button can read the final value.
                                // SideEffect would miss updates because TimeInput manages its
                                // own inner recomposition scope and never bubbles up to here.
                                timePickerStates[field.key] = timeState
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        field.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TimeInput(state = timeState)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Commit any TimePicker values: read state directly at click time
                // so we capture exactly what is shown, regardless of recomposition history.
                timePickerStates.forEach { (key, state) ->
                    values[key] = "${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}"
                }
                onConfirm(values.toMap())
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownConfigField(
    field: ConfigField.Dropdown,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = field.options.find { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { Text(field.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.options.forEach { (optValue, optLabel) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        onValueChange(optValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rename dialog
// ---------------------------------------------------------------------------

@Composable
private fun RenameDialog(
    initialName: String,
    initialDescription: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Flow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
