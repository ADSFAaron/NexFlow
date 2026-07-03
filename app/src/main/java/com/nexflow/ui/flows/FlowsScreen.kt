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
package com.nexflow.ui.flows

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.event.ImportEventSource
import com.nexflow.permissions.PermissionReminder
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.common.PermissionSetupDialogs
import com.nexflow.ui.flows.detail.config.info
import com.nexflow.service.FlowExecutionService
import com.nexflow.ui.flowimport.ImportViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowsScreen(
    vm: FlowsViewModel = hiltViewModel(),
    importVm: ImportViewModel = hiltViewModel(),
    onFlowClick: (String) -> Unit = {},
) {
    val flows by vm.flows.collectAsState()
    val flowsMissingPermissions by vm.flowsMissingPermissions.collectAsState()
    val importResult by importVm.result.collectAsState()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var permissionReminder by remember { mutableStateOf<PermissionReminder?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Captured here: AnimatedContent's transitionSpec lambda is not composable.
    val contentFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val serviceRunning by FlowExecutionService.running.collectAsState()
    // null = 無提示, true = 已啟動, false = 已關閉
    var serviceNotification by remember { mutableStateOf<Boolean?>(null) }
    var isInitialServiceState by remember { mutableStateOf(true) }
    LaunchedEffect(serviceRunning) {
        if (isInitialServiceState) {
            isInitialServiceState = false
            return@LaunchedEffect
        }
        serviceNotification = serviceRunning
        delay(3000L)
        serviceNotification = null
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
        importVm.importAuto(content)
    }

    // Content arriving from an external app (shared JSON / opened .flow file) must NOT be
    // imported silently — a malicious file could otherwise install a flow without the user's
    // knowledge. Hold it and require explicit confirmation. (Imported flows are also always
    // created disabled; see ImportViewModel.)
    var pendingExternalImport by remember { mutableStateOf<String?>(null) }
    val pendingImport by ImportEventSource.pendingContent.collectAsState()
    LaunchedEffect(pendingImport) {
        val content = pendingImport ?: return@LaunchedEffect
        pendingExternalImport = content
        ImportEventSource.clear()
    }

    pendingExternalImport?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingExternalImport = null },
            title = { Text(stringResource(R.string.flows_import_flow_q)) },
            text = {
                Text(stringResource(R.string.flows_import_external_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    importVm.importAuto(content)
                    pendingExternalImport = null
                }) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalImport = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    LaunchedEffect(vm) {
        vm.navigateToFlow.collect { flowId ->
            showCreateDialog = false
            onFlowClick(flowId)
        }
    }

    val permissionSetup by vm.permissionSetup.collectAsState()

    LaunchedEffect(vm) {
        vm.permissionReminder.collect { permissionReminder = it }
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

    // Re-check permission warnings each time the screen resumes — the user may have just
    // returned from system Settings after granting (or revoking) a permission. (The wizard
    // itself advances on resume inside PermissionSetupDialogs.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshPermissionWarnings()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = fabMenuExpanded) { fabMenuExpanded = false }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.flows_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    ServiceCapsule(
                        running = serviceRunning,
                        notification = serviceNotification,
                        onClick = {
                            // The capsule is the persistent master switch: remember the choice
                            // so reopening the app / rebooting doesn't override it.
                            if (serviceRunning) {
                                ServiceEnabledPrefs.set(context, false)
                                FlowExecutionService.stop(context)
                            } else {
                                ServiceEnabledPrefs.set(context, true)
                                FlowExecutionService.start(context)
                            }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        // Crossfade empty state ↔ list. targetState is the list itself with an isEmpty
        // contentKey: the outgoing branch keeps its captured items, so deleting the last
        // flow fades the card out instead of blanking it before the transition.
        AnimatedContent(
            targetState = flows,
            contentKey = { it.isEmpty() },
            transitionSpec = { fadeIn(contentFade) togetherWith fadeOut(contentFade) },
            label = "flows_content",
        ) { currentFlows ->
        // Master switch off ⇒ triggers won't fire no matter what the per-flow switches say;
        // surface that state prominently with a one-tap way back on.
        val startService = {
            ServiceEnabledPrefs.set(context, true)
            FlowExecutionService.start(context)
        }
        if (currentFlows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (!serviceRunning) {
                    ServiceOffBanner(
                        onStart = startService,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
                EmptyFlowsContent(modifier = Modifier.fillMaxSize())
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!serviceRunning) {
                    item(key = "service_off_banner") {
                        ServiceOffBanner(
                            onStart = startService,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(currentFlows, key = { it.id }) { flow ->
                    var lastRunMs by remember { mutableStateOf(0L) }
                    // positionalThreshold is left at the spec default (56dp) — the previous 15%
                    // fraction made accidental swipes delete flows. Deletion is also recoverable
                    // via the snackbar's Undo action below.
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastRunMs > 500L) {
                                        lastRunMs = now
                                        vm.runFlow(flow.id)
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.flows_running, flow.name)) }
                                    }
                                    false
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    vm.deleteFlow(flow.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.snackbar_deleted, flow.name),
                                            actionLabel = context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) vm.restoreFlow(flow)
                                    }
                                    true
                                }
                                else -> false
                            }
                        },
                    )
                    // The state is saveable and keyed by flow id, so a row restored via the
                    // snackbar's Undo comes back with its saved *dismissed* value — it would
                    // render as the bare red delete background forever. Animate it back to
                    // Settled: the undone card slides in from the edge. Keyed on the state
                    // instance so it never re-fires mid-delete for a live row.
                    LaunchedEffect(dismissState) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            dismissState.reset()
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                        backgroundContent = {
                            when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.action_run),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .wrapContentSize(Alignment.CenterStart)
                                        .padding(horizontal = 24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                SwipeToDismissBoxValue.EndToStart -> Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .wrapContentSize(Alignment.CenterEnd)
                                        .padding(horizontal = 24.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                SwipeToDismissBoxValue.Settled -> {}
                            }
                        },
                    ) {
                        FlowCard(
                            flow = flow,
                            permissionWarning = flow.id in flowsMissingPermissions,
                            onClick = { onFlowClick(flow.id) },
                            onToggle = { vm.toggleEnabled(flow.id, it) },
                            onRun = { vm.runFlow(flow.id) },
                            onWarningClick = { vm.showMissingPermissions(flow.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(128.dp)) }
            }
        }
        }

        // Official M3 placement: the FAB menu lives in a full-size Box and aligns itself
        // BottomEnd, handling its own 16dp edge spacing internally (per the FAB-menu spec).
        // padding(innerPadding) keeps the collapsed "x" clear of the system nav bar.
        FloatingActionButtonMenu(
            expanded = fabMenuExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(innerPadding),
            button = {
                // Compact height (phone in landscape, < 480dp tall): the large 96dp FAB's
                // expanded menu doesn't fit — the item column becomes scroll-constrained and
                // the close button overlaps the pills. Fall back to the baseline FAB size.
                val compactHeight = LocalConfiguration.current.screenHeightDp < 480
                ToggleFloatingActionButton(
                    checked = fabMenuExpanded,
                    onCheckedChange = { fabMenuExpanded = it },
                    containerSize = if (compactHeight) ToggleFloatingActionButtonDefaults.containerSize()
                        else ToggleFloatingActionButtonDefaults.containerSizeLarge(),
                    containerCornerRadius = if (compactHeight) ToggleFloatingActionButtonDefaults.containerCornerRadius()
                        else ToggleFloatingActionButtonDefaults.containerCornerRadiusLarge(),
                    // contentAlignment is left at the spec default (TopEnd): as the FAB morphs
                    // into the 56dp close button it pins to the top-end of its box, so the X
                    // stays flush with the menu pills' trailing edge (Keep-style) instead of
                    // shrinking into the middle.
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (fabMenuExpanded) stringResource(R.string.flows_close_menu) else stringResource(R.string.flows_open_actions),
                        // animateIcon animates both size AND color across checkedProgress, so the
                        // "x" flips to the on-checked content color (proper contrast on the checked
                        // container) instead of keeping the unchecked tint. rotate turns + into x.
                        modifier = with(ToggleFloatingActionButtonDefaults) {
                            Modifier
                                .animateIcon(
                                    checkedProgress = { checkedProgress },
                                    size = if (compactHeight) iconSize() else iconSizeLarge(),
                                )
                                .rotate(checkedProgress * 45f)
                        },
                    )
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    fabMenuExpanded = false
                    filePicker.launch(arrayOf("*/*"))
                },
                text = { Text(stringResource(R.string.settings_import_flow)) },
                icon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    fabMenuExpanded = false
                    showCreateDialog = true
                },
                text = { Text(stringResource(R.string.flows_new_flow)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
        }
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = importVm::clearResult,
            title = {
                Text(if (result.error != null) stringResource(R.string.flows_import_failed) else stringResource(R.string.flows_import_complete))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (result.error != null) {
                        Text(result.error)
                    } else {
                        Text(pluralStringResource(R.plurals.flows_imported_count, result.imported, result.imported))
                        if (result.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                pluralStringResource(R.plurals.flows_warning_count, result.warnings.size, result.warnings.size),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            result.warnings.take(5).forEach { Text(stringResource(R.string.flows_bullet, it), style = MaterialTheme.typography.bodySmall) }
                            if (result.warnings.size > 5) {
                                Text(stringResource(R.string.flows_and_more, result.warnings.size - 5), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = importVm::clearResult) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    // Shared permission UI: reminder dialog + step-by-step wizard + bg-location disclosure.
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

    if (showCreateDialog) {
        CreateFlowDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                vm.createFlow(name, desc)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun ServiceCapsule(
    running: Boolean,
    notification: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (running)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (running)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .padding(end = 8.dp)
            // Size change = spatial motion; the theme's expressive token keeps the bounce
            // consistent with built-in component motion.
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = if (running) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                contentDescription = if (running) stringResource(R.string.flows_stop_service) else stringResource(R.string.flows_start_service),
                modifier = Modifier.size(20.dp),
            )
            if (notification != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (notification) stringResource(R.string.flows_service_started) else stringResource(R.string.flows_service_stopped),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Warning banner shown while the automation service (master switch) is stopped. */
@Composable
private fun ServiceOffBanner(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.flows_service_off_banner),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStart) { Text(stringResource(R.string.flows_service_off_action)) }
        }
    }
}

@Composable
private fun EmptyFlowsContent(modifier: Modifier = Modifier) {
    // Drive the bounce ourselves: a bouncy spring overshoots past scale 1.0, and
    // AnimatedVisibility clips that overshoot to the content's settled bounds — cutting off
    // the edges of the text. A graphicsLayer with clip = false lets the scaled-up content
    // draw in full.
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    // Theme motion tokens: scale is spatial (may overshoot), fade is an effect (no bounce).
    val scaleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, scaleSpec) }
        launch { alpha.animateTo(1f, alphaSpec) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                clip = false
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.flows_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.flows_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowCard(
    flow: Flow,
    permissionWarning: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onWarningClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var runActivated by remember { mutableStateOf(false) }

    LaunchedEffect(runActivated) {
        if (runActivated) {
            delay(3000L)
            runActivated = false
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .background(
                            FlowIcons.color(flow.iconColor) ?: MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        FlowIcons.vector(flow.icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = flow.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The card's own click opens the detail screen, so the row can't be the
                // toggleable — give the bare Switch an explicit label for TalkBack instead.
                val switchDesc = stringResource(R.string.flows_flow_switch_desc, flow.name)
                Switch(
                    checked = flow.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .semantics { contentDescription = switchDesc },
                )
            }

            if (flow.description.isNotBlank()) {
                Text(
                    text = flow.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Animate the badge in/out so granting a permission doesn't hard-jump the card
            // layout — expand/shrink is spatial, the fade is an effect.
            AnimatedVisibility(
                visible = permissionWarning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    onClick = onWarningClick,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.flows_missing_permission_desc),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.flows_missing_permission),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Plain badges, not chips: they aren't tappable, so an onClick-less Surface
                // keeps TalkBack from announcing a button that does nothing. Labels come from
                // the localized trigger catalog rather than the raw enum name.
                val context = LocalContext.current
                flow.triggers.take(2).forEach { trigger ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            trigger.type.info(context).label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                RunCapsule(
                    activated = runActivated,
                    onClick = {
                        if (!runActivated) {
                            runActivated = true
                            onRun()
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RunCapsule(
    activated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (activated)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (activated)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = if (activated) Icons.Filled.Bolt else Icons.Filled.PlayArrow,
                contentDescription = if (activated) stringResource(R.string.flows_flow_activated) else stringResource(R.string.fd_run_flow),
                modifier = Modifier.size(16.dp),
            )
            if (activated) {
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.flows_flow_started), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CreateFlowDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    // rememberSaveable: typed text must survive rotation / process death (Core App Quality).
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100)
        nameFocusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flows_new_flow)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
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
                onClick = { onCreate(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
