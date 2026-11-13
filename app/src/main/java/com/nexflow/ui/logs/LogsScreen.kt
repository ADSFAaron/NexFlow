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
package com.nexflow.ui.logs

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nexflow.R
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroupTitle
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsTopAppBar
import com.nexflow.ui.common.settingsItemPadding
import com.nexflow.ui.common.settingsItemShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(
    onEntryClick: (logId: String) -> Unit = {},
    vm: LogsViewModel = hiltViewModel(),
) {
    val entries by vm.logEntries.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(entries, zone) { groupByDay(entries.orEmpty(), zone) }
    // Re-read on resume: left open overnight, "Today" would otherwise stay on yesterday's runs
    // until the next run happened to recompose the list.
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    LifecycleResumeEffect(zone) {
        today = LocalDate.now(zone)
        onPauseOrDispose {}
    }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.logs_clear_q)) },
            text = { Text(stringResource(R.string.logs_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    showClearDialog = false
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    LogsContent(
        days = days,
        loading = entries == null,
        today = today,
        zone = zone,
        onEntryClick = onEntryClick,
        onClearAll = { showClearDialog = true },
    )
}

/**
 * The page itself, without the view model — internal so app/src/screenshotTest can render it
 * with a fixed [today] and [zone], which is what keeps "Today" / "Yesterday" from changing
 * under the reference images.
 *
 * Runs are grouped by day the way Android 16's notification history and settings lists group
 * theirs: a heading per day and the day's runs as one block of tiles, instead of a single
 * divided list where every row repeated its date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsContent(
    days: List<LogDay>,
    today: LocalDate,
    zone: ZoneId,
    onEntryClick: (logId: String) -> Unit,
    onClearAll: () -> Unit,
    /** The log is still being read: show nothing yet rather than claim there is nothing. */
    loading: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // LocalLocale is observable state, unlike Locale.getDefault(): the list reformats itself when
    // the app language changes. Patterns come from skeletons rather than being hardcoded, so
    // ja/zh get 11月6日(金) rather than English field order.
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(locale, zone) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "HHmmss"), locale).withZone(zone)
    }
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale)
    }
    val dateWithYearFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMEd"), locale)
    }
    val todayLabel = stringResource(R.string.logs_today)
    val yesterdayLabel = stringResource(R.string.logs_yesterday)
    fun dayLabel(date: LocalDate): String = when {
        date == today -> todayLabel
        date == today.minusDays(1) -> yesterdayLabel
        date.year == today.year -> dateFormat.format(date)
        else -> dateWithYearFormat.format(date)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.logs_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    if (days.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.logs_clear_all))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Crossfade empty state ↔ list (see FlowsScreen for the contentKey rationale).
        val contentFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = days,
            contentKey = { it.isEmpty() },
            transitionSpec = { fadeIn(contentFade) togetherWith fadeOut(contentFade) },
            label = "logs_content",
        ) { currentDays ->
            if (currentDays.isEmpty()) {
                if (!loading) EmptyLogsContent(modifier = Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Each run stays its own lazy item (up to 200 of them) rather than one
                    // SettingsGroup per day composed all at once; the group's shapes and gaps
                    // are applied item by item instead.
                    currentDays.forEach { day ->
                        item(key = "day-${day.date}", contentType = "day") {
                            SettingsGroupTitle(
                                title = dayLabel(day.date),
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                            )
                        }
                        itemsIndexed(
                            day.entries,
                            key = { _, entry -> entry.log.id },
                            contentType = { _, _ -> "run" },
                        ) { index, entry ->
                            // animateItem: new runs slide in at the top instead of popping.
                            LogEntryItem(
                                entry = entry,
                                shapes = settingsItemShapes(index, day.entries.size),
                                time = timeFormat.format(Instant.ofEpochMilli(entry.log.triggeredAt)),
                                onClick = { onEntryClick(entry.log.id) },
                                modifier = Modifier.animateItem().settingsItemPadding(index),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsContent(modifier: Modifier = Modifier) {
    // Drive the bounce ourselves: a bouncy spring overshoots past scale 1.0, and
    // AnimatedVisibility clips that overshoot to the content's settled bounds — cutting off
    // the edges of the text. A graphicsLayer with clip = false lets the scaled-up content
    // draw in full.
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    // Theme motion tokens: scale is spatial (may overshoot), fade is an effect (no bounce).
    val scaleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, scaleSpec) }
        launch { alpha.animateTo(1f, alphaSpec) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                clip = false
            },
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.logs_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.logs_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: LogEntry,
    shapes: ListItemShapes,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint) = when (entry.log.status) {
        // Use tertiary (green-teal) from the M3 color scheme for success — avoids hardcoded color
        ExecutionStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary
        ExecutionStatus.FAIL -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        ExecutionStatus.SKIPPED -> Icons.Filled.RemoveCircle to MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The icon is the only thing conveying the result, so TalkBack needs a localized
    // status here — the raw enum name would be read out in English on every locale.
    val statusDescription = stringResource(
        when (entry.log.status) {
            ExecutionStatus.SUCCESS -> R.string.log_status_success
            ExecutionStatus.FAIL -> R.string.log_status_fail
            ExecutionStatus.SKIPPED -> R.string.log_status_skipped
        },
    )
    val duration = if (entry.log.executionDurationMs < 1000) {
        "${entry.log.executionDurationMs}ms"
    } else {
        "${"%.1f".format(entry.log.executionDurationMs / 1000.0)}s"
    }

    // The whole tile opens the run's steps. A summary alone cannot say which of a flow's
    // actions failed, and that is the question this list gets opened to answer. No chevron:
    // as in the settings pages, the tile being a tile is what says it opens something.
    SettingsItem(
        shapes = shapes,
        title = entry.flowName,
        leading = { Icon(icon, contentDescription = statusDescription, tint = tint) },
        summaryContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("$time · $duration")
                val errorMessage = LogMessages.render(LocalContext.current, entry.log.errorMessage)
                if (entry.log.status == ExecutionStatus.FAIL && errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}
