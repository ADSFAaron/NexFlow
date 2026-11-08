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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.nexflow.R
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsSwitchItem
import com.nexflow.ui.common.SettingsTopAppBar
import com.nexflow.ui.theme.NexFlowTheme

/**
 * The Android 16-style settings list the Settings, About and Global variables pages are built
 * from. SettingsScreen itself takes Hilt view models, so this assembles a page from the same
 * components and the same strings: the large title, a granted and a missing permission (the
 * warning mark is the one coloured thing on the page), switches on and off with their thumb
 * marks, and a group with a footer — the longest text here, so the one the locales stress.
 *
 * The top of the page is what is in view, which is all that is needed: the group shapes, the
 * gaps and the page/tile contrast are the same all the way down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@LocaleThemePreviews
@Composable
fun SettingsListPage() {
    NexFlowTheme(dynamicColor = false) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            containerColor = SettingsDefaults.pageColor,
            topBar = { SettingsTopAppBar(stringResource(R.string.settings_title), scrollBehavior) },
        ) { innerPadding ->
            LazyColumn(contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = 24.dp)) {
                item {
                    SettingsGroup(stringResource(R.string.settings_section_general)) {
                        item { shapes ->
                            SettingsItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_language),
                                summary = stringResource(R.string.language_system_default),
                                leading = { Icon(Icons.Outlined.Language, contentDescription = null) },
                                onClick = {},
                            )
                        }
                    }
                }
                item {
                    SettingsGroup(stringResource(R.string.settings_section_permissions)) {
                        item { shapes ->
                            SettingsItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_notifications),
                                summary = stringResource(R.string.settings_granted),
                                leading = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                                onClick = {},
                            )
                        }
                        item { shapes ->
                            SettingsItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_accessibility),
                                summary = stringResource(R.string.settings_accessibility_required),
                                leading = { Icon(Icons.Outlined.AccessibilityNew, contentDescription = null) },
                                trailing = {
                                    Icon(
                                        Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {},
                            )
                        }
                        item { shapes ->
                            SettingsItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_adb),
                                summary = stringResource(R.string.settings_adb_summary),
                                leading = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                                onClick = {},
                            )
                        }
                    }
                }
                item {
                    SettingsGroup(stringResource(R.string.settings_section_automation)) {
                        item { shapes ->
                            SettingsSwitchItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_autostart),
                                summary = stringResource(R.string.settings_autostart_desc),
                                leading = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                                checked = false,
                                onCheckedChange = {},
                            )
                        }
                        item { shapes ->
                            SettingsSwitchItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_execution_toast),
                                summary = stringResource(R.string.settings_execution_toast_desc),
                                leading = { Icon(Icons.Outlined.Campaign, contentDescription = null) },
                                checked = true,
                                onCheckedChange = {},
                            )
                        }
                    }
                }
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
                                checked = true,
                                onCheckedChange = {},
                            )
                        }
                        item { shapes ->
                            SettingsItem(
                                shapes = shapes,
                                title = stringResource(R.string.settings_log_retention),
                                summary = "30 · 200",
                                leading = { Icon(Icons.Outlined.History, contentDescription = null) },
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A group with a footer, on its own: the footer is the longest text on the Settings page and
 * sits below the fold of the page preview above, so it gets a frame of its own.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun SettingsListGroupFooter() {
    NexFlowTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(SettingsDefaults.pageColor)) {
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
                        checked = false,
                        onCheckedChange = {},
                    )
                }
                item { shapes ->
                    SettingsItem(
                        shapes = shapes,
                        title = stringResource(R.string.settings_log_retention),
                        summary = "30 · 200",
                        leading = { Icon(Icons.Outlined.History, contentDescription = null) },
                        onClick = {},
                    )
                }
            }
        }
    }
}
