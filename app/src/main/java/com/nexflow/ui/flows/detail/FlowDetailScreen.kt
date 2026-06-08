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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.common.AppPickerDialog
import com.nexflow.ui.flows.detail.config.ActionInfo
import com.nexflow.ui.flows.detail.config.CONTROL_FLOW_ACTIONS
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.TriggerInfo
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import kotlinx.coroutines.launch
import java.util.UUID

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowDetailScreen(
    vm: FlowDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val flow by vm.flow.collectAsState()

    if (flow == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    val f = flow!!
    val isRunning by vm.isRunning.collectAsState()

    var showTriggerPicker by rememberSaveable { mutableStateOf(false) }
    var showActionPicker by rememberSaveable { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<PendingConfig?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val flowVariables = remember(f.actions) {
        f.actions.filter { it.type == ActionType.SET_VARIABLE }
            .mapNotNull { it.config["variable_name"]?.takeIf { n -> n.isNotBlank() } }
    }

    val lazyListState = rememberLazyListState()
    val sortedActions = remember(f.actions) { f.actions.sortedBy { it.order } }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        val cur = vm.flow.value?.actions?.sortedBy { it.order } ?: return@rememberReorderableLazyListState
        val fromIdx = cur.indexOfFirst { it.id == fromId }
        val toIdx = cur.indexOfFirst { it.id == toId }
        if (fromIdx >= 0 && toIdx >= 0) vm.reorderActions(fromIdx, toIdx)
    }

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
                    IconButton(onClick = {
                        val json = vm.exportAsJson() ?: return@IconButton
                        val dir = File(context.cacheDir, "flow_exports").also { it.mkdirs() }
                        // Preserve flow name; only strip characters forbidden in filenames.
                        val safeName = f.name
                            .replace(Regex("""[/\\:*?"<>|]"""), "_")
                            .trim()
                            .ifBlank { "flow" }
                        val file = File(dir, "$safeName.flow")
                        file.writeText(json)
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "$safeName.flow")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export flow"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FlowControls(
                enabled = f.enabled,
                isRunning = isRunning,
                onToggle = { vm.setEnabled(it) },
                onRun = {
                    vm.runNow()
                    scope.launch { snackbarHostState.showSnackbar("Flow started") }
                },
                onStop = {
                    vm.cancelRun()
                    scope.launch { snackbarHostState.showSnackbar("Flow stopped") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {

            // ---- TRIGGERS ----
            item {
                SectionHeader(
                    title = "WHEN",
                    trailing = if (f.triggers.size > 1) {
                        {
                            TriggerLogicToggle(
                                logic = f.triggerLogic,
                                onSelect = { vm.setTriggerLogic(it) },
                            )
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

            items(sortedActions, key = { it.id }) { action ->
                val haptic = LocalHapticFeedback.current
                ReorderableItem(reorderState, key = action.id) { _ ->
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
                        showDragHandle = true,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
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

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showTriggerPicker) {
        val alreadyHasManual = f.triggers.any { it.type == TriggerType.MANUAL }
        TypePickerSheet(
            title = "Choose Trigger",
            items = TriggerType.entries
                .filter { !(it == TriggerType.MANUAL && alreadyHasManual) }
                .map { it to it.info },
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

    pendingConfig?.let { cfg ->
        when (cfg) {
            is PendingConfig.NewTrigger -> ConfigDialog(
                title = cfg.type.info.label,
                fields = cfg.type.info.fields,
                initialValues = emptyMap(),
                availableVariables = flowVariables,
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
                availableVariables = flowVariables,
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
                availableVariables = flowVariables,
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
                availableVariables = flowVariables,
                onConfirm = { values ->
                    vm.updateAction(cfg.action.copy(config = values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
        }
    }

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

@Composable
private fun FlowControls(
    enabled: Boolean,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallFloatingActionButton(
            onClick = { onToggle(!enabled) },
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                contentDescription = if (enabled) "Automation enabled" else "Automation disabled",
            )
        }
        FloatingActionButton(
            onClick = if (isRunning) onStop else onRun,
            containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isRunning) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            AnimatedContent(
                targetState = isRunning,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "play_stop",
            ) { running ->
                Icon(
                    imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Stop flow" else "Run flow",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// M3 Expressive ButtonGroup — ANY / ALL trigger logic toggle (E3)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerLogicToggle(
    logic: TriggerLogic,
    onSelect: (TriggerLogic) -> Unit,
) {
    ButtonGroup(overflowIndicator = {}) {
        toggleableItem(
            checked = logic == TriggerLogic.ANY,
            onCheckedChange = { onSelect(TriggerLogic.ANY) },
            label = "ANY",
        )
        toggleableItem(
            checked = logic == TriggerLogic.ALL,
            onCheckedChange = { onSelect(TriggerLogic.ALL) },
            label = "ALL",
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
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = icon,
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDragHandle) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = "Reorder",
                        modifier = dragHandleModifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
// Type picker bottom sheet — breakpoint-aware grid columns (S3)
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val columns = when {
        screenWidthDp >= 840 -> 5
        screenWidthDp >= 600 -> 4
        else -> 3
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
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
                            textAlign = TextAlign.Center,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConfigDialog(
    title: String,
    fields: List<ConfigField>,
    initialValues: Map<String, String>,
    availableVariables: List<String>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val values = remember(initialValues) {
        mutableStateMapOf<String, String>().also { it.putAll(initialValues) }
    }
    val timePickerStates = remember { mutableMapOf<String, TimePickerState>() }
    val timePickerInputModes = remember { mutableStateMapOf<String, Boolean>() }

    val firstTextInputKey = remember(fields) {
        fields.filterIsInstance<ConfigField.TextInput>().firstOrNull()?.key
    }
    val firstFieldFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (firstTextInputKey != null) firstFieldFocusRequester.requestFocus()
    }

    var appPickerKey by remember { mutableStateOf<String?>(null) }

    appPickerKey?.let { key ->
        AppPickerDialog(
            onSelect = { pkg ->
                values[key] = pkg
                appPickerKey = null
            },
            onDismiss = { appPickerKey = null },
        )
    }

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
                            is ConfigField.TextInput -> {
                                OutlinedTextField(
                                    value = values[field.key] ?: "",
                                    onValueChange = { values[field.key] = it },
                                    label = { Text(field.label) },
                                    placeholder = if (field.hint.isNotBlank()) {
                                        { Text(field.hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    } else null,
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = if (field.multiline) ImeAction.Default else ImeAction.Next,
                                    ),
                                    modifier = Modifier.fillMaxWidth().let {
                                        if (field.key == firstTextInputKey) it.focusRequester(firstFieldFocusRequester) else it
                                    },
                                    minLines = if (field.multiline) 3 else 1,
                                    maxLines = if (field.multiline) 5 else 1,
                                )
                                if (availableVariables.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Code,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        availableVariables.forEach { varName ->
                                            AssistChip(
                                                onClick = {
                                                    val cur = values[field.key] ?: ""
                                                    values[field.key] = cur + "{{$varName}}"
                                                },
                                                label = {
                                                    Text(
                                                        varName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

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
                                timePickerStates[field.key] = timeState
                                val isInputMode = timePickerInputModes[field.key] ?: false
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
                                    if (isInputMode) {
                                        TimeInput(state = timeState)
                                    } else {
                                        TimePicker(state = timeState)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                    ) {
                                        IconButton(
                                            onClick = { timePickerInputModes[field.key] = !isInputMode },
                                        ) {
                                            Icon(
                                                if (isInputMode) Icons.Outlined.AccessTime
                                                else Icons.Outlined.Keyboard,
                                                contentDescription = if (isInputMode) "切換為撥盤" else "切換為鍵盤",
                                            )
                                        }
                                    }
                                }
                            }

                            is ConfigField.AppPicker -> {
                                val pkg = values[field.key] ?: ""
                                val appLabel = remember(pkg) {
                                    if (pkg.isBlank()) null
                                    else runCatching {
                                        context.packageManager
                                            .getApplicationLabel(
                                                context.packageManager.getApplicationInfo(pkg, 0),
                                            ).toString()
                                    }.getOrNull()
                                }
                                OutlinedButton(
                                    onClick = { appPickerKey = field.key },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (appLabel != null) "$appLabel  ($pkg)"
                                        else if (pkg.isNotBlank()) pkg
                                        else "Choose app…",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            is ConfigField.DayPicker -> {
                                if (field.showWhenKey != null &&
                                    values[field.showWhenKey] != field.showWhenValue
                                ) return@forEach

                                val days = listOf(
                                    "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed",
                                    "THU" to "Thu", "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun",
                                )
                                val selectedDays = remember {
                                    mutableStateOf(
                                        (values[field.key] ?: "")
                                            .split(",")
                                            .filter { it.isNotBlank() }
                                            .toMutableSet(),
                                    )
                                }
                                Column {
                                    Text(
                                        field.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        days.forEach { (id, label) ->
                                            FilterChip(
                                                selected = id in selectedDays.value,
                                                onClick = {
                                                    val updated = selectedDays.value.toMutableSet()
                                                    if (id in updated) updated.remove(id) else updated.add(id)
                                                    selectedDays.value = updated
                                                    values[field.key] = updated.joinToString(",")
                                                },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            )
                                        }
                                    }
                                }
                            }

                            is ConfigField.WifiSsidInput -> {
                                @Suppress("DEPRECATION")
                                val currentSsid = remember {
                                    runCatching {
                                        val wm = context.applicationContext
                                            .getSystemService(Context.WIFI_SERVICE) as WifiManager
                                        wm.connectionInfo?.ssid
                                            ?.removePrefix("\"")?.removeSuffix("\"")
                                            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                                    }.getOrNull()
                                }
                                Column {
                                    OutlinedTextField(
                                        value = values[field.key] ?: "",
                                        onValueChange = { values[field.key] = it },
                                        label = { Text(field.label) },
                                        placeholder = { Text("Leave blank for any network") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (currentSsid != null) {
                                        Spacer(Modifier.height(4.dp))
                                        AssistChip(
                                            onClick = { values[field.key] = currentSsid },
                                            label = { Text("Use current: $currentSsid") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Wifi, null, Modifier.size(16.dp))
                                            },
                                        )
                                    }
                                }
                            }

                            is ConfigField.NfcTagScan -> {
                                val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
                                var scanning by remember { mutableStateOf(false) }
                                val activity = context as? Activity

                                DisposableEffect(scanning) {
                                    var readerModeEnabled = false
                                    if (scanning && nfcAdapter != null && activity != null) {
                                        nfcAdapter.enableReaderMode(
                                            activity,
                                            { tag ->
                                                val id = tag.id.joinToString("") { "%02X".format(it) }
                                                values[field.key] = id
                                                scanning = false
                                            },
                                            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                                                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                                                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                                            null,
                                        )
                                        readerModeEnabled = true
                                    }
                                    onDispose {
                                        // Only disable reader mode if this effect actually enabled it.
                                        // Unconditional disable would kill MainActivity's onResume reader mode.
                                        if (readerModeEnabled) {
                                            runCatching {
                                                if (activity != null) nfcAdapter?.disableReaderMode(activity)
                                            }
                                        }
                                    }
                                }

                                Column {
                                    OutlinedTextField(
                                        value = values[field.key] ?: "",
                                        onValueChange = { values[field.key] = it },
                                        label = { Text(field.label) },
                                        placeholder = { Text("Leave blank for any tag") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = scanning,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    when {
                                        nfcAdapter == null -> Text(
                                            "NFC is not available on this device",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        !nfcAdapter.isEnabled -> Text(
                                            "NFC is disabled — enable it in device settings",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        scanning -> Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            LoadingIndicator(modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Hold device near NFC tag…",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            TextButton(onClick = { scanning = false }) { Text("Cancel") }
                                        }
                                        else -> OutlinedButton(
                                            onClick = { scanning = true },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Outlined.Nfc, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if ((values[field.key] ?: "").isBlank()) "Scan NFC tag" else "Rescan tag")
                                        }
                                    }
                                }
                            }

                            is ConfigField.CurrentLocationButton -> {
                                var locationError by remember { mutableStateOf<String?>(null) }
                                val locationLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.RequestPermission(),
                                ) { granted ->
                                    if (granted) {
                                        fillLocation(context, values, field.latKey, field.lngKey) {
                                            locationError = it
                                        }
                                    } else {
                                        locationError = "Location permission denied"
                                    }
                                }
                                Column {
                                    OutlinedButton(
                                        onClick = {
                                            locationError = null
                                            if (ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                fillLocation(context, values, field.latKey, field.lngKey) {
                                                    locationError = it
                                                }
                                            } else {
                                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.MyLocation, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(field.label)
                                    }
                                    locationError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            is ConfigField.InfoText -> {
                                Surface(
                                    color = if (field.isWarning) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        if (field.isWarning) {
                                            Icon(
                                                Icons.Outlined.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                        Text(
                                            field.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (field.isWarning) MaterialTheme.colorScheme.onErrorContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            is ConfigField.Toggle -> {
                                val checked = values[field.key] == "true"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(field.label)
                                        if (field.description.isNotBlank()) {
                                            Text(
                                                field.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { values[field.key] = it.toString() },
                                    )
                                }
                            }

                            is ConfigField.UnitSlider -> {
                                val selectedUnitId = values[field.unitKey]
                                    ?: field.units.firstOrNull()?.id ?: ""
                                val unitDef = field.units.find { it.id == selectedUnitId }
                                    ?: field.units.first()
                                val current = (values[field.key]?.toFloatOrNull() ?: unitDef.min.toFloat())
                                    .coerceIn(unitDef.min.toFloat(), unitDef.max.toFloat())

                                var inputText by remember(field.key) { mutableStateOf(current.toInt().toString()) }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(field.label, modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = inputText,
                                            onValueChange = { text ->
                                                inputText = text
                                                text.toIntOrNull()
                                                    ?.coerceIn(unitDef.min, unitDef.max)
                                                    ?.let { v -> values[field.key] = v.toString() }
                                            },
                                            suffix = if (unitDef.suffix.isNotBlank()) {
                                                { Text(unitDef.suffix) }
                                            } else null,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(96.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        field.units.forEachIndexed { idx, udef ->
                                            SegmentedButton(
                                                selected = selectedUnitId == udef.id,
                                                onClick = {
                                                    values[field.unitKey] = udef.id
                                                    val minVal = udef.min.toString()
                                                    values[field.key] = minVal
                                                    inputText = minVal
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(idx, field.units.size),
                                            ) { Text(udef.displayLabel) }
                                        }
                                    }
                                    Slider(
                                        value = current,
                                        onValueChange = {
                                            val intVal = it.toInt().toString()
                                            values[field.key] = intVal
                                            inputText = intVal
                                        },
                                        valueRange = unitDef.min.toFloat()..unitDef.max.toFloat(),
                                        steps = 0,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
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

@SuppressLint("MissingPermission")
private fun fillLocation(
    context: Context,
    values: MutableMap<String, String>,
    latKey: String,
    lngKey: String,
    onError: (String) -> Unit,
) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    if (loc != null) {
        values[latKey] = "%.6f".format(loc.latitude)
        values[lngKey] = "%.6f".format(loc.longitude)
    } else {
        onError("Location not available — ensure GPS or network location is on")
    }
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
