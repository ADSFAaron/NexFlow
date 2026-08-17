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

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.nexflow.R
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.ui.flows.detail.config.info
import java.text.SimpleDateFormat
import java.util.Date

/**
 * One run, step by step — the screen a failing flow sends the user to.
 *
 * The list is the run as it actually happened, not the flow as it is now: rows come from the
 * recorded steps, so a REPEAT appears once per round and an action added since the run does not
 * appear at all. That is the point — the flow may have been edited, and a log that re-derived
 * itself from the current flow would quietly describe a run that never took place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    onBack: () -> Unit,
    vm: RunDetailViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Which REPEAT rows have had their later rounds opened, keyed by action id. Survives
    // recomposition but not the screen: a fresh visit starts collapsed, which is the reading order.
    val expandedRepeats = remember { mutableStateMapOf<String, Boolean>() }
    val expandedSteps = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail.flowName.ifEmpty { stringResource(R.string.run_detail_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val log = detail.log
        when {
            detail.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            log == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.run_detail_gone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }

            else -> {
                val rows = remember(detail.steps, expandedRepeats.toMap()) {
                    visibleRows(detail.steps, expandedRepeats)
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { RunSummary(detail) }

                    if (detail.steps.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.run_no_steps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    items(rows, key = { it.step.seq }) { row ->
                        StepRow(
                            row = row,
                            expanded = expandedSteps[row.step.seq] == true,
                            repeatExpanded = expandedRepeats[row.step.actionId] == true,
                            onToggle = {
                                if (row.isRepeatHeader) {
                                    expandedRepeats[row.step.actionId] =
                                        expandedRepeats[row.step.actionId] != true
                                } else {
                                    expandedSteps[row.step.seq] = expandedSteps[row.step.seq] != true
                                }
                            },
                        )
                    }

                    if (detail.droppedSteps > 0) {
                        item {
                            Text(
                                pluralStringResource(
                                    R.plurals.run_steps_truncated,
                                    detail.droppedSteps,
                                    detail.droppedSteps,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A step plus what the list needs to draw it that the step alone does not say. */
internal data class StepRow(
    val step: ExecutionStep,
    val isRepeatHeader: Boolean,
    /** Rounds this REPEAT ran, for its header row; 0 for anything else. */
    val repeatRounds: Int,
)

/**
 * Flattens the recorded steps into the rows to draw, hiding every repeat round after the first
 * until its REPEAT row is opened.
 *
 * Hiding by depth watermark rather than by iteration alone is what makes nesting behave: when a
 * collapsed round is skipped, everything nested inside it goes with it — an inner loop's own
 * "round 0" steps belong to a round the user has not asked to see.
 */
internal fun visibleRows(
    steps: List<ExecutionStep>,
    expandedRepeats: Map<String, Boolean>,
): List<StepRow> {
    // Rounds per REPEAT, read off its note (`repeat:5`) so the header can say so without the flow.
    val rounds = steps.associate { step ->
        step.seq to step.note?.removePrefix(FlowInterpreter.NOTE_REPEAT)?.toIntOrNull()
    }
    val rows = mutableListOf<StepRow>()
    // Which REPEAT owns each depth, so a hidden round can be attributed to the row that unhides it.
    val repeatAtDepth = mutableMapOf<Int, String>()
    var hiddenBelow = Int.MAX_VALUE

    for (step in steps) {
        // Left the collapsed region: this step is shallower than the rounds being hidden.
        if (step.depth <= hiddenBelow) hiddenBelow = Int.MAX_VALUE
        if (hiddenBelow != Int.MAX_VALUE) continue

        val isRepeat = step.actionType == ActionType.REPEAT_BLOCK
        if (isRepeat) repeatAtDepth[step.depth + 1] = step.actionId

        if (step.iteration > 0 && expandedRepeats[repeatAtDepth[step.depth]] != true) {
            // Hide this row and everything nested under it, until the depth comes back up.
            hiddenBelow = step.depth - 1
            continue
        }
        rows += StepRow(
            step = step,
            isRepeatHeader = isRepeat && (rounds[step.seq] ?: 0) > 1,
            repeatRounds = if (isRepeat) rounds[step.seq] ?: 0 else 0,
        )
    }
    return rows
}

