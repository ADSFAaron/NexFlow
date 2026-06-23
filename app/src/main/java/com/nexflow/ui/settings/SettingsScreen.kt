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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nexflow.prefs.LogRetentionOption
import com.nexflow.prefs.LogRetentionPrefs
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexflow.R
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.prefs.AutoStartPrefs
import com.nexflow.ui.flowimport.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAboutClick: () -> Unit = {},
    importVm: ImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(AutoStartPrefs.get(context)) }
    var logRetention by remember { mutableStateOf(LogRetentionPrefs.get(context)) }
    var showLogRetentionDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    // Google Play accessibility policy requires a prominent disclosure of what the service
    // does (and consent) before sending the user to enable it.
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val currentLanguage = remember { AppLanguage.current() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val notifGranted = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted.value = granted }

    val writeSettingsGranted = remember { mutableStateOf(Settings.System.canWrite(context)) }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { writeSettingsGranted.value = Settings.System.canWrite(context) }

    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    val dndGranted = remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    val dndLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { dndGranted.value = nm.isNotificationPolicyAccessGranted }

    val accessibilityGranted = remember {
        mutableStateOf(
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )?.contains(context.packageName) ?: false,
        )
    }
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        accessibilityGranted.value = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.contains(context.packageName) ?: false
    }

    val notifListenerGranted = remember {
        mutableStateOf(
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            )?.contains(context.packageName) ?: false,
        )
    }
    val notifListenerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        notifListenerGranted.value = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        )?.contains(context.packageName) ?: false
    }

    val hasWriteSecure = remember {
        context.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
        importVm.importAuto(content)
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_service_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDisclosure = false
                    accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
                    headlineContent = { Text(stringResource(option.displayNameRes)) },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.log_retention_detail, option.days, option.maxCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        RadioButton(
                            selected = logRetention == option,
                            onClick = {
                                logRetention = option
                                LogRetentionPrefs.set(context, option)
                                showLogRetentionDialog = false
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        logRetention = option
                        LogRetentionPrefs.set(context, option)
                        showLogRetentionDialog = false
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLanguageDialog) {
        ModalBottomSheet(onDismissRequest = { showLanguageDialog = false }) {
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
                    headlineContent = { Text(label) },
                    leadingContent = {
                        RadioButton(
                            selected = currentLanguage == language,
                            onClick = {
                                showLanguageDialog = false
                                AppLanguage.apply(language)
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        showLanguageDialog = false
                        AppLanguage.apply(language)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {

            // ----- General -----
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                val languageLabel = if (currentLanguage == AppLanguage.SYSTEM) {
                    stringResource(R.string.language_system_default)
                } else {
                    currentLanguage.displayName
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(languageLabel) },
                    leadingContent = {
                        Icon(Icons.Outlined.Language, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showLanguageDialog = true },
                )
            }

            item { HorizontalDivider() }

            // ----- Permissions -----
            item { SectionHeader(stringResource(R.string.settings_section_permissions)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                    supportingContent = {
                        Text(if (notifGranted.value) stringResource(R.string.settings_granted) else stringResource(R.string.settings_notifications_required))
                    },
                    leadingContent = {
                        Icon(
                            if (notifGranted.value) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (notifGranted.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                    trailingContent = {
                        if (!notifGranted.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                            ) { Text(stringResource(R.string.action_grant)) }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notif_access)) },
                    supportingContent = {
                        Text(
                            if (notifListenerGranted.value) stringResource(R.string.settings_notif_access_granted)
                            else stringResource(R.string.settings_notif_access_required),
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (notifListenerGranted.value) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (notifListenerGranted.value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        if (!notifListenerGranted.value) {
                            OutlinedButton(
                                onClick = {
                                    notifListenerLauncher.launch(
                                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                                    )
                                },
                            ) { Text(stringResource(R.string.action_enable)) }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_accessibility)) },
                    supportingContent = {
                        Text(
                            if (accessibilityGranted.value) stringResource(R.string.settings_accessibility_granted)
                            else stringResource(R.string.settings_accessibility_required),
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (accessibilityGranted.value) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (accessibilityGranted.value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        if (!accessibilityGranted.value) {
                            OutlinedButton(
                                onClick = { showAccessibilityDisclosure = true },
                            ) { Text(stringResource(R.string.action_enable)) }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_write_settings)) },
                    supportingContent = {
                        Text(
                            if (writeSettingsGranted.value) stringResource(R.string.settings_granted)
                            else stringResource(R.string.settings_write_settings_required),
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (writeSettingsGranted.value) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (writeSettingsGranted.value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        if (!writeSettingsGranted.value) {
                            OutlinedButton(
                                onClick = {
                                    writeSettingsLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                },
                            ) { Text(stringResource(R.string.action_grant)) }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dnd)) },
                    supportingContent = {
                        Text(
                            if (dndGranted.value) stringResource(R.string.settings_granted)
                            else stringResource(R.string.settings_dnd_required),
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (dndGranted.value) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (dndGranted.value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    trailingContent = {
                        if (!dndGranted.value) {
                            OutlinedButton(
                                onClick = {
                                    dndLauncher.launch(
                                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                                    )
                                },
                            ) { Text(stringResource(R.string.action_grant)) }
                        }
                    },
                )
            }

            item {
                val clipboard = remember {
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                }
                val adbCommand =
                    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_adb)) },
                    leadingContent = {
                        Icon(
                            if (hasWriteSecure) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = if (hasWriteSecure) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = {
                        if (hasWriteSecure) {
                            Text(stringResource(R.string.settings_adb_granted))
                        } else {
                            Column {
                                Text(stringResource(R.string.settings_adb_required))
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = adbCommand,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText("ADB command", adbCommand),
                                                )
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.settings_command_copied))
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.settings_copy_command))
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }

            item { HorizontalDivider() }

            // ----- Import / Export -----
            item { SectionHeader(stringResource(R.string.settings_section_import_export)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import_flow)) },
                    supportingContent = { Text(stringResource(R.string.settings_import_flow_desc)) },
                    leadingContent = {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    },
                    trailingContent = {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.action_import))
                        }
                    },
                )
            }

            item { HorizontalDivider() }

            // ----- Automation -----
            item { SectionHeader(stringResource(R.string.settings_section_automation)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_autostart)) },
                    supportingContent = { Text(stringResource(R.string.settings_autostart_desc)) },
                    leadingContent = {
                        Icon(Icons.Outlined.Autorenew, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = autoStart,
                            onCheckedChange = { value ->
                                autoStart = value
                                AutoStartPrefs.set(context, value)
                            },
                        )
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_log_retention)) },
                    supportingContent = {
                        Text(
                            stringResource(logRetention.displayNameRes) + " — " +
                                stringResource(R.string.log_retention_detail, logRetention.days, logRetention.maxCount),
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.History, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showLogRetentionDialog = true },
                )
            }

            item { HorizontalDivider() }

            // ----- About -----
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_nexflow)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_desc)) },
                    leadingContent = {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onAboutClick() },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
