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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexflow.R
import com.nexflow.core.automation.model.Flow

/**
 * "Choose a flow" for a home-screen widget or the Quick Settings tile: one choice out of the
 * user's flows, made on a settings-style page. Tapping a flow is the whole interaction — the
 * caller saves it and closes — so there is no separate confirm button. [selectedId] marks what
 * this widget or tile runs now, so reconfiguring one shows where it starts from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPickerPage(
    flows: List<Flow>,
    selectedId: String?,
    onPick: (Flow) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.widget_configure_title),
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (flows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.widget_no_flows),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                // TalkBack reads "n of m" across the radio tiles.
                modifier = Modifier.fillMaxSize().selectableGroup(),
            ) {
                itemsIndexed(flows, key = { _, flow -> flow.id }) { index, flow ->
                    SettingsRadioItem(
                        shapes = settingsItemShapes(index, flows.size),
                        title = flow.name,
                        summary = flow.description.takeIf { it.isNotBlank() },
                        // Descriptions run to five lines; at two the list stays something to scan.
                        summaryMaxLines = 2,
                        selected = flow.id == selectedId,
                        onClick = { onPick(flow) },
                        modifier = Modifier.settingsItemPadding(index),
                    )
                }
            }
        }
    }
}