@Composable
private fun RunSummary(detail: RunDetail) {
    val log = detail.log ?: return
    val (icon, tint) = statusVisual(log.status)
    val locale = LocalLocale.current.platformLocale
    val format = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMdHHmmss"), locale)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (log.status) {
                            ExecutionStatus.SUCCESS -> R.string.log_status_success
                            ExecutionStatus.FAIL -> R.string.log_status_fail
                            ExecutionStatus.SKIPPED -> R.string.log_status_skipped
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "${format.format(Date(log.triggeredAt))} · ${formatDuration(log.executionDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val error = log.errorMessage
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    row: StepRow,
    expanded: Boolean,
    repeatExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val step = row.step
    val (icon, tint) = statusVisual(step.status)
    val detailText = step.errorMessage ?: step.resolvedConfig
    val canExpand = row.isRepeatHeader || detailText != null
    // Indent caps at three levels: past that the text column is narrower than the words in it,
    // and the guide lines already carry the nesting.
    val indent = (step.depth.coerceAtMost(MAX_INDENT_LEVELS) * INDENT_STEP.value).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (canExpand) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Guide lines instead of blank space: at three levels of nesting, counting indents by
            // eye is guesswork, and a run's log is read when something is already wrong.
            repeat(step.depth.coerceAtMost(MAX_INDENT_LEVELS)) {
                Box(
                    modifier = Modifier
                        .width(INDENT_STEP)
                        .height(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.actionType.info(context).label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val note = noteText(context, step, row.repeatRounds)
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (step.durationMs > 0) {
                Text(
                    formatDuration(step.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canExpand) {
                val open = if (row.isRepeatHeader) repeatExpanded else expanded
                val angle by animateFloatAsState(if (open) 90f else 0f, label = "step_chevron")
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(angle),
                )
            }
        }

        AnimatedVisibility(visible = expanded && detailText != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .padding(start = indent + 28.dp, top = 4.dp, bottom = 4.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    detailText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (step.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    // A resolved URL or a stack-shaped error must not be re-wrapped into
                    // unreadability, so the block scrolls sideways rather than the page.
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
        }
    }
}

/** The localized remark for a step, from its `note` token — see [ExecutionStep.note]. */
private fun noteText(context: Context, step: ExecutionStep, repeatRounds: Int): String? {
    val note = step.note ?: return null
    return when {
        note == FlowInterpreter.NOTE_DISABLED -> context.getString(R.string.run_step_disabled)
        note == FlowInterpreter.NOTE_IF_TRUE -> context.getString(R.string.run_step_if_true)
        note == FlowInterpreter.NOTE_IF_FALSE -> context.getString(R.string.run_step_if_false)
        note.startsWith(FlowInterpreter.NOTE_REPEAT) ->
            context.resources.getQuantityString(R.plurals.run_step_repeat, repeatRounds, repeatRounds)
        note.startsWith(FlowInterpreter.NOTE_MENU) ->
            context.getString(R.string.run_step_menu, note.removePrefix(FlowInterpreter.NOTE_MENU))
        // A token this build does not recognize is still better shown than swallowed.
        else -> note
    }
}

@Composable
private fun statusVisual(status: ExecutionStatus): Pair<ImageVector, Color> = when (status) {
    // Tertiary for success matches LogsScreen — the M3 scheme's green-teal, never a hardcoded one.
    ExecutionStatus.SUCCESS -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary
    ExecutionStatus.FAIL -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    ExecutionStatus.SKIPPED -> Icons.Filled.RemoveCircle to MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "${ms}ms" else "${"%.1f".format(ms / 1000.0)}s"

private val INDENT_STEP = 16.dp
private const val MAX_INDENT_LEVELS = 3
