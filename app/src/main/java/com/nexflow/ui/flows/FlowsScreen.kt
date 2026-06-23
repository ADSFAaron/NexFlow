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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.event.ImportEventSource
import com.nexflow.ui.common.FlowIcons
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
    onOpenSettings: () -> Unit = {},
) {
    val flows by vm.flows.collectAsState()
    val importResult by importVm.result.collectAsState()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var permissionReminder by remember { mutableStateOf<PermissionReminder?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionReminder = null }

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

    LaunchedEffect(vm) {
        vm.permissionReminder.collect { permissionReminder = it }
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
                            if (serviceRunning) FlowExecutionService.stop(context)
                            else FlowExecutionService.start(context)
                        },
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = it },
                        containerSize = ToggleFloatingActionButtonDefaults.containerSizeLarge(),
                        containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadiusLarge(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (fabMenuExpanded) stringResource(R.string.flows_close_menu) else stringResource(R.string.flows_open_actions),
                            modifier = Modifier
                                .size(ToggleFloatingActionButtonDefaults.iconSizeLarge()(checkedProgress))
                                .rotate(checkedProgress * 45f),
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
        },
    ) { innerPadding ->
        if (flows.isEmpty()) {
            EmptyFlowsContent(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(flows, key = { it.id }) { flow ->
                    var lastRunMs by remember { mutableStateOf(0L) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance * 0.15f },
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
                                    true
                                }
                                else -> false
                            }
                        },
                    )
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
                            onClick = { onFlowClick(flow.id) },
                            onToggle = { vm.toggleEnabled(flow.id, it) },
                            onRun = { vm.runFlow(flow.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(128.dp)) }
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

    permissionReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = { permissionReminder = null },
            title = { Text(stringResource(R.string.flows_permissions_needed)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.flows_permissions_body, reminder.flowName))
                    Spacer(Modifier.height(4.dp))
                    reminder.missing.forEach {
                        Text(stringResource(R.string.flows_bullet, it.label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                val runtime = reminder.missing.flatMap { it.runtimePermissions }
                when {
                    // Foreground permissions first — request them via the runtime dialog. Any
                    // background-location step intentionally reappears on the next attempt,
                    // once foreground location has been granted.
                    runtime.isNotEmpty() -> TextButton(
                        onClick = { permissionLauncher.launch(runtime.toTypedArray()) },
                    ) { Text(stringResource(R.string.action_grant)) }
                    // Background location can only be set to "Allow all the time" in system
                    // settings on Android 11+, so open the app's settings page directly.
                    reminder.missing.any { it.openLocationSettings } -> TextButton(
                        onClick = {
                            permissionReminder = null
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.flows_open_settings)) }
                    else -> TextButton(
                        onClick = {
                            permissionReminder = null
                            onOpenSettings()
                        },
                    ) { Text(stringResource(R.string.flows_open_settings)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionReminder = null }) { Text(stringResource(R.string.action_later)) }
            },
        )
    }

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
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
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

@Composable
private fun EmptyFlowsContent(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Composable
private fun FlowCard(
    flow: Flow,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
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
                Switch(
                    checked = flow.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(end = 8.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                flow.triggers.take(2).forEach { trigger ->
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                trigger.type.name.lowercase().replace('_', ' ')
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
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
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
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
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
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
