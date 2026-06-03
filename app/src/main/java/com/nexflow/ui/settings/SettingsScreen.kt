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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var autoStart by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {
            item {
                SectionHeader("Automation")
            }
            item {
                ListItem(
                    headlineContent = { Text("Auto-start on boot") },
                    supportingContent = { Text("Resume enabled flows when device starts") },
                    trailingContent = {
                        Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Log retention") },
                    supportingContent = { Text("Keep the last 200 execution logs") },
                )
            }
            item { HorizontalDivider() }
            item {
                SectionHeader("About")
            }
            item {
                ListItem(
                    headlineContent = { Text("NexFlow") },
                    supportingContent = { Text("Version 1.0.0 · Open-source Android automation") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Licence") },
                    supportingContent = { Text("Apache License 2.0") },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
