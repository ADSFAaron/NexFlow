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
package com.nexflow.ui.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexflow.R
import com.nexflow.permissions.PermissionIntents
import com.nexflow.permissions.PermissionReminder
import com.nexflow.permissions.PermissionSetup
import com.nexflow.permissions.SpecialAccess

/**
 * The complete permission-granting UI shared by the Flows list and the flow detail
 * screen: the "permissions needed" reminder, the step-by-step wizard dialog (with the
 * runtime-permission and Settings-page launchers) and the background-location
 * prominent disclosure required by Google Play.
 *
 * The wizard also advances itself on every ON_RESUME, because the user typically
 * returns from a system Settings page rather than through a result callback.
 */
@Composable
fun PermissionSetupDialogs(
    reminder: PermissionReminder?,
    onReminderDismiss: () -> Unit,
    onBeginSetup: (PermissionReminder) -> Unit,
    setup: PermissionSetup?,
    onAdvance: () -> Unit,
    onMarkAttempted: (List<String>) -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    // Prominent disclosure shown before sending the user to grant background location
    // ("Allow all the time"), as required by Google Play's background-location policy.
    var showBgLocationDisclosure by remember { mutableStateOf(false) }

    // Runtime-permission dialog doesn't reliably fire ON_RESUME, so advance the wizard here.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onAdvance() }

    // Settings pages are separate activities; the result callback fires on return (ON_RESUME
    // also advances, so double-firing is harmless — advance just recomputes).
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onAdvance() }

    // Advance each time the hosting screen resumes — the user may have just returned from
    // system Settings after granting (or revoking) a permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onAdvance()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    reminder?.let { rem ->
        AlertDialog(
            onDismissRequest = onReminderDismiss,
            title = { Text(stringResource(R.string.flows_permissions_needed)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.flows_permissions_body, rem.flowName))
                    Spacer(Modifier.height(4.dp))
                    rem.missing.forEach {
                        Text(stringResource(R.string.flows_bullet, it.label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                // Kick off the guided, one-at-a-time setup: each grant walks the user to the
                // next still-missing permission until the flow can run.
                TextButton(
                    onClick = {
                        onReminderDismiss()
                        onBeginSetup(rem)
                    },
                ) { Text(stringResource(R.string.flows_perm_setup_start)) }
            },
            dismissButton = {
                TextButton(onClick = onReminderDismiss) { Text(stringResource(R.string.action_later)) }
            },
        )
    }

    // Step-by-step permission wizard. Shows one permission at a time; the current one is
    // launched with the exact runtime dialog or the deep-linked Settings page.
    setup?.let { current ->
        val step = current.remaining.firstOrNull() ?: return@let
        val activity = context as? Activity
        val isRuntime = step.runtimePermissions.isNotEmpty()
        // A runtime permission is permanently denied ("Don't ask again") when it has been
        // requested already but the system now refuses to show a rationale. In that state the
        // runtime dialog will not reappear, so route the user to the app's settings page.
        val permanentlyDenied = isRuntime && activity != null &&
            step.runtimePermissions.all { it in current.attempted } &&
            step.runtimePermissions.none {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(stringResource(R.string.flows_perm_setup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.flows_perm_setup_current, step.label))
                    if (permanentlyDenied) {
                        Text(
                            text = stringResource(R.string.flows_perm_setup_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val more = current.remaining.size - 1
                    if (more > 0) {
                        Text(
                            text = stringResource(R.string.flows_perm_setup_remaining, more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            // Denied for good → the app settings page is the only way to grant.
                            permanentlyDenied ->
                                settingsLauncher.launch(PermissionIntents.appDetailsSettings(context))
                            isRuntime -> {
                                onMarkAttempted(step.runtimePermissions)
                                permissionLauncher.launch(step.runtimePermissions.toTypedArray())
                            }
                            step.special == SpecialAccess.BACKGROUND_LOCATION ->
                                showBgLocationDisclosure = true
                            step.special != null -> {
                                val intent = PermissionIntents.forSpecial(context, step.special)
                                if (intent != null) settingsLauncher.launch(intent) else onSkip()
                            }
                            step.packageToInstall != null -> {
                                // Missing app (shared/imported flow, or uninstalled later):
                                // send the user to its Play Store page; returning re-checks.
                                val market = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=${step.packageToInstall}"),
                                )
                                runCatching { settingsLauncher.launch(market) }.recoverCatching {
                                    settingsLauncher.launch(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                "https://play.google.com/store/apps/details?id=${step.packageToInstall}",
                                            ),
                                        ),
                                    )
                                }.getOrElse { onSkip() }
                            }
                            else -> onSkip()
                        }
                    },
                ) {
                    Text(
                        when {
                            permanentlyDenied -> stringResource(R.string.flows_open_settings)
                            isRuntime -> stringResource(R.string.action_grant)
                            step.packageToInstall != null -> stringResource(R.string.action_install)
                            else -> stringResource(R.string.action_enable)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.flows_perm_setup_skip))
                }
            },
        )
    }

    if (showBgLocationDisclosure) {
        AlertDialog(
            onDismissRequest = { showBgLocationDisclosure = false },
            title = { Text(stringResource(R.string.bg_location_disclosure_title)) },
            text = { Text(stringResource(R.string.bg_location_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBgLocationDisclosure = false
                    PermissionIntents.forSpecial(context, SpecialAccess.BACKGROUND_LOCATION)?.let {
                        settingsLauncher.launch(it)
                    }
                }) { Text(stringResource(R.string.accessibility_disclosure_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showBgLocationDisclosure = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
