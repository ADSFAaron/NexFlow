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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexflow.R
import com.nexflow.shortcut.AppShortcutQuery
import com.nexflow.shortcut.ConfigurableShortcutSource
import com.nexflow.shortcut.StaticShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of picking a shortcut: everything the LAUNCH_SHORTCUT action needs to persist. */
data class ShortcutSelection(
    val intentUri: String,
    val label: String,
    val packageName: String,
)

/**
 * Two-level shortcut picker: app list → that app's shortcuts.
 * Level two merges manifest shortcuts (like the launcher long-press menu) with
 * ACTION_CREATE_SHORTCUT configuration activities (shortcuts the app builds on demand).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutPickerDialog(
    onSelect: (ShortcutSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            apps = loadLaunchableApps(context)
            loadingApps = false
        }
    }

    // The app's own configuration activity returns the shortcut to create; the legacy
    // extras are also set by ShortcutManagerCompat, so this covers modern apps too.
    val createShortcutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val shortcutIntent = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        if (shortcutIntent == null) {
            // App only returned the modern PinItemRequest payload, which needs launcher
            // privileges to unpack — nothing usable for us.
            Toast.makeText(context, R.string.sp_unsupported_result, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        onSelect(
            ShortcutSelection(
                intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME),
                label = name ?: selectedApp?.label.orEmpty(),
                packageName = selectedApp?.packageName
                    ?: shortcutIntent.component?.packageName
                    ?: shortcutIntent.`package`.orEmpty(),
            ),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val app = selectedApp
        if (app == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.app_picker_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (loadingApps) {
                LoadingBox()
            } else {
                val filtered = if (query.isBlank()) apps
                else apps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
                LazyColumn {
                    items(filtered, key = { it.packageName }) { item ->
                        ListItem(
                            leadingContent = { AppIcon(item.icon) },
                            headlineContent = {
                                Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedApp = item },
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            ShortcutList(
                app = app,
                onBack = { selectedApp = null },
                onStaticSelect = { shortcut ->
                    onSelect(
                        ShortcutSelection(
                            intentUri = shortcut.intent.toUri(Intent.URI_INTENT_SCHEME),
                            label = shortcut.label,
                            packageName = app.packageName,
                        ),
                    )
                },
                onConfigurableSelect = { source ->
                    runCatching { createShortcutLauncher.launch(source.launchIntent) }
                        .onFailure {
                            Toast.makeText(context, R.string.sp_unsupported_result, Toast.LENGTH_LONG).show()
                        }
                },
            )
        }
    }
}

@Composable
private fun ShortcutList(
    app: InstalledApp,
    onBack: () -> Unit,
    onStaticSelect: (StaticShortcut) -> Unit,
    onConfigurableSelect: (ConfigurableShortcutSource) -> Unit,
) {
    val context = LocalContext.current
    var staticShortcuts by remember { mutableStateOf<List<StaticShortcut>>(emptyList()) }
    var hiddenCount by remember { mutableStateOf(0) }
    var configurable by remember { mutableStateOf<List<ConfigurableShortcutSource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            val result = AppShortcutQuery.staticShortcuts(context, app.packageName)
            staticShortcuts = result.launchable
            hiddenCount = result.notLaunchableCount
            configurable = AppShortcutQuery.configurableShortcutSources(context, app.packageName)
            loading = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
        }
        AppIcon(app.icon, size = 28)
        Text(
            app.label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    HorizontalDivider()

    if (loading) {
        LoadingBox()
        return
    }
    if (staticShortcuts.isEmpty() && configurable.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            Text(stringResource(R.string.sp_empty), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (hiddenCount > 0) stringResource(R.string.sp_hidden_count, hiddenCount)
                else stringResource(R.string.sp_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn {
        if (staticShortcuts.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sp_static_section)) }
            items(staticShortcuts, key = { "s:${it.id}" }) { shortcut ->
                ListItem(
                    leadingContent = { AppIcon(shortcut.icon ?: app.icon) },
                    headlineContent = {
                        Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStaticSelect(shortcut) },
                )
            }
        }
        if (configurable.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sp_configurable_section)) }
            items(configurable, key = { "c:${it.launchIntent.component}" }) { source ->
                ListItem(
                    leadingContent = { AppIcon(source.icon ?: app.icon) },
                    headlineContent = {
                        Text(source.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.sp_configurable_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfigurableSelect(source) },
                )
            }
        }
        if (hiddenCount > 0) {
            item {
                Text(
                    stringResource(R.string.sp_hidden_count, hiddenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, size: Int = 40) {
    if (drawable != null) {
        Image(
            bitmap = drawable.toBitmapSafe().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp),
        )
    } else {
        Box(Modifier.size(size.dp))
    }
}

internal fun loadLaunchableApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    val launchablePackages = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        0,
    ).map { it.activityInfo.packageName }.toSet()

    return launchablePackages
        .mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                InstalledApp(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(pkg),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

private fun Drawable.toBitmapSafe(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
