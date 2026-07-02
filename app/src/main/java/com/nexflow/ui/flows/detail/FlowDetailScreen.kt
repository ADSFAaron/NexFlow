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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.nexflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.core.automation.model.Action
import com.nexflow.FlavorFeatures
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.common.AppPickerDialog
import com.nexflow.ui.common.FlowIconPickerDialog
import com.nexflow.ui.common.FlowIcons
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import androidx.compose.ui.graphics.Color
import com.nexflow.ui.flows.detail.config.ActionInfo
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.TriggerInfo
import com.nexflow.ui.flows.detail.config.category
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val currentActionId by vm.currentActionId.collectAsState()

    var showTriggerPicker by rememberSaveable { mutableStateOf(false) }
    var showActionPicker by rememberSaveable { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<PendingConfig?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    // null = closed; Variable with blank name = creating a new one
    var editingVariable by remember { mutableStateOf<Variable?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val flowVariables = remember(f.actions, f.variables) {
        (
            f.variables.map { it.name } +
                f.actions.filter { it.type == ActionType.SET_VARIABLE }
                    .mapNotNull { it.config["variable_name"]?.takeIf { n -> n.isNotBlank() } }
            ).distinct()
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

    val onShare = share@{
        val json = vm.exportAsJson() ?: return@share
        val dir = File(context.cacheDir, "flow_exports").also { it.mkdirs() }
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
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.fd_export_flow)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(34.dp)
                                .background(
                                    FlowIcons.color(f.iconColor) ?: MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                )
                                .clickable { showIconPicker = true },
                        ) {
                            Icon(
                                FlowIcons.vector(f.icon),
                                contentDescription = stringResource(R.string.fd_change_icon),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.fd_edit_flow))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FlowControlsBar(
                enabled = f.enabled,
                isRunning = isRunning,
                onToggle = { vm.setEnabled(it) },
                onShare = onShare,
                onRun = {
                    vm.runNow()
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fd_flow_started)) }
                },
                onStop = {
                    vm.cancelRun()
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.fd_flow_stopped)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {

            // ---- DESCRIPTION ----
            if (f.description.isNotBlank()) {
                item {
                    Text(
                        f.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // ---- TRIGGERS ----
            item {
                SectionHeader(
                    title = stringResource(R.string.fd_section_when),
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
                        stringResource(R.string.fd_no_triggers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(f.triggers, key = { _, t -> t.id }) { index, trigger ->
                val ti = trigger.type.info(context)
                GroupedItem(index = index, count = f.triggers.size + 1) {
                    TriggerOrActionRow(
                        icon = { Icon(ti.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        headline = ti.label,
                        supporting = trigger.type.configSummary(context, trigger.config),
                        onEdit = { pendingConfig = PendingConfig.EditTrigger(trigger) },
                        onDelete = { vm.removeTrigger(trigger.id) },
                    )
                }
            }

            item {
                GroupedItem(
                    index = f.triggers.size,
                    count = f.triggers.size + 1,
                    onClick = { showTriggerPicker = true },
                ) {
                    AddRowContent(stringResource(R.string.fd_add_trigger))
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ---- ACTIONS ----
            item { SectionHeader(stringResource(R.string.fd_section_then)) }

            if (f.actions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.fd_no_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(sortedActions, key = { _, a -> a.id }) { index, action ->
                val haptic = LocalHapticFeedback.current
                ReorderableItem(reorderState, key = action.id) { isDragging ->
                    val ai = action.type.info(context)
                    val isExecuting = currentActionId == action.id
                    GroupedItem(
                        index = index,
                        count = sortedActions.size + 1,
                        highlighted = isExecuting,
                        dragging = isDragging,
                        // Long-press anywhere on the row to start reordering (no visible handle).
                        dragModifier = if (isExecuting) Modifier else Modifier.longPressDraggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        ),
                    ) {
                        TriggerOrActionRow(
                            icon = {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(ai.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            },
                            headline = ai.label,
                            supporting = action.type.configSummary(context, action.config),
                            isExecuting = isExecuting,
                            onEdit = { pendingConfig = PendingConfig.EditAction(action) },
                            onDelete = { vm.removeAction(action.id) },
                        )
                    }
                }
            }

            item {
                GroupedItem(
                    index = sortedActions.size,
                    count = sortedActions.size + 1,
                    onClick = { showActionPicker = true },
                ) {
                    AddRowContent(stringResource(R.string.fd_add_action))
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ---- VARIABLES ----
            item { SectionHeader(stringResource(R.string.fd_section_variables)) }

            if (f.variables.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.fd_no_variables),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(f.variables, key = { _, v -> "var_${v.name}" }) { index, variable ->
                GroupedItem(index = index, count = f.variables.size + 1) {
                    TriggerOrActionRow(
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        headline = variable.name,
                        supporting = variable.defaultValue.ifBlank { stringResource(R.string.fd_empty_value) },
                        onEdit = { editingVariable = variable },
                        onDelete = { vm.removeVariable(variable.name) },
                    )
                }
            }

            item {
                GroupedItem(
                    index = f.variables.size,
                    count = f.variables.size + 1,
                    onClick = { editingVariable = Variable("", VariableType.STRING, "") },
                ) {
                    AddRowContent(stringResource(R.string.fd_add_variable))
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

        }
    }

    if (showTriggerPicker) {
        val alreadyHasManual = f.triggers.any { it.type == TriggerType.MANUAL }
        SearchPickerSheet(
            entries = remember(alreadyHasManual) {
                TriggerType.entries
                    .filter { it !in FlavorFeatures.hiddenTriggerTypes }
                    .filter { !(it == TriggerType.MANUAL && alreadyHasManual) }
                    .map {
                        val ti = it.info(context)
                        PickerEntry(it, ti.label, ti.icon, ti.description, context.getString(it.category.labelRes), it.category.ordinal)
                    }
            },
            searchPlaceholder = stringResource(R.string.fd_search_triggers),
            onSelect = { type ->
                showTriggerPicker = false
                val ti = type.info(context)
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
        SearchPickerSheet(
            entries = remember {
                ActionType.entries
                    .filter { it !in FlavorFeatures.hiddenActionTypes }
                    .map {
                        val ai = it.info(context)
                        PickerEntry(it, ai.label, ai.icon, ai.description, context.getString(it.category.labelRes), it.category.ordinal)
                    }
            },
            searchPlaceholder = stringResource(R.string.fd_search_actions),
            onSelect = { type ->
                showActionPicker = false
                val ai = type.info(context)
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
                title = cfg.type.info(context).label,
                fields = cfg.type.info(context).fields,
                initialValues = emptyMap(),
                availableVariables = flowVariables,
                onConfirm = { values ->
                    vm.addTrigger(Trigger(UUID.randomUUID().toString(), cfg.type, values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.EditTrigger -> ConfigDialog(
                title = cfg.trigger.type.info(context).label,
                fields = cfg.trigger.type.info(context).fields,
                initialValues = cfg.trigger.config,
                availableVariables = flowVariables,
                onConfirm = { values ->
                    vm.updateTrigger(cfg.trigger.copy(config = values))
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.NewAction -> if (cfg.type == ActionType.SHOW_MENU) {
                ShowMenuConfigDialog(
                    initialTitle = "",
                    initialOptions = listOf("", ""),
                    onConfirm = { title, options ->
                        val base = f.actions.size
                        val menuId = UUID.randomUUID().toString()
                        val optionsJson = Json.encodeToString(options)
                        val block = listOf(
                            Action(menuId, ActionType.SHOW_MENU, mapOf("title" to title, "options" to optionsJson), base, true),
                        ) + options.mapIndexed { i, opt ->
                            Action(UUID.randomUUID().toString(), ActionType.MENU_CASE, mapOf("option" to opt), base + 1 + i, true)
                        } + Action(UUID.randomUUID().toString(), ActionType.END_MENU, emptyMap(), base + 1 + options.size, true)
                        vm.addActions(block)
                        pendingConfig = null
                    },
                    onDismiss = { pendingConfig = null },
                )
            } else {
                ConfigDialog(
                    title = cfg.type.info(context).label,
                    fields = cfg.type.info(context).fields,
                    initialValues = emptyMap(),
                    availableVariables = flowVariables,
                    onConfirm = { values ->
                        vm.addAction(Action(UUID.randomUUID().toString(), cfg.type, values, f.actions.size, true))
                        pendingConfig = null
                    },
                    onDismiss = { pendingConfig = null },
                )
            }
            is PendingConfig.EditAction -> if (cfg.action.type == ActionType.SHOW_MENU) {
                val currentOptions = runCatching {
                    Json.decodeFromString<List<String>>(cfg.action.config["options"] ?: "[]")
                }.getOrElse { listOf("", "") }
                ShowMenuConfigDialog(
                    initialTitle = cfg.action.config["title"] ?: "",
                    initialOptions = currentOptions.ifEmpty { listOf("", "") },
                    onConfirm = { title, options ->
                        vm.syncMenuBlock(cfg.action.id, title, options)
                        pendingConfig = null
                    },
                    onDismiss = { pendingConfig = null },
                )
            } else {
                ConfigDialog(
                    title = cfg.action.type.info(context).label,
                    fields = cfg.action.type.info(context).fields,
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
    }

    editingVariable?.let { variable ->
        VariableDialog(
            variable = variable,
            existingNames = f.variables.map { it.name },
            onConfirm = { updated ->
                vm.saveVariable(originalName = variable.name.ifBlank { null }, variable = updated)
                editingVariable = null
            },
            onDismiss = { editingVariable = null },
        )
    }

    if (showIconPicker) {
        FlowIconPickerDialog(
            initialIcon = f.icon,
            initialColor = f.iconColor,
            onConfirm = { icon, color ->
                vm.setIcon(icon, color)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }

    if (showRenameDialog) {
        EditFlowDialog(
            initialName = f.name,
            initialDescription = f.description,
            initialIcon = f.icon,
            initialIconColor = f.iconColor,
            onConfirm = { name, desc, icon, color ->
                vm.updateDetails(name, desc, icon, color)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

@Composable
private fun FlowControlsBar(
    enabled: Boolean,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onShare: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowEnabledCapsule(
                    enabled = enabled,
                    onClick = { onToggle(!enabled) },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.fd_export))
                }
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = if (isRunning) onStop else onRun,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primary,
                        contentColor = if (isRunning) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    AnimatedContent(
                        targetState = isRunning,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "play_stop",
                    ) { running ->
                        Icon(
                            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (running) stringResource(R.string.fd_stop_flow) else stringResource(R.string.fd_run_flow),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowEnabledCapsule(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (enabled)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                contentDescription = if (enabled) stringResource(R.string.fd_disable_flow) else stringResource(R.string.fd_enable_flow),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (enabled) stringResource(R.string.fd_enabled) else stringResource(R.string.fd_disabled),
                style = MaterialTheme.typography.labelMedium,
            )
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
    // stringResource must be resolved here: ButtonGroup's content scope is not @Composable.
    val anyLabel = stringResource(R.string.fd_logic_any)
    val allLabel = stringResource(R.string.fd_logic_all)
    ButtonGroup(overflowIndicator = {}) {
        toggleableItem(
            checked = logic == TriggerLogic.ANY,
            onCheckedChange = { onSelect(TriggerLogic.ANY) },
            label = anyLabel,
        )
        toggleableItem(
            checked = logic == TriggerLogic.ALL,
            onCheckedChange = { onSelect(TriggerLogic.ALL) },
            label = allLabel,
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
// Segmented rounded card group (M3 expressive list style): first/last items get
// large corners, middle items small ones, separated by a 2dp gap.
// ---------------------------------------------------------------------------

@Composable
private fun GroupedItem(
    index: Int,
    count: Int,
    highlighted: Boolean = false,
    dragging: Boolean = false,
    onClick: (() -> Unit)? = null,
    dragModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val large = 18.dp
    val small = 5.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) large else small,
        topEnd = if (index == 0) large else small,
        bottomStart = if (index == count - 1) large else small,
        bottomEnd = if (index == count - 1) large else small,
    )

    val primary = MaterialTheme.colorScheme.primary
    val surfaceColor by animateColorAsState(
        targetValue = if (highlighted) primary.copy(alpha = 0.18f)
                      else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = snap(),
        label = "item_bg",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (highlighted) 8.dp else 0.dp,
        animationSpec = snap(),
        label = "item_shadow",
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) primary else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = snap(),
        label = "item_border",
    )

    // Smooth "lift" feedback while the row is being dragged (official pattern:
    // animate Surface shadowElevation on isDragging — no graphicsLayer/scale,
    // which would composite into a separate layer and ghost during the drag).
    val dragElevation by animateDpAsState(
        targetValue = if (dragging) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "item_drag_elevation",
    )

    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 2.dp)
        .then(if (highlighted) Modifier.zIndex(1f) else Modifier)
        .then(dragModifier)
        .border(width = 2.dp, color = borderColor, shape = shape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = surfaceColor,
            shadowElevation = maxOf(shadowElevation, dragElevation),
            modifier = modifier,
        ) { content() }
    } else {
        Surface(
            shape = shape,
            color = surfaceColor,
            shadowElevation = maxOf(shadowElevation, dragElevation),
            modifier = modifier,
        ) { content() }
    }
}

/** Centered "+ Add …" row used as the trailing segment of each group. */
@Composable
private fun AddRowContent(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
    isExecuting: Boolean = false,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon()
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
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
// Trigger/action picker — full-height sheet with search and category groups
// ---------------------------------------------------------------------------

private data class PickerEntry<T>(
    val type: T,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val categoryLabel: String,
    val categoryOrder: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> SearchPickerSheet(
    entries: List<PickerEntry<T>>,
    searchPlaceholder: String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }

    val grouped = remember(entries) {
        entries.sortedBy { it.categoryOrder }.groupBy { it.categoryLabel }.toList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fd_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
            )

            val q = query.trim()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (q.isEmpty()) {
                    grouped.forEach { (categoryLabel, categoryEntries) ->
                        item(key = "header_$categoryLabel") {
                            Text(
                                categoryLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(categoryEntries, key = { it.type.toString() }) { entry ->
                            PickerRow(entry = entry, onClick = { onSelect(entry.type) })
                        }
                    }
                } else {
                    val matches = entries.filter { entry ->
                        entry.label.contains(q, ignoreCase = true) ||
                            entry.description.contains(q, ignoreCase = true)
                    }
                    if (matches.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.fd_nothing_matches, q),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(matches, key = { it.type.toString() }) { entry ->
                        PickerRow(entry = entry, onClick = { onSelect(entry.type) })
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun <T> PickerRow(entry: PickerEntry<T>, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        headlineContent = { Text(entry.label) },
        supportingContent = {
            Text(entry.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------------------
// Generic config dialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConfigDialog(
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
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (firstTextInputKey != null) {
            // The dialog window needs a frame to attach before focus + IME take effect;
            // requestFocus() alone moves the cursor but doesn't reliably open the keyboard.
            delay(100)
            firstFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
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
                Text(stringResource(R.string.fd_no_config_needed))
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
                                                contentDescription = if (isInputMode) stringResource(R.string.fd_switch_to_dial) else stringResource(R.string.fd_switch_to_keyboard),
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
                                        else stringResource(R.string.fd_choose_app),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            is ConfigField.ImagePicker -> {
                                val current = values[field.key] ?: ""
                                // The stored value is a private copy under filesDir; flag it if the
                                // copy is somehow gone so the user re-picks instead of silently
                                // failing when the flow later runs.
                                val imageMissing = remember(current) {
                                    current.isNotBlank() && !File(current).exists()
                                }
                                val pickerScope = rememberCoroutineScope()
                                var copying by remember { mutableStateOf(false) }

                                // Persist the cropped image into app-private storage. Reading our own
                                // durable copy at run time means the flow keeps working even if the
                                // user later moves or deletes the original from their gallery.
                                fun persistCrop(cropped: Uri) {
                                    copying = true
                                    pickerScope.launch {
                                        try {
                                            val savedPath = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val dir = File(context.filesDir, "wallpapers")
                                                        .apply { mkdirs() }
                                                    val dest = File(dir, "wp_${UUID.randomUUID()}.img")
                                                    context.contentResolver.openInputStream(cropped)?.use { input ->
                                                        dest.outputStream().use { input.copyTo(it) }
                                                    } ?: return@runCatching null
                                                    dest.absolutePath
                                                }.getOrNull()
                                            }
                                            if (savedPath != null) {
                                                // Drop the previous copy so re-picking doesn't pile up files.
                                                current.takeIf { it.startsWith(context.filesDir.path) }
                                                    ?.let { old -> runCatching { File(old).delete() } }
                                                values[field.key] = savedPath
                                            }
                                        } finally {
                                            copying = false
                                        }
                                    }
                                }

                                val cropLauncher = rememberLauncherForActivityResult(
                                    CropImageContract(),
                                ) { result ->
                                    if (result.isSuccessful) {
                                        result.uriContent?.let { persistCrop(it) }
                                    }
                                }

                                val imagePicker = rememberLauncherForActivityResult(
                                    ActivityResultContracts.PickVisualMedia(),
                                ) { uri ->
                                    if (uri != null) {
                                        // Lock the crop frame to THIS device's screen aspect ratio so the
                                        // wallpaper fills the screen without the system blindly centre-
                                        // cropping (and never stretched — aspect is preserved throughout).
                                        val bounds = context.getSystemService(WindowManager::class.java)
                                            .currentWindowMetrics.bounds
                                        cropLauncher.launch(
                                            CropImageContractOptions(
                                                uri,
                                                CropImageOptions(
                                                    fixAspectRatio = true,
                                                    aspectRatioX = bounds.width().coerceAtLeast(1),
                                                    aspectRatioY = bounds.height().coerceAtLeast(1),
                                                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                                                    outputCompressQuality = 90,
                                                    // We already picked the image; don't show the library's
                                                    // own source chooser.
                                                    imageSourceIncludeGallery = false,
                                                    imageSourceIncludeCamera = false,
                                                ),
                                            ),
                                        )
                                    }
                                }
                                Column {
                                    OutlinedButton(
                                        enabled = !copying,
                                        onClick = {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                ),
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.Image, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            when {
                                                copying -> stringResource(R.string.fd_image_processing)
                                                current.isNotBlank() && !imageMissing -> stringResource(R.string.fd_image_selected)
                                                else -> field.label
                                            },
                                        )
                                    }
                                    if (imageMissing) {
                                        Text(
                                            stringResource(R.string.fd_image_missing),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            is ConfigField.DayPicker -> {
                                if (field.showWhenKey != null &&
                                    values[field.showWhenKey] != field.showWhenValue
                                ) return@forEach

                                val days = listOf(
                                    "MON" to stringResource(R.string.day_mon),
                                    "TUE" to stringResource(R.string.day_tue),
                                    "WED" to stringResource(R.string.day_wed),
                                    "THU" to stringResource(R.string.day_thu),
                                    "FRI" to stringResource(R.string.day_fri),
                                    "SAT" to stringResource(R.string.day_sat),
                                    "SUN" to stringResource(R.string.day_sun),
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
                                        placeholder = { Text(stringResource(R.string.fd_ssid_blank_hint)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (currentSsid != null) {
                                        Spacer(Modifier.height(4.dp))
                                        AssistChip(
                                            onClick = { values[field.key] = currentSsid },
                                            label = { Text(stringResource(R.string.fd_use_current_ssid, currentSsid)) },
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
                                        placeholder = { Text(stringResource(R.string.fd_tag_blank_hint)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = scanning,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    when {
                                        nfcAdapter == null -> Text(
                                            stringResource(R.string.fd_nfc_unavailable),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        !nfcAdapter.isEnabled -> Text(
                                            stringResource(R.string.fd_nfc_disabled),
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
                                                stringResource(R.string.fd_nfc_hold),
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            TextButton(onClick = { scanning = false }) { Text(stringResource(R.string.action_cancel)) }
                                        }
                                        else -> OutlinedButton(
                                            onClick = { scanning = true },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Outlined.Nfc, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if ((values[field.key] ?: "").isBlank()) stringResource(R.string.fd_scan_nfc) else stringResource(R.string.fd_rescan_tag))
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
                                        locationError = context.getString(R.string.fd_location_denied)
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

                            is ConfigField.MenuOptionList -> {
                                val stored = values[field.key] ?: "[]"
                                val optionList = remember(stored) {
                                    mutableStateOf(
                                        runCatching {
                                            Json.decodeFromString<List<String>>(stored)
                                        }.getOrElse { emptyList() }.toMutableList(),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        field.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    optionList.value.forEachIndexed { idx, opt ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            OutlinedTextField(
                                                value = opt,
                                                onValueChange = { newVal ->
                                                    val updated = optionList.value.toMutableList()
                                                    updated[idx] = newVal
                                                    optionList.value = updated
                                                    values[field.key] = Json.encodeToString(updated.toList())
                                                },
                                                label = { Text(stringResource(R.string.fd_option_n, idx + 1)) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = {
                                                    val updated = optionList.value.toMutableList()
                                                    updated.removeAt(idx)
                                                    optionList.value = updated
                                                    values[field.key] = Json.encodeToString(updated.toList())
                                                },
                                                enabled = optionList.value.size > 2,
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = stringResource(R.string.fd_remove_option),
                                                    tint = if (optionList.value.size > 2)
                                                        MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            val updated = optionList.value.toMutableList()
                                            updated.add("")
                                            optionList.value = updated
                                            values[field.key] = Json.encodeToString(updated.toList())
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.fd_add_option))
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
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        onError(context.getString(R.string.fd_location_unavailable))
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
// Dedicated dialog for creating / editing a Show Menu block
// ---------------------------------------------------------------------------

@Composable
private fun ShowMenuConfigDialog(
    initialTitle: String,
    initialOptions: List<String>,
    onConfirm: (title: String, options: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    val options = remember { mutableStateOf(initialOptions.toMutableList()) }

    val isValid = options.value.size >= 2 && options.value.all { it.isNotBlank() }

    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100)
        titleFocusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fd_show_menu)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.fd_prompt_title)) },
                    placeholder = { Text(stringResource(R.string.fd_menu_title_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                )
                Text(
                    stringResource(R.string.fd_menu_options),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                options.value.forEachIndexed { idx, opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { newVal ->
                                val updated = options.value.toMutableList()
                                updated[idx] = newVal
                                options.value = updated
                            },
                            label = { Text(stringResource(R.string.fd_option_n, idx + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val updated = options.value.toMutableList()
                                updated.removeAt(idx)
                                options.value = updated
                            },
                            enabled = options.value.size > 2,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.fd_remove_option),
                                tint = if (options.value.size > 2) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        options.value = (options.value + "").toMutableList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.fd_add_option))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), options.value.map { it.trim() }) },
                enabled = isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Variable editor dialog
// ---------------------------------------------------------------------------

@Composable
private fun VariableDialog(
    variable: Variable,
    existingNames: List<String>,
    onConfirm: (Variable) -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = variable.name.isBlank()
    var name by remember { mutableStateOf(variable.name) }
    var value by remember { mutableStateOf(variable.defaultValue) }

    val trimmedName = name.trim()
    val nameTaken = isNew && trimmedName in existingNames

    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (isNew) {
            delay(100)
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.fd_new_variable) else stringResource(R.string.fd_edit_variable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text(stringResource(R.string.fd_var_name_taken)) }
                    } else {
                        { Text(stringResource(R.string.fd_var_name_hint, trimmedName)) }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.fd_default_value)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(Variable(trimmedName, variable.type, value)) },
                enabled = trimmedName.isNotBlank() && !nameTaken,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Edit Flow dialog — name, description, icon and color
// ---------------------------------------------------------------------------

@Composable
private fun EditFlowDialog(
    initialName: String,
    initialDescription: String,
    initialIcon: String?,
    initialIconColor: String?,
    onConfirm: (name: String, description: String, icon: String, iconColor: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var icon by rememberSaveable { mutableStateOf(initialIcon ?: FlowIcons.DEFAULT_KEY) }
    var iconColor by rememberSaveable {
        mutableStateOf(initialIconColor ?: FlowIcons.colorPalette.first())
    }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }

    if (showIconPicker) {
        FlowIconPickerDialog(
            initialIcon = icon,
            initialColor = iconColor,
            onConfirm = { newIcon, newColor ->
                icon = newIcon
                iconColor = newColor
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fd_edit_flow_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIconPicker = true }
                        .padding(vertical = 4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                FlowIcons.color(iconColor) ?: MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            FlowIcons.vector(icon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.fd_icon), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.fd_tap_to_customize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description_optional)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), icon, iconColor) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
