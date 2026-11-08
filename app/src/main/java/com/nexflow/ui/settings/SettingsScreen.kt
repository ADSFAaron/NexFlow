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
package com.nexflow.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nexflow.prefs.LogRetentionOption
import com.nexflow.prefs.LogRetentionPrefs
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexflow.R
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.prefs.AiProvider
import com.nexflow.prefs.AutoStartPrefs
import com.nexflow.prefs.DetailedLogPrefs
import com.nexflow.prefs.ExecutionFeedbackPrefs
import com.nexflow.ui.flowimport.ImportReviewDialog
import com.nexflow.ui.flowimport.ImportViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsSwitchItem
import com.nexflow.ui.common.SettingsTopAppBar
import androidx.compose.material3.ListItemDefaults
import com.nexflow.ui.common.inContainerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAboutClick: () -> Unit = {},
    onGlobalVariablesClick: () -> Unit = {},
    onAiSettingsClick: () -> Unit = {},
    /** [focusItemId] opens that trigger/condition/action's settings on arrival. */
    onOpenFlow: (flowId: String, focusItemId: String?) -> Unit = { _, _ -> },
    importVm: ImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(AutoStartPrefs.get(context)) }
    var executionToast by remember { mutableStateOf(ExecutionFeedbackPrefs.isToastEnabled(context)) }
    var detailedLog by remember { mutableStateOf(DetailedLogPrefs.isEnabled(context)) }
    var logRetention by remember { mutableStateOf(LogRetentionPrefs.get(context)) }
    var showLogRetentionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    // What the AI row says; re-read on resume, which is also the way back from the AI page.
    var aiState by remember { mutableStateOf(AiSettingsState.read(context)) }
    // Google Play accessibility policy requires a prominent disclosure of what the service
    // does (and consent) before sending the user to enable it.
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val currentLanguage = remember { AppLanguage.current() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Every permission is re-read on resume, not only when one of our own launchers returns:
    // the user can just as well revoke one in system settings and come back through Recents,
    // and a row that still says "granted" then is worse than no row at all.
    var notifGranted by remember { mutableStateOf(context.notificationsGranted()) }
    var notifListenerGranted by remember { mutableStateOf(context.notificationListenerGranted()) }
    var accessibilityGranted by remember { mutableStateOf(context.accessibilityGranted()) }
    var writeSettingsGranted by remember { mutableStateOf(Settings.System.canWrite(context)) }
    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var dndGranted by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    // Granted from a computer while the app is open, so it is worth re-reading too.
    var hasWriteSecure by remember { mutableStateOf(context.writeSecureSettingsGranted()) }
    fun refreshPermissions() {
        notifGranted = context.notificationsGranted()
        notifListenerGranted = context.notificationListenerGranted()
        accessibilityGranted = context.accessibilityGranted()
        writeSettingsGranted = Settings.System.canWrite(context)
        dndGranted = nm.isNotificationPolicyAccessGranted
        hasWriteSecure = context.writeSecureSettingsGranted()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                aiState = AiSettingsState.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }
    // For everything that is granted on a system screen: come back, look again.
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshPermissions() }
    var showAdbDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Importing from here used to report nothing at all — same review as the Flows screen.
    val importResult by importVm.result.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
        importVm.importAuto(content)
    }

    importResult?.let { result ->
        ImportReviewDialog(
            result = result,
            onDismiss = importVm::clearResult,
            onOpenItem = onOpenFlow,
        )
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_service_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDisclosure = false
                    systemSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(stringResource(R.string.accessibility_disclosure_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLogRetentionDialog) {
        ModalBottomSheet(
            onDismissRequest = { showLogRetentionDialog = false },
            scrimColor = Color.Transparent,
        ) {
            Text(
                text = stringResource(R.string.settings_log_retention),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_log_retention_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LogRetentionOption.entries.forEach { option ->
                ListItem(
                    colors = ListItemDefaults.inContainerColors(),
                    headlineContent = { Text(stringResource(option.displayNameRes)) },
                    supportingContent = {
                        Text(
                            text = logRetentionDetail(option.days, option.maxCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        // onClick = null: the whole row is the selectable — a second clickable
                        // here would give TalkBack two focus stops for one choice.
                        RadioButton(selected = logRetention == option, onClick = null)
                    },
                    modifier = Modifier.selectable(
                        selected = logRetention == option,
                        role = Role.RadioButton,
                        onClick = {
                            logRetention = option
                            LogRetentionPrefs.set(context, option)
                            showLogRetentionDialog = false
                        },
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLanguageDialog) {
        ModalBottomSheet(onDismissRequest = { showLanguageDialog = false }, scrimColor = Color.Transparent) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            AppLanguage.entries.forEach { language ->
                val label = if (language == AppLanguage.SYSTEM) {
                    stringResource(R.string.language_system_default)
                } else {
                    language.displayName
                }
                ListItem(
                    colors = ListItemDefaults.inContainerColors(),
                    headlineContent = { Text(label) },
                    leadingContent = {
                        RadioButton(selected = currentLanguage == language, onClick = null)
                    },
                    modifier = Modifier.selectable(
                        selected = currentLanguage == language,
                        role = Role.RadioButton,
                        onClick = {
                            showLanguageDialog = false
                            AppLanguage.apply(language)
                        },
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAdbDialog) {
        val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
        val adbCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            icon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_adb)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_adb_required))
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        SelectionContainer {
                            Text(
                                text = adbCommand,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", adbCommand))
                    showAdbDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.settings_command_copied))
                    }
                }) { Text(stringResource(R.string.settings_copy_command)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdbDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = { SettingsTopAppBar(stringResource(R.string.settings_title), scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SettingsGroup(stringResource(R.string.settings_section_general)) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_language),
                            summary = if (currentLanguage == AppLanguage.SYSTEM) {
                                stringResource(R.string.language_system_default)
                            } else {
                                currentLanguage.displayName
                            },
                            leading = { Icon(Icons.Outlined.Language, contentDescription = null) },
                            onClick = { showLanguageDialog = true },
                        )
                    }
                }
            }

            // One row into the AI page: which service is in use and with what model, or that
            // nothing is set up yet — the one state worth flagging from here.
            item {
                SettingsGroup(stringResource(R.string.settings_section_ai)) {
                    item { shapes ->
                        val state = aiState
                        val model = when (state.provider) {
                            AiProvider.GEMINI -> state.geminiModel
                            AiProvider.CUSTOM -> state.customModel.orEmpty()
                        }
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_provider),
                            summary = if (state.configured) {
                                stringResource(R.string.settings_ai_summary, state.provider.label(), model)
                            } else {
                                stringResource(R.string.settings_ai_not_configured)
                            },
                            leading = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                            trailing = if (state.configured) {
                                null
                            } else {
                                {
                                    Icon(
                                        Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            onClick = onAiSettingsClick,
                        )
                    }
                }
            }

            // Each row is the way in to its system screen, granted or not — before, a granted
            // permission left a row that did nothing, with no way to revoke it from here.
            item {
                SettingsGroup(stringResource(R.string.settings_section_permissions)) {
                    item { shapes ->
                        PermissionItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_notifications),
                            icon = Icons.Outlined.Notifications,
                            granted = notifGranted,
                            grantedSummary = stringResource(R.string.settings_granted),
                            requiredSummary = stringResource(R.string.settings_notifications_required),
                            onClick = {
                                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    systemSettingsLauncher.launch(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }
                            },
                        )
                    }
                    item { shapes ->
                        PermissionItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_notif_access),
                            icon = Icons.Outlined.NotificationsActive,
                            granted = notifListenerGranted,
                            grantedSummary = stringResource(R.string.settings_notif_access_granted),
                            requiredSummary = stringResource(R.string.settings_notif_access_required),
                            onClick = {
                                systemSettingsLauncher.launch(
                                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                                )
                            },
                        )
                    }
                    item { shapes ->
                        PermissionItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_accessibility),
                            icon = Icons.Outlined.AccessibilityNew,
                            granted = accessibilityGranted,
                            grantedSummary = stringResource(R.string.settings_accessibility_granted),
                            requiredSummary = stringResource(R.string.settings_accessibility_required),
                            onClick = {
                                // The disclosure is owed before enabling, not before turning it off.
                                if (accessibilityGranted) {
                                    systemSettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } else {
                                    showAccessibilityDisclosure = true
                                }
                            },
                        )
                    }
                    item { shapes ->
                        PermissionItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_write_settings),
                            icon = Icons.Outlined.BrightnessMedium,
                            granted = writeSettingsGranted,
                            grantedSummary = stringResource(R.string.settings_granted),
                            requiredSummary = stringResource(R.string.settings_write_settings_required),
                            onClick = {
                                systemSettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        "package:${context.packageName}".toUri(),
                                    ),
                                )
                            },
                        )
                    }
                    item { shapes ->
                        PermissionItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_dnd),
                            icon = Icons.Outlined.DoNotDisturbOn,
                            granted = dndGranted,
                            grantedSummary = stringResource(R.string.settings_granted),
                            requiredSummary = stringResource(R.string.settings_dnd_required),
                            onClick = {
                                systemSettingsLauncher.launch(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                )
                            },
                        )
                    }
                    // Optional, unlike the rows above — nothing breaks without it, so a missing
                    // grant is not flagged as a problem. The command lives in a dialog: a block of
                    // monospace in the middle of the list was the one thing on the page no
                    // settings screen has.
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_adb),
                            summary = stringResource(
                                if (hasWriteSecure) R.string.settings_adb_granted else R.string.settings_adb_summary,
                            ),
                            leading = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            onClick = if (hasWriteSecure) null else ({ showAdbDialog = true }),
                        )
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_import_export)) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_import_flow),
                            summary = stringResource(R.string.settings_import_flow_desc),
                            leading = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_automation)) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_global_variables),
                            summary = stringResource(R.string.settings_global_variables_desc),
                            leading = { Icon(Icons.Outlined.Public, contentDescription = null) },
                            onClick = onGlobalVariablesClick,
                        )
                    }
                    item { shapes ->
                        SettingsSwitchItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_autostart),
                            summary = stringResource(R.string.settings_autostart_desc),
                            leading = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                            checked = autoStart,
                            onCheckedChange = { value ->
                                autoStart = value
                                AutoStartPrefs.set(context, value)
                            },
                        )
                    }
                    item { shapes ->
                        SettingsSwitchItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_execution_toast),
                            summary = stringResource(R.string.settings_execution_toast_desc),
                            leading = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                            checked = executionToast,
                            onCheckedChange = { value ->
                                executionToast = value
                                ExecutionFeedbackPrefs.setToastEnabled(context, value)
                            },
                        )
                    }
                }
            }

            // Its own group so the privacy note can sit under it as a footer: five lines of it
            // as the switch's summary buried the switch, and it applies to the retention row too.
            item {
                SettingsGroup(
                    title = stringResource(R.string.nav_logs),
                    footer = stringResource(R.string.settings_detailed_log_desc),
                ) {
                    item { shapes ->
                        SettingsSwitchItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_detailed_log),
                            summary = stringResource(R.string.settings_detailed_log_summary),
                            leading = { Icon(Icons.Outlined.Troubleshoot, contentDescription = null) },
                            checked = detailedLog,
                            onCheckedChange = { value ->
                                detailedLog = value
                                DetailedLogPrefs.setEnabled(context, value)
                            },
                        )
                    }
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_log_retention),
                            summary = stringResource(logRetention.displayNameRes) + " — " +
                                logRetentionDetail(logRetention.days, logRetention.maxCount),
                            leading = { Icon(Icons.Outlined.History, contentDescription = null) },
                            onClick = { showLogRetentionDialog = true },
                        )
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_about)) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_about_nexflow),
                            summary = stringResource(R.string.settings_about_desc),
                            leading = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = onAboutClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A permission the app needs for some of its triggers or actions. The leading icon says what
 * it is for — the same kind of icon as every other row on the page — and what state it is in
 * goes in the summary, with a warning at the end only while it is missing, so the rows that
 * need attention still stand out at a glance.
 */
