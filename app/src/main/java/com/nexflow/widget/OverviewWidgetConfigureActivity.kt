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
package com.nexflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lets the user decide what one overview widget shows: the flows they ran recently (the
 * behaviour every overview widget had before this screen existed), or a list they pick and order
 * themselves. Each widget keeps its own choice, so one can follow recent runs while another
 * holds the three flows the user always wants in reach.
 */
@AndroidEntryPoint
class OverviewWidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var flowRepository: FlowRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val resultData = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Backing out of the first configuration on API 30 (where it cannot be optional) must
        // not leave a widget behind; on a reconfigure the result is ignored.
        setResult(RESULT_CANCELED, resultData)

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@OverviewWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
            val stored = getAppWidgetState(
                this@OverviewWidgetConfigureActivity,
                PreferencesGlanceStateDefinition,
                glanceId,
            )
            val initialPick = NexFlowWidget.decodePinnedIds(stored[NexFlowWidget.KEY_PINNED_IDS])

            setContent {
                NexFlowTheme {
                    val flows by flowRepository.observeAll().collectAsState(emptyList())
                    var manual by rememberSaveable { mutableStateOf(initialPick != null) }
                    var picked by rememberSaveable { mutableStateOf(initialPick.orEmpty()) }

                    ConfigureScreen(
                        flows = flows,
                        manual = manual,
                        picked = picked,
                        onManualChange = { manual = it },
                        onToggle = { id ->
                            picked = when {
                                id in picked -> picked - id
                                picked.size >= NexFlowWidget.MAX_FLOWS -> picked
                                else -> picked + id
                            }
                        },
                        onSave = {
                            lifecycleScope.launch {
                                val all = flowRepository.observeAll().first()
                                val byId = all.associateBy { it.id }
                                val recent = flowRepository.observeRecentLogs(limit = RECENT_LOG_LIMIT).first()
                                    .map { it.flowId }
                                    .distinct()
                                    .mapNotNull { byId[it] }
                                NexFlowWidget.configure(
                                    context = this@OverviewWidgetConfigureActivity,
                                    id = glanceId,
                                    pinnedIds = if (manual) picked.filter { it in byId } else null,
                                    recent = recent,
                                    all = all,
                                )
                                setResult(RESULT_OK, resultData)
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    private companion object {
        /** Same window [com.nexflow.shortcut.ShortcutSyncManager] reads recent runs from. */
        const val RECENT_LOG_LIMIT = 20
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(
    flows: List<Flow>,
    manual: Boolean,
    picked: List<String>,
    onManualChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_overview_configure_title)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.widget_overview_save))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = !manual,
                    onClick = { onManualChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.widget_overview_mode_auto)) }
                SegmentedButton(
                    selected = manual,
                    onClick = { onManualChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.widget_overview_mode_manual)) }
            }
            Text(
                text = if (manual) {
                    stringResource(R.string.widget_overview_mode_manual_hint, picked.size, NexFlowWidget.MAX_FLOWS)
                } else {
                    stringResource(R.string.widget_overview_mode_auto_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (manual) {
                if (flows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.widget_no_flows),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Picked flows first, in the order they will sit on the widget; the rest
                    // below in the app's own order.
                    val byId = flows.associateBy { it.id }
                    val ordered = picked.mapNotNull { byId[it] } + flows.filter { it.id !in picked }
                    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(ordered, key = { it.id }) { flow ->
                            val position = picked.indexOf(flow.id)
                            PickRow(
                                flow = flow,
                                position = position.takeIf { it >= 0 }?.plus(1),
                                enabled = position >= 0 || picked.size < NexFlowWidget.MAX_FLOWS,
                                onToggle = { onToggle(flow.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    flow: Flow,
    position: Int?,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.toggleable(
            value = position != null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onToggle() },
        ),
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(FlowIcons.color(flow.iconColor) ?: MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    FlowIcons.vector(flow.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        headlineContent = { Text(flow.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = position?.let {
            { Text(stringResource(R.string.widget_overview_position, it)) }
        },
        trailingContent = {
            // The row is the toggle; the checkbox only shows its state.
            Checkbox(checked = position != null, onCheckedChange = null, enabled = enabled)
        },
    )
}
