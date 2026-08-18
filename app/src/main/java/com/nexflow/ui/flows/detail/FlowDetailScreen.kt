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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nexflow.R
import com.nexflow.executor.HttpActionExecutor
import com.nexflow.shortcut.PinShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.Flow
import com.nexflow.FlavorFeatures
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerVariables
import com.nexflow.permissions.PermissionReminder
import com.nexflow.service.AllTriggersGate
import com.nexflow.ui.common.AppPickerDialog
import com.nexflow.ui.common.FlowIconPickerDialog
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.common.geminiGradientTint
import com.nexflow.ui.common.PermissionSetupDialogs
import com.nexflow.ui.common.ShortcutPickerDialog
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import androidx.compose.ui.graphics.Color
import com.nexflow.ui.flows.detail.config.ActionInfo
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.NEGATE_KEY
import com.nexflow.ui.flows.detail.config.normalizeConfigForEditing
import com.nexflow.ui.flows.detail.config.CoordinatePickerDialog
import com.nexflow.ui.flows.detail.config.pointFrom
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowDetailScreen(
    vm: FlowDetailViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onEditWithAi: () -> Unit = {},
) {
    val flow by vm.flow.collectAsState()

    // Crossfade loading → editor. contentKey limits the transition to the null ↔ loaded
    // switch, so ordinary flow edits (every save changes the state) don't re-animate.
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = flow,
        contentKey = { it == null },
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        label = "fd_loading",
    ) { loaded ->
        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            FlowDetailContent(f = loaded, vm = vm, onBack = onBack, onEditWithAi = onEditWithAi)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlowDetailContent(
    f: Flow,
    vm: FlowDetailViewModel,
    onBack: () -> Unit,
    onEditWithAi: () -> Unit,
) {
    val isRunning by vm.isRunning.collectAsState()
    val currentActionId by vm.currentActionId.collectAsState()

    var showTriggerPicker by rememberSaveable { mutableStateOf(false) }
    var showConditionPicker by rememberSaveable { mutableStateOf(false) }
    var showActionPicker by rememberSaveable { mutableStateOf(false) }
    // Non-null while the action picker was opened from a block marker's "+" (Menu Case /
    // If / Else / Repeat): the chosen action is inserted right after this id, inside the
    // branch, instead of being appended after the block's End marker.
    var insertAnchorId by rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable so an open config dialog (and what it points at) survives rotation and
    // process death — otherwise a screen rotation silently discards the user's input.
    var pendingConfig by rememberSaveable(stateSaver = PendingConfigSaver) {
        mutableStateOf<PendingConfig?>(null)
    }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }

    // Arriving from the import review's "Fix now": open the settings of the exact item it was
    // about. Consumed immediately so returning here later doesn't reopen the dialog.
    LaunchedEffect(f.id) {
        val focusId = vm.focusItemId ?: return@LaunchedEffect
        vm.consumeFocusItem()
        pendingConfig = when {
            f.triggers.any { it.id == focusId } -> PendingConfig.EditTrigger(focusId)
            f.conditions.any { it.id == focusId } -> PendingConfig.EditCondition(focusId)
            f.actions.any { it.id == focusId } -> PendingConfig.EditAction(focusId)
            // The item was already deleted, or it is a menu block the editor opens as a whole.
            else -> null
        }
    }
    // null = closed; Variable with blank name = creating a new one
    var editingVariable by rememberSaveable(stateSaver = EditingVariableSaver) {
        mutableStateOf<Variable?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pinUnsupportedMessage = stringResource(R.string.fd_pin_shortcut_unsupported)
    val onPinShortcut: () -> Unit = {
        if (PinShortcutHelper.isSupported(context)) {
            PinShortcutHelper.pin(context, f)
        } else {
            scope.launch { snackbarHostState.showSnackbar(pinUnsupportedMessage) }
        }
    }

    // Deleting a trigger/action/variable is one tap with no confirmation, so every delete
    // must be recoverable: show a snackbar whose Undo action re-inserts the captured item.
    val showDeletedSnackbar: (String, () -> Unit) -> Unit = { label, undo ->
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.snackbar_deleted, label),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    // Enabling from this screen goes through the same permission gate as the Flows list:
    // missing permissions block the toggle and open the reminder + guided setup instead.
    var permissionReminder by remember { mutableStateOf<PermissionReminder?>(null) }
    val permissionSetup by vm.permissionSetup.collectAsState()
    LaunchedEffect(vm) {
        vm.permissionReminder.collect { permissionReminder = it }
    }
    // A run the flow's own conditions held back has no visible effect otherwise.
    val skippedMessage = stringResource(R.string.fd_flow_skipped)
    LaunchedEffect(vm) {
        vm.runSkipped.collect { snackbarHostState.showSnackbar(skippedMessage) }
    }
    LaunchedEffect(vm) {
        vm.setupComplete.collect { result ->
            val msg = if (result.allGranted) {
                context.getString(R.string.flows_perm_setup_done, result.flowName)
            } else {
                context.getString(R.string.flows_perm_setup_incomplete, result.flowName)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    PermissionSetupDialogs(
        reminder = permissionReminder,
        onReminderDismiss = { permissionReminder = null },
        onBeginSetup = { vm.beginPermissionSetup(it.flowId, it.autoEnableOnComplete) },
        setup = permissionSetup,
        onAdvance = vm::advancePermissionSetup,
        onMarkAttempted = vm::markPermissionAttempted,
        onSkip = vm::skipCurrentPermission,
        onCancel = vm::cancelPermissionSetup,
    )

    val globalVariableRefs by vm.globalVariableRefs.collectAsState()
    val flowVariables = remember(f.actions, f.variables, f.triggers, globalVariableRefs) {
        (
            f.variables.map { it.name } +
                f.actions.filter { it.type == ActionType.SET_VARIABLE }
                    .mapNotNull { it.config["variable_name"]?.takeIf { n -> n.isNotBlank() } } +
                // What an HTTP request stores: the variable the user named for the response, plus
                // the status code every request publishes. Branching on an API reply is the point
                // of the action, and without these the Save gate would reject the {{ref}} for it.
                f.actions.filter { it.type == ActionType.HTTP_REQUEST }
                    .flatMap { action ->
                        listOfNotNull(
                            action.config["response_var"]?.trim()?.takeIf { n -> n.isNotBlank() },
                            HttpActionExecutor.STATUS_VARIABLE,
                        )
                    } +
                // Global (cross-flow) variables, referenced as {{g:name}}.
                globalVariableRefs +
                // What this flow's triggers report about the event, as {{trigger.name}} —
                // offered by the insert menu and accepted by the dialog's unknown-reference
                // check, which would otherwise block Save on a perfectly valid reference.
                f.triggers.flatMap { trigger ->
                    TriggerVariables.keysFor(trigger.type).map { "${TriggerVariables.PREFIX}$it" }
                }
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
                        // The visible circle stays 34dp, but the clickable node is expanded to
                        // the 48dp accessibility minimum (minimumInteractiveComponentSize);
                        // end padding compensates for the extra 7dp so spacing looks the same.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable { showIconPicker = true },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        FlowIcons.color(f.iconColor) ?: MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    ),
                            ) {
                                Icon(
                                    FlowIcons.vector(f.icon),
                                    contentDescription = stringResource(R.string.fd_change_icon),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
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
                    IconButton(onClick = onPinShortcut) {
                        Icon(
                            Icons.AutoMirrored.Outlined.AddToHomeScreen,
                            contentDescription = stringResource(R.string.fd_pin_shortcut),
                        )
                    }
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
                onEditWithAi = onEditWithAi,
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

            // ALL is a combination over time, not a state — say how long it waits, otherwise the
            // toggle reads as "and" and a flow that never fires looks broken.
            if (f.triggerLogic == TriggerLogic.ALL && f.triggers.size > 1) {
                item {
                    Text(
                        pluralStringResource(
                            R.plurals.fd_logic_all_hint,
                            (AllTriggersGate.DEFAULT_WINDOW_MS / 60_000).toInt(),
                            (AllTriggersGate.DEFAULT_WINDOW_MS / 60_000).toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(f.triggers, key = { _, t -> t.id }) { index, trigger ->
                val ti = trigger.type.info(context)
                // animateItem: added/removed/undone rows slide into place instead of popping.
                GroupedItem(index = index, count = f.triggers.size + 1, modifier = Modifier.animateItem()) {
                    TriggerOrActionRow(
                        icon = { Icon(ti.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        headline = ti.label,
                        supporting = trigger.type.configSummary(context, trigger.config),
                        onEdit = { pendingConfig = PendingConfig.EditTrigger(trigger.id) },
                        onDelete = {
                            vm.removeTrigger(trigger.id)
                            showDeletedSnackbar(ti.label) { vm.addTrigger(trigger) }
                        },
                    )
                }
            }

            item(key = "add_trigger") {
                GroupedItem(
                    index = f.triggers.size,
                    count = f.triggers.size + 1,
                    modifier = Modifier.animateItem(),
                    onClick = { showTriggerPicker = true },
                ) {
                    AddRowContent(stringResource(R.string.fd_add_trigger))
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // ---- CONDITIONS (constraints) ----
            item { SectionHeader(stringResource(R.string.fd_section_only_if)) }

            if (f.conditions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.fd_no_conditions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            itemsIndexed(f.conditions, key = { _, c -> c.id }) { index, condition ->
                // A type this build doesn't know (hand-edited file, MacroDroid import) is shown
                // as-is with a warning: the engine refuses to run the flow rather than ignore a
                // constraint it can't check, so the row must say why.
                val knownType = remember(condition.type) { ConditionType.fromId(condition.type) }
                val ci = knownType?.info(context)
                GroupedItem(index = index, count = f.conditions.size + 1, modifier = Modifier.animateItem()) {
                    TriggerOrActionRow(
                        icon = {
                            Icon(
                                ci?.icon ?: Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        headline = ci?.label ?: condition.type,
                        supporting = knownType?.configSummary(context, condition.config, condition.negate)
                            ?: stringResource(R.string.cnd_unsupported),
                        onEdit = if (knownType != null) {
                            { pendingConfig = PendingConfig.EditCondition(condition.id) }
                        } else null,
                        onDelete = {
                            vm.removeCondition(condition.id)
                            showDeletedSnackbar(ci?.label ?: condition.type) { vm.addCondition(condition) }
                        },
                    )
                }
            }

            item(key = "add_condition") {
                GroupedItem(
                    index = f.conditions.size,
                    count = f.conditions.size + 1,
                    modifier = Modifier.animateItem(),
                    onClick = { showConditionPicker = true },
                ) {
                    AddRowContent(stringResource(R.string.fd_add_condition))
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
                            onEdit = { pendingConfig = PendingConfig.EditAction(action.id) },
                            onDelete = {
                                vm.removeAction(action.id)
                                showDeletedSnackbar(ai.label) { vm.restoreAction(action) }
                            },
                            // Branch markers get a "+" that inserts the new action INSIDE the
                            // branch — plain "Add Action" would land it after the End marker.
                            onAddBranchAction = if (action.type in branchStartTypes) {
                                {
                                    insertAnchorId = action.id
                                    showActionPicker = true
                                }
                            } else null,
                        )
                    }
                }
            }

            item(key = "add_action") {
                GroupedItem(
                    index = sortedActions.size,
                    count = sortedActions.size + 1,
                    modifier = Modifier.animateItem(),
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
                GroupedItem(index = index, count = f.variables.size + 1, modifier = Modifier.animateItem()) {
                    TriggerOrActionRow(
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        headline = variable.name,
                        supporting = variable.defaultValue.ifBlank { stringResource(R.string.fd_empty_value) },
                        onEdit = { editingVariable = variable },
                        onDelete = {
                            vm.removeVariable(variable.name)
                            showDeletedSnackbar(variable.name) { vm.saveVariable(null, variable) }
                        },
                    )
                }
            }

            item(key = "add_variable") {
                GroupedItem(
                    index = f.variables.size,
                    count = f.variables.size + 1,
                    modifier = Modifier.animateItem(),
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

    if (showConditionPicker) {
        SearchPickerSheet(
            entries = remember {
                ConditionType.entries.map {
                    val ci = it.info(context)
                    PickerEntry(it, ci.label, ci.icon, ci.description, context.getString(it.category.labelRes), it.category.ordinal)
                }
            },
            searchPlaceholder = stringResource(R.string.fd_search_conditions),
            onSelect = { type ->
                showConditionPicker = false
                pendingConfig = PendingConfig.NewCondition(type)
            },
            onDismiss = { showConditionPicker = false },
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
                val anchor = insertAnchorId
                insertAnchorId = null
                val ai = type.info(context)
                if (ai.fields.isEmpty()) {
                    val newAction = Action(UUID.randomUUID().toString(), type, emptyMap(), f.actions.size, true)
                    if (anchor != null) vm.addActionsAfter(anchor, listOf(newAction))
                    else vm.addAction(newAction)
                } else {
                    pendingConfig = PendingConfig.NewAction(type, anchor)
                }
            },
            onDismiss = {
                showActionPicker = false
                insertAnchorId = null
            },
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
            is PendingConfig.EditTrigger -> {
                // Resolved by id: the saveable PendingConfig only stores the id, and the
                // trigger may be gone after a restore — then there is nothing to edit.
                val trigger = f.triggers.find { it.id == cfg.triggerId }
                if (trigger != null) {
                    ConfigDialog(
                        title = trigger.type.info(context).label,
                        fields = trigger.type.info(context).fields,
                        initialValues = trigger.config,
                        availableVariables = flowVariables,
                        onConfirm = { values ->
                            vm.updateTrigger(trigger.copy(config = values))
                            pendingConfig = null
                        },
                        onDismiss = { pendingConfig = null },
                    )
                }
            }
            // Conditions carry `negate` outside their config map, so the dialog trades it through
            // the reserved NEGATE_KEY toggle and it is split back off here.
            is PendingConfig.NewCondition -> ConfigDialog(
                title = cfg.type.info(context).label,
                fields = cfg.type.info(context).fields,
                initialValues = emptyMap(),
                availableVariables = flowVariables,
                onConfirm = { values ->
                    vm.addCondition(
                        Condition(
                            id = UUID.randomUUID().toString(),
                            type = cfg.type.name,
                            config = values - NEGATE_KEY,
                            negate = values[NEGATE_KEY] == "true",
                        ),
                    )
                    pendingConfig = null
                },
                onDismiss = { pendingConfig = null },
            )
            is PendingConfig.EditCondition -> {
                val condition = f.conditions.find { it.id == cfg.conditionId }
                val type = condition?.let { ConditionType.fromId(it.type) }
                if (condition != null && type != null) {
                    ConfigDialog(
                        title = type.info(context).label,
                        fields = type.info(context).fields,
                        initialValues = condition.config + (NEGATE_KEY to condition.negate.toString()),
                        availableVariables = flowVariables,
                        onConfirm = { values ->
                            vm.updateCondition(
                                condition.copy(
                                    config = values - NEGATE_KEY,
                                    negate = values[NEGATE_KEY] == "true",
                                ),
                            )
                            pendingConfig = null
                        },
                        onDismiss = { pendingConfig = null },
                    )
                }
            }
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
                        if (cfg.anchorId != null) vm.addActionsAfter(cfg.anchorId, block)
                        else vm.addActions(block)
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
                        val newAction = Action(UUID.randomUUID().toString(), cfg.type, values, f.actions.size, true)
                        if (cfg.anchorId != null) vm.addActionsAfter(cfg.anchorId, listOf(newAction))
                        else vm.addAction(newAction)
                        pendingConfig = null
                    },
                    onDismiss = { pendingConfig = null },
                )
            }
            is PendingConfig.EditAction -> {
                val action = f.actions.find { it.id == cfg.actionId }
                when {
                    action == null -> {}
                    action.type == ActionType.SHOW_MENU -> {
                        val currentOptions = runCatching {
                            Json.decodeFromString<List<String>>(action.config["options"] ?: "[]")
                        }.getOrElse { listOf("", "") }
                        ShowMenuConfigDialog(
                            initialTitle = action.config["title"] ?: "",
                            initialOptions = currentOptions.ifEmpty { listOf("", "") },
                            onConfirm = { title, options ->
                                vm.syncMenuBlock(action.id, title, options)
                                pendingConfig = null
                            },
                            onDismiss = { pendingConfig = null },
                        )
                    }
                    else -> ConfigDialog(
                        title = action.type.info(context).label,
                        fields = action.type.info(context).fields,
                        initialValues = normalizeConfigForEditing(action.type, action.config),
                        availableVariables = flowVariables,
                        onConfirm = { values ->
                            vm.updateAction(action.copy(config = values))
                            pendingConfig = null
                        },
                        onDismiss = { pendingConfig = null },
                    )
                }
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
    onEditWithAi: () -> Unit,
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
                // Hands this flow to the AI chat as context so the user can ask for changes
                // in words. Same gradient-tinted mark as the Flows list' AI entry point.
                IconButton(onClick = onEditWithAi) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(R.string.fd_edit_with_ai),
                        tint = Color.White,
                        modifier = Modifier.geminiGradientTint(),
                    )
                }
                Spacer(Modifier.width(4.dp))
                // The icon already crossfades; animate the container/content colors on the
                // effects token too so run ↔ stop doesn't hard-swap the button color.
                val runContainer by animateColorAsState(
                    targetValue = if (isRunning) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "run_container",
                )
                val runContent by animateColorAsState(
                    targetValue = if (isRunning) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimary,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "run_content",
                )
                FilledIconButton(
                    onClick = if (isRunning) onStop else onRun,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = runContainer,
                        contentColor = runContent,
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
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                // includeFontPadding's default top/bottom padding is asymmetric, so the
                // glyphs sit visibly low within the line box even though the Row already
                // centers the Text's own bounds against the Icon.
                style = MaterialTheme.typography.labelMedium.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
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

/** Block-marker types whose row offers a "+" to insert an action inside the branch. */
private val branchStartTypes = setOf(
    ActionType.MENU_CASE,
    ActionType.IF_BLOCK,
    ActionType.ELSE_BLOCK,
    ActionType.REPEAT_BLOCK,
)

private sealed class PendingConfig {
    data class NewTrigger(val type: TriggerType) : PendingConfig()
    data class EditTrigger(val triggerId: String) : PendingConfig()
    data class NewCondition(val type: ConditionType) : PendingConfig()
    data class EditCondition(val conditionId: String) : PendingConfig()
    /** [anchorId] non-null = insert the new action right after that row (branch insert). */
    data class NewAction(val type: ActionType, val anchorId: String? = null) : PendingConfig()
    data class EditAction(val actionId: String) : PendingConfig()
}

/**
 * Serialises [PendingConfig] as `[kind, payload]` so an open config dialog survives
 * rotation/process death. Edit variants hold only the id; the live Trigger/Action is
 * re-resolved from the flow when the dialog is (re)composed.
 */
private val PendingConfigSaver = listSaver<PendingConfig?, String>(
    save = { cfg ->
        when (cfg) {
            null -> emptyList()
            is PendingConfig.NewTrigger -> listOf("new_trigger", cfg.type.name)
            is PendingConfig.EditTrigger -> listOf("edit_trigger", cfg.triggerId)
            is PendingConfig.NewCondition -> listOf("new_condition", cfg.type.name)
            is PendingConfig.EditCondition -> listOf("edit_condition", cfg.conditionId)
            is PendingConfig.NewAction -> listOf("new_action", cfg.type.name, cfg.anchorId ?: "")
            is PendingConfig.EditAction -> listOf("edit_action", cfg.actionId)
        }
    },
    restore = { saved ->
        val payload = saved.getOrNull(1)
        when {
            payload == null -> null
            saved[0] == "new_trigger" -> runCatching { PendingConfig.NewTrigger(TriggerType.valueOf(payload)) }.getOrNull()
            saved[0] == "edit_trigger" -> PendingConfig.EditTrigger(payload)
            saved[0] == "new_condition" -> runCatching { PendingConfig.NewCondition(ConditionType.valueOf(payload)) }.getOrNull()
            saved[0] == "edit_condition" -> PendingConfig.EditCondition(payload)
            saved[0] == "new_action" -> runCatching {
                PendingConfig.NewAction(
                    ActionType.valueOf(payload),
                    anchorId = saved.getOrNull(2)?.takeIf { it.isNotEmpty() },
                )
            }.getOrNull()
            saved[0] == "edit_action" -> PendingConfig.EditAction(payload)
            else -> null
        }
    },
)

/** Saves the open variable editor (blank name = "new variable" sentinel) across rotation. */
private val EditingVariableSaver = listSaver<Variable?, String>(
    save = { v -> if (v == null) emptyList() else listOf(v.name, v.type.name, v.defaultValue) },
    restore = { saved ->
        if (saved.size < 3) null
        else Variable(
            name = saved[0],
            type = runCatching { VariableType.valueOf(saved[1]) }.getOrDefault(VariableType.STRING),
            defaultValue = saved[2],
        )
    },
)

// ---------------------------------------------------------------------------
// Segmented rounded card group (M3 expressive list style): first/last items get
// large corners, middle items small ones, separated by a 2dp gap.
// ---------------------------------------------------------------------------

@Composable
private fun GroupedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
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
    // Highlight ON snaps so the "currently executing" marker tracks the engine with zero
    // lag; highlight OFF eases out on the effects token (M3: color/elevation = effects).
    val colorEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val dpEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Dp>()
    val surfaceColor by animateColorAsState(
        targetValue = if (highlighted) primary.copy(alpha = 0.18f)
                      else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = if (highlighted) snap() else colorEffects,
        label = "item_bg",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (highlighted) 8.dp else 0.dp,
        animationSpec = if (highlighted) snap() else dpEffects,
        label = "item_shadow",
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) primary else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = if (highlighted) snap() else colorEffects,
        label = "item_border",
    )

    // Smooth "lift" feedback while the row is being dragged (official pattern:
    // animate Surface shadowElevation on isDragging — no graphicsLayer/scale,
    // which would composite into a separate layer and ghost during the drag).
    val dragElevation by animateDpAsState(
        targetValue = if (dragging) 8.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "item_drag_elevation",
    )

    val rowModifier = modifier
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
            modifier = rowModifier,
        ) { content() }
    } else {
        Surface(
            shape = shape,
            color = surfaceColor,
            shadowElevation = maxOf(shadowElevation, dragElevation),
            modifier = rowModifier,
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
    /** Null hides the edit button — for rows with nothing this build can configure. */
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    isExecuting: Boolean = false,
    onAddBranchAction: (() -> Unit)? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon()
                androidx.compose.animation.AnimatedVisibility(
                    visible = isExecuting,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
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
                if (onAddBranchAction != null) {
                    IconButton(onClick = onAddBranchAction) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.fd_add_branch_action),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(20.dp))
                    }
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, scrimColor = Color.Transparent) {
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
                            PickerRow(
                                entry = entry,
                                onClick = { onSelect(entry.type) },
                                modifier = Modifier.animateItem(),
                            )
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
                    // Same keys as the grouped branch, so rows reflow smoothly as the
                    // user types instead of the whole list snapping to the filtered set.
                    items(matches, key = { it.type.toString() }) { entry ->
                        PickerRow(
                            entry = entry,
                            onClick = { onSelect(entry.type) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun <T> PickerRow(
    entry: PickerEntry<T>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier.clickable(onClick = onClick),
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
    // Saveable: everything the user has typed must survive rotation. The map is
    // flattened to [k1, v1, k2, v2, …] because SnapshotStateMap itself isn't bundleable.
    val values = rememberSaveable(
        initialValues,
        saver = listSaver(
            save = { map -> map.entries.flatMap { (k, v) -> listOf(k, v) } },
            restore = { flat ->
                mutableStateMapOf<String, String>().apply {
                    var i = 0
                    while (i + 1 < flat.size) {
                        put(flat[i], flat[i + 1])
                        i += 2
                    }
                }
            },
        ),
    ) {
        mutableStateMapOf<String, String>().also { it.putAll(initialValues) }
    }
    val timePickerStates = remember { mutableMapOf<String, TimePickerState>() }
    val timePickerInputModes = remember { mutableStateMapOf<String, Boolean>() }

    val firstTextInputKey = remember(fields) {
        fields.firstNotNullOfOrNull { field ->
            when (field) {
                is ConfigField.TextInput -> field.key
                is ConfigField.VariableNameInput -> field.key
                else -> null
            }
        }
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

    // Variable safety net, shared by the in-dialog banners and the Save gate. Two kinds of typo
    // are caught: a {{ref}} no known variable matches, and a `g:` SET_VARIABLE target no global
    // declares (that one has no braces, so the {{ref}} scan can't see it). Both fail silently or
    // noisily at run time, so Save stays disabled until they're fixed.
    val unknownRefs by remember(availableVariables) {
        derivedStateOf {
            val known = availableVariables.toSet()
            VARIABLE_REF_REGEX.findAll(values.values.joinToString("\n"))
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() && it !in known }
                .distinct()
                .toList()
        }
    }
    val undeclaredGlobals by remember(availableVariables, fields) {
        derivedStateOf {
            val known = availableVariables.toSet()
            fields.filterIsInstance<ConfigField.VariableNameInput>()
                .mapNotNull { values[it.key]?.trim() }
                .filter { isGlobalVariable(it) && it !in known }
                .distinct()
        }
    }
    val hasVariableError = unknownRefs.isNotEmpty() || undeclaredGlobals.isNotEmpty()

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

    var shortcutPickerField by remember { mutableStateOf<ConfigField.ShortcutPicker?>(null) }

    shortcutPickerField?.let { field ->
        ShortcutPickerDialog(
            onSelect = { selection ->
                values[field.key] = selection.intentUri
                values[field.labelKey] = selection.label
                values[field.packageKey] = selection.packageName
                shortcutPickerField = null
            },
            onDismiss = { shortcutPickerField = null },
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
                    if (unknownRefs.isNotEmpty()) {
                        UnknownVariableWarning(unknownRefs)
                    }
                    if (undeclaredGlobals.isNotEmpty()) {
                        UndeclaredGlobalWarning(undeclaredGlobals)
                    }
                    fields.forEach { field ->
                        // Conditional fields (e.g. the app picker that only matters once the
                        // notification's tap action is "open app") stay out of the way until the
                        // field they depend on selects them.
                        if (!field.isVisible(values)) return@forEach
                        when (field) {
                            is ConfigField.TextInput -> {
                                VariableInsertField(
                                    value = values[field.key] ?: "",
                                    onValueChange = { values[field.key] = it },
                                    label = field.label,
                                    availableVariables = availableVariables,
                                    hint = field.hint,
                                    multiline = field.multiline,
                                    imeNext = !field.multiline,
                                    focusRequester = if (field.key == firstTextInputKey) firstFieldFocusRequester else null,
                                )
                            }

                            is ConfigField.VariableNameInput -> VariableNameField(
                                value = values[field.key] ?: "",
                                onValueChange = { values[field.key] = it },
                                label = field.label,
                                hint = field.hint,
                                knownVariables = availableVariables,
                                isUndeclaredGlobal = (values[field.key] ?: "").trim() in undeclaredGlobals,
                                focusRequester = if (field.key == firstTextInputKey) firstFieldFocusRequester else null,
                            )

                            is ConfigField.ConditionInput -> ConditionBuilderField(
                                label = field.label,
                                expression = values[field.key] ?: "",
                                onExpressionChange = { values[field.key] = it },
                                availableVariables = availableVariables,
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
                                    // What the number lands on here, for a value whose meaning
                                    // depends on the device rather than on the flow.
                                    field.describe?.invoke(context, values)?.let { detail ->
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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

                            is ConfigField.ShortcutPicker -> {
                                val storedLabel = values[field.labelKey]?.takeIf { it.isNotBlank() }
                                val pkg = values[field.packageKey]?.takeIf { it.isNotBlank() }
                                OutlinedButton(
                                    onClick = { shortcutPickerField = field },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        when {
                                            storedLabel != null && pkg != null -> "$storedLabel  ($pkg)"
                                            storedLabel != null -> storedLabel
                                            else -> stringResource(R.string.fd_choose_shortcut)
                                        },
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

                            is ConfigField.ScreenCoordinatePicker -> {
                                val endXKey = field.endXKey
                                val endYKey = field.endYKey
                                val swipeMode = endXKey != null && endYKey != null
                                var showPicker by remember { mutableStateOf(false) }

                                if (showPicker) {
                                    CoordinatePickerDialog(
                                        swipeMode = swipeMode,
                                        initialStart = pointFrom(values[field.xKey], values[field.yKey]),
                                        initialEnd = if (swipeMode) {
                                            pointFrom(values[endXKey], values[endYKey])
                                        } else null,
                                        onDismiss = { showPicker = false },
                                        onConfirm = { pickedStart, pickedEnd ->
                                            values[field.xKey] = pickedStart.x.toInt().toString()
                                            values[field.yKey] = pickedStart.y.toInt().toString()
                                            if (endXKey != null && endYKey != null && pickedEnd != null) {
                                                values[endXKey] = pickedEnd.x.toInt().toString()
                                                values[endYKey] = pickedEnd.y.toInt().toString()
                                            }
                                            showPicker = false
                                        },
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showPicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.TouchApp, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(field.label)
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
            TextButton(
                onClick = {
                    timePickerStates.forEach { (key, state) ->
                        values[key] = "${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}"
                    }
                    onConfirm(values.toMap())
                },
                // A typo'd variable name never does what the user meant, so don't let it be saved.
                enabled = !hasVariableError,
            ) { Text(stringResource(R.string.action_save)) }
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
// Variable-aware inputs: insert menu + structured condition builder
// ---------------------------------------------------------------------------

/** Matches {{name}} references. Explicit \}\} escape per the known Android regex quirk. */
private val VARIABLE_REF_REGEX = Regex("""\{\{([^}]+)\}\}""")

/** Comparison operators the interpreter understands, two-char first so parsing is unambiguous. */
private val CONDITION_OPERATORS = listOf("==", "!=", "<=", ">=", "<", ">")

private fun operatorLabel(op: String, context: Context): String = when (op) {
    "==" -> context.getString(R.string.op_equals)
    "!=" -> context.getString(R.string.op_not_equals)
    "<" -> context.getString(R.string.op_less)
    "<=" -> context.getString(R.string.op_less_equal)
    ">" -> context.getString(R.string.op_greater)
    ">=" -> context.getString(R.string.op_greater_equal)
    else -> op
}

/**
 * Splits an expression into (left, operator, right). Mirrors [FlowInterpreter]'s parser:
 * two-char operators win over their single-char prefixes, and an operator at index 0 is
 * ignored. No operator found -> the whole string is the left operand (a truthy check).
 */
internal fun parseCondition(expression: String): Triple<String, String, String> {
    val trimmed = expression.trim()
    for (op in CONDITION_OPERATORS) {
        val idx = trimmed.indexOf(op)
        if (idx > 0) {
            return Triple(
                trimmed.substring(0, idx).trim(),
                op,
                trimmed.substring(idx + op.length).trim(),
            )
        }
    }
    return Triple(trimmed, "==", "")
}

/** Rebuilds the interpreter expression. An empty right operand stores just the left. */
internal fun serializeCondition(left: String, op: String, right: String): String {
    val l = left.trim()
    val r = right.trim()
    return when {
        l.isEmpty() && r.isEmpty() -> ""
        r.isEmpty() -> l
        else -> "$l $op $r"
    }
}

/**
 * Outlined text field with a trailing "insert variable" menu. Selecting a variable drops
 * `{{name}}` at the caret (not blindly at the end), so the user never hand-types a name and
 * can't introduce a typo. The parent's [value] String stays the source of truth.
 */
@Composable
private fun VariableInsertField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    availableVariables: List<String>,
    modifier: Modifier = Modifier,
    hint: String = "",
    multiline: Boolean = false,
    imeNext: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Resync if the value is rewritten from outside (e.g. the condition builder re-parses).
    if (tfv.text != value) {
        tfv = TextFieldValue(value, TextRange(value.length))
    }
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = tfv,
        onValueChange = {
            tfv = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        placeholder = if (hint.isNotBlank()) {
            { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        keyboardOptions = KeyboardOptions(
            imeAction = if (imeNext) ImeAction.Next else ImeAction.Default,
        ),
        trailingIcon = if (availableVariables.isNotEmpty()) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.DataObject,
                            contentDescription = stringResource(R.string.cfg_insert_variable),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        availableVariables.forEach { varName ->
                            DropdownMenuItem(
                                text = { VariableMenuLabel("{{$varName}}", isGlobalVariable(varName)) },
                                onClick = {
                                    val start = tfv.selection.min
                                    val end = tfv.selection.max
                                    val insert = "{{$varName}}"
                                    val newText = tfv.text.replaceRange(start, end, insert)
                                    tfv = TextFieldValue(newText, TextRange(start + insert.length))
                                    onValueChange(newText)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        } else null,
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 5 else 1,
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

/** Global variables are namespaced `g:name`; used to colour-code them apart from local ones. */
private fun isGlobalVariable(name: String): Boolean = name.startsWith(FlowInterpreter.GLOBAL_PREFIX)

/**
 * One dropdown row for a variable, colour-coded: global variables (`g:name`) use the tertiary
 * colour + a globe icon, local ones the default text + a code icon.
 */
@Composable
private fun VariableMenuLabel(display: String, isGlobal: Boolean) {
    val color = if (isGlobal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (isGlobal) Icons.Outlined.Public else Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(display, color = color)
        if (isGlobal) {
            Text(
                stringResource(R.string.cfg_variable_global_tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * Free-text field for a variable *name* with a dropdown of existing local/global variables.
 * Picking one stores the bare name (`counter` or `g:shared`); typing a new *local* name is allowed
 * too, so SET_VARIABLE can still create one. A `g:` name must already exist — [isUndeclaredGlobal]
 * marks the typo case, which the engine refuses to run. Globals are colour-coded via
 * [VariableMenuLabel].
 */
@Composable
private fun VariableNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    knownVariables: List<String>,
    isUndeclaredGlobal: Boolean,
    focusRequester: FocusRequester?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isGlobal = isGlobalVariable(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (hint.isNotBlank()) {
            { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        isError = isUndeclaredGlobal,
        supportingText = if (isUndeclaredGlobal) {
            { Text(stringResource(R.string.cfg_undeclared_global_field_error)) }
        } else null,
        singleLine = true,
        // Tint the entered name too, so the field itself reflects local vs global — except when
        // it's an unknown global, where the error styling has to win.
        colors = if (isGlobal && !isUndeclaredGlobal) {
            OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.tertiary, unfocusedTextColor = MaterialTheme.colorScheme.tertiary)
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        trailingIcon = if (knownVariables.isNotEmpty()) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = stringResource(R.string.cfg_pick_variable),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        knownVariables.forEach { name ->
                            DropdownMenuItem(
                                text = { VariableMenuLabel(name, isGlobalVariable(name)) },
                                onClick = {
                                    onValueChange(name)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

/**
 * Structured editor for an IF condition: `value A` [operator] `value B`. Both operands use
 * [VariableInsertField], and the operator is a dropdown — so the whole expression is built by
 * tapping, never by typing raw `{{x}} < y` strings. Serialized back to the interpreter format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionBuilderField(
    label: String,
    expression: String,
    onExpressionChange: (String) -> Unit,
    availableVariables: List<String>,
) {
    val parsed = remember { parseCondition(expression) }
    var left by remember { mutableStateOf(parsed.first) }
    var op by remember { mutableStateOf(parsed.second) }
    var right by remember { mutableStateOf(parsed.third) }

    fun push() = onExpressionChange(serializeCondition(left, op, right))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        VariableInsertField(
            value = left,
            onValueChange = { left = it; push() },
            label = stringResource(R.string.cfg_condition_value_a),
            availableVariables = availableVariables,
            imeNext = true,
        )
        OperatorDropdown(selected = op, onSelected = { op = it; push() })
        VariableInsertField(
            value = right,
            onValueChange = { right = it; push() },
            label = stringResource(R.string.cfg_condition_value_b),
            availableVariables = availableVariables,
            imeNext = false,
        )
        Text(
            stringResource(R.string.cfg_condition_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperatorDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "$selected    ${operatorLabel(selected, context)}",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.cfg_condition_operator)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CONDITION_OPERATORS.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("$opt    ${operatorLabel(opt, context)}") },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Banner for a `g:` variable name that no global declares — a write to it fails the run, so this
 * (like [UnknownVariableWarning]) also disables Save.
 */
@Composable
private fun UndeclaredGlobalWarning(names: List<String>) {
    WarningBanner(stringResource(R.string.cfg_undeclared_global_warning, names.joinToString("、")))
}

/** Banner listing {{variable}} references that don't match a known variable; blocks Save. */
@Composable
private fun UnknownVariableWarning(unknownRefs: List<String>) {
    WarningBanner(
        stringResource(
            R.string.cfg_unknown_variable_warning,
            unknownRefs.joinToString("、") { "{{$it}}" },
        ),
    )
}

@Composable
private fun WarningBanner(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
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
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val options = rememberSaveable(
        saver = listSaver<MutableState<MutableList<String>>, String>(
            save = { it.value.toList() },
            restore = { mutableStateOf(it.toMutableList()) },
        ),
    ) { mutableStateOf(initialOptions.toMutableList()) }

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
    var name by rememberSaveable { mutableStateOf(variable.name) }
    var value by rememberSaveable { mutableStateOf(variable.defaultValue) }

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
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
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