@Composable
private fun PermissionItem(
    shapes: ListItemShapes,
    title: String,
    icon: ImageVector,
    granted: Boolean,
    grantedSummary: String,
    requiredSummary: String,
    onClick: () -> Unit,
) {
    SettingsItem(
        shapes = shapes,
        title = title,
        summary = if (granted) grantedSummary else requiredSummary,
        leading = { Icon(icon, contentDescription = null) },
        trailing = if (granted) {
            null
        } else {
            {
                // The summary already says it is missing; this is for the eye, not TalkBack.
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * "30 days · 500 entries" — two counts, so two plurals joined by a format string rather than one
 * plurals resource, which can only agree with a single quantity.
 */
@Composable
private fun logRetentionDetail(days: Int, entries: Int): String = stringResource(
    R.string.log_retention_detail,
    pluralStringResource(R.plurals.log_retention_days, days, days),
    pluralStringResource(R.plurals.log_retention_entries, entries, entries),
)

private fun Context.notificationsGranted(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun Context.accessibilityGranted(): Boolean =
    Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(packageName) ?: false

private fun Context.notificationListenerGranted(): Boolean =
    Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.contains(packageName) ?: false

private fun Context.writeSecureSettingsGranted(): Boolean =
    checkPermission("android.permission.WRITE_SECURE_SETTINGS", Process.myPid(), Process.myUid()) ==
        PackageManager.PERMISSION_GRANTED
