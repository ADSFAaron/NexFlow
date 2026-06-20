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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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

    if (showLogRetentionDialog) {
        ModalBottomSheet(
            onDismissRequest = { showLogRetentionDialog = false },
        ) {
            Text(
                text = "Log retention",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            Text(
                text = "每週自動清理一次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LogRetentionOption.entries.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.displayName) },
                    supportingContent = {
                        Text(
                            text = option.detail,
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {

            // ----- Permissions -----
            item { SectionHeader("Permissions") }
            item {
                ListItem(
                    headlineContent = { Text("Notifications") },
                    supportingContent = {
                        Text(if (notifGranted.value) "Granted" else "Required for Notification actions")
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
                            ) { Text("Grant") }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Notification access") },
                    supportingContent = {
                        Text(
                            if (notifListenerGranted.value) "Granted — notification trigger active"
                            else "Required for Notification received trigger",
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
                            ) { Text("Enable") }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Accessibility Service") },
                    supportingContent = {
                        Text(
                            if (accessibilityGranted.value) "Granted — app launch trigger & screenshot active"
                            else "Required for App Launch trigger & Screenshot actions",
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
                                onClick = {
                                    accessibilityLauncher.launch(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                    )
                                },
                            ) { Text("Enable") }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Modify system settings") },
                    supportingContent = {
                        Text(
                            if (writeSettingsGranted.value) "Granted"
                            else "Required for Brightness actions",
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
                            ) { Text("Grant") }
                        }
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Do Not Disturb access") },
                    supportingContent = {
                        Text(
                            if (dndGranted.value) "Granted"
                            else "Required for DND toggle actions",
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
                            ) { Text("Grant") }
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
                    headlineContent = { Text("Wi-Fi & Airplane mode (ADB)") },
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
                            Text("WRITE_SECURE_SETTINGS granted — silent toggling active")
                        } else {
                            Column {
                                Text("Run once via ADB to enable silent Wi-Fi / Airplane mode toggling:")
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
                                                    snackbarHostState.showSnackbar("Command copied to clipboard")
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy command")
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
            item { SectionHeader("Import / Export") }
            item {
                ListItem(
                    headlineContent = { Text("Import flow") },
                    supportingContent = { Text("Pick a NexFlow JSON or MacroDroid .mdr file") },
                    leadingContent = {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    },
                    trailingContent = {
                        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Text("Import")
                        }
                    },
                )
            }

            item { HorizontalDivider() }

            // ----- Automation -----
            item { SectionHeader("Automation") }
            item {
                ListItem(
                    headlineContent = { Text("Auto-start on boot") },
                    supportingContent = { Text("Resume enabled flows when device starts") },
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
                    headlineContent = { Text("Log retention") },
                    supportingContent = { Text("${logRetention.displayName} — ${logRetention.detail}") },
                    leadingContent = {
                        Icon(Icons.Outlined.History, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showLogRetentionDialog = true },
                )
            }

            item { HorizontalDivider() }

            // ----- About -----
            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("About NexFlow") },
                    supportingContent = { Text("版本、開發者、授權條款") },
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
