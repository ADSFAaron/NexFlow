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
package com.nexflow.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexflow.R
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * First-run onboarding. Three pages: what NexFlow is, how flows work, and an
 * interactive permissions setup so users understand *why* the app needs the
 * special permissions before being sent to system settings — the biggest
 * drop-off point for automation apps.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardingInfoPage(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.onboarding_welcome_title),
                        body = stringResource(R.string.onboarding_welcome_body),
                    )
                    1 -> OnboardingInfoPage(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.onboarding_how_title),
                        body = stringResource(R.string.onboarding_how_body),
                    )
                    else -> OnboardingPermissionsPage()
                }
            }

            PageIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isLastPage = pagerState.currentPage == PAGE_COUNT - 1
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                ) {
                    Text(
                        if (isLastPage) {
                            stringResource(R.string.onboarding_get_started)
                        } else {
                            stringResource(R.string.onboarding_next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingInfoPage(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingPermissionsPage() {
    val context = LocalContext.current

    fun accessibilityEnabled(): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(context.packageName) ?: false

    fun notifListenerEnabled(): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) ?: false

    fun notifPermGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    var notifGranted by remember { mutableStateOf(notifPermGranted()) }
    var accessibilityGranted by remember { mutableStateOf(accessibilityEnabled()) }
    var notifListenerGranted by remember { mutableStateOf(notifListenerEnabled()) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { accessibilityGranted = accessibilityEnabled() }

    val notifListenerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { notifListenerGranted = notifListenerEnabled() }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_perms_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_perms_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = stringResource(R.string.settings_notifications),
                grantedText = stringResource(R.string.settings_granted),
                requiredText = stringResource(R.string.settings_notifications_required),
                granted = notifGranted,
                actionLabel = stringResource(R.string.action_grant),
                onAction = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        PermissionRow(
            title = stringResource(R.string.settings_accessibility),
            grantedText = stringResource(R.string.settings_accessibility_granted),
            requiredText = stringResource(R.string.settings_accessibility_required),
            granted = accessibilityGranted,
            actionLabel = stringResource(R.string.action_enable),
            onAction = { showAccessibilityDisclosure = true },
        )
        PermissionRow(
            title = stringResource(R.string.settings_notif_access),
            grantedText = stringResource(R.string.settings_notif_access_granted),
            requiredText = stringResource(R.string.settings_notif_access_required),
            granted = notifListenerGranted,
            actionLabel = stringResource(R.string.action_enable),
            onAction = {
                notifListenerLauncher.launch(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.onboarding_perms_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    grantedText: String,
    requiredText: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) grantedText else requiredText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .background(if (active) activeColor else inactiveColor, CircleShape),
            )
        }
    }
}
