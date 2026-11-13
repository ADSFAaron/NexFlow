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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsGroupTitle
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsRadioItem
import com.nexflow.ui.common.SettingsTopAppBar
import com.nexflow.ui.common.settingsItemPadding
import com.nexflow.ui.common.settingsItemShapes
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
        // Draw behind the status and navigation bars like MainActivity, so the page colour
        // reaches the screen edges; Android 15+ forces this, but on 12-14 the bars stay black.
        enableEdgeToEdge()

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        // The activity is exported (the launcher has to reach it), so the id is whatever the
        // caller sent. One that is not a NexFlow widget makes Glance's getGlanceIdBy throw
        // "Invalid AppWidget ID" — close instead of crashing.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || provider?.packageName != packageName) {
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
                        onBack = ::finish,
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

/**
 * Internal so app/src/screenshotTest can render it. The mode is a radio pair on the page rather
 * than a segmented button above it, so each mode carries its own explanation; the flows to pick
 * are a second group of tiles below, shown only while picking by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigureScreen(
    flows: List<Flow>,
    manual: Boolean,
    picked: List<String>,
    onManualChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.widget_overview_configure_title),
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
        bottomBar = {
            // On the page colour, not a raised tonal bar: the button is the page's last word,
            // not a separate toolbar.
            Surface(color = SettingsDefaults.pageColor) {
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
        // Picked flows first, in the order they will sit on the widget; the rest below in the
        // app's own order.
        val byId = flows.associateBy { it.id }
        val ordered = picked.mapNotNull { byId[it] } + flows.filter { it.id !in picked }
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "mode") {
                SettingsGroup(title = null, modifier = Modifier.selectableGroup().padding(top = 8.dp)) {
                    item { shapes ->
                        SettingsRadioItem(
                            shapes = shapes,
                            title = stringResource(R.string.widget_overview_mode_auto),
                            summary = stringResource(R.string.widget_overview_mode_auto_hint),
                            selected = !manual,
                            onClick = { onManualChange(false) },
                        )
                    }
                    item { shapes ->
                        SettingsRadioItem(
                            shapes = shapes,
                            title = stringResource(R.string.widget_overview_mode_manual),
                            summary = stringResource(
                                R.string.widget_overview_mode_manual_hint,
                                picked.size,
                                NexFlowWidget.MAX_FLOWS,
                            ),
                            selected = manual,
                            onClick = { onManualChange(true) },
                        )
                    }
                }
            }

            if (manual) {
                if (flows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.widget_no_flows),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    item(key = "flows_title") {
                        SettingsGroupTitle(
                            stringResource(R.string.widget_overview_edit),
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                        )
                    }
                    itemsIndexed(ordered, key = { _, flow -> flow.id }) { index, flow ->
                        val position = picked.indexOf(flow.id)
                        PickRow(
                            flow = flow,
                            shapes = settingsItemShapes(index, ordered.size),
                            position = position.takeIf { it >= 0 }?.plus(1),
                            enabled = position >= 0 || picked.size < NexFlowWidget.MAX_FLOWS,
                            onToggle = { onToggle(flow.id) },
                            modifier = Modifier.animateItem().settingsItemPadding(index),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    flow: Flow,
    shapes: ListItemShapes,
    position: Int?,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = position != null
    SettingsItem(
        shapes = shapes,
        title = flow.name,
        summary = position?.let { stringResource(R.string.widget_overview_position, it) },
        enabled = enabled,
        leading = {
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
        // The tile is the toggle; the checkbox only shows its state, so TalkBack gets one stop
        // that reads the name and whether it is ticked.
        trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        onClick = onToggle,
        modifier = modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(checked)
        },
    )
}
