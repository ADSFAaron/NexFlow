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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexflow.R

/*
 * Settings pages in the Android 16+ style: each section is a group of rounded tiles a step
 * brighter than the page behind them, separated by a hairline gap instead of dividers, with the
 * first and last tile of a group carrying the large corners. Android 17 kept this look (its
 * QPR1 only restored 16's spacing after 17.0 had tightened it), so it is the target for every
 * settings-like page in the app: Settings itself, About, and Global variables.
 *
 * Built on Material3's SegmentedListItem and ListItemDefaults.segmentedShapes/SegmentedGap,
 * following the official SegmentedListItems sample, rather than on hand-drawn cards.
 */

object SettingsDefaults {
    /*
     * Light: white-ish tiles on a tinted page. Dark: tiles only a little lighter than a dark
     * page. surfaceBright on surfaceContainer would be "brighter tile" in both, but in dark it is
     * a pale grey slab — Android 16's dark settings keep the tiles dark. Which one applies is read
     * off the scheme itself, so it follows dynamic colour and the app's own theme switch alike.
     */
    private val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

    /** The page behind the tiles. */
    val pageColor: Color
        @Composable get() = MaterialTheme.colorScheme.run { if (isDark) surface else surfaceContainer }

    /**
     * Tiles sit one step brighter than [pageColor]. Also the colour for a card standing on the
     * page in place of a tile (the Flows list), so every tab's foreground reads the same.
     */
    val tileColor: Color
        @Composable get() = MaterialTheme.colorScheme.run { if (isDark) surfaceContainerHigh else surfaceBright }

    @Composable
    fun itemColors(): ListItemColors = ListItemDefaults.segmentedColors(containerColor = tileColor)

    /** The bar takes the page colour scrolled or not, so it reads as part of the page. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun topAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = pageColor,
        scrolledContainerColor = pageColor,
    )
}

/**
 * The large title that collapses as the page scrolls. [onBack] puts the back arrow in a tonal
 * circle, as Android 16's own settings pages do; a tab's root page passes null and has none.
 * [actions] is for the page's own commands (Logs' clear, Flows' AI and service capsule).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
        colors = SettingsDefaults.topAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

/** Collects a group's tiles so each can be given its shape from its position in the group. */
class SettingsGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    /** One tile. [content] receives the shape for its position; pass it to the list item. */
    fun item(content: @Composable (shapes: ListItemShapes) -> Unit) {
        items += content
    }
}

/**
 * One section: an optional [title] above, the tiles, and an optional [footer] below for an
 * explanation too long to be any one tile's summary.
 *
 * Tiles are declared in [content] with `item { shapes -> ... }`, and may be conditional — the
 * shapes are worked out from the tiles actually present, so a hidden row never leaves the group
 * with a square corner at its end.
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: SettingsGroupScope.() -> Unit,
) {
    val items = SettingsGroupScope().apply(content).items
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) SettingsGroupTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                item(ListItemDefaults.segmentedShapes(index = index, count = items.size))
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/**
 * A group's heading, on its own. [SettingsGroup] draws it; a list too long to compose in one
 * go (Logs) draws it as a lazy item and gives each tile its shape with [settingsItemShapes] /
 * [settingsItemPadding], so the group looks the same while its rows stay lazy.
 */
@Composable
fun SettingsGroupTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** The shape of the tile at [index] of [count] in a group laid out item by item. */
@Composable
fun settingsItemShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.segmentedShapes(index = index, count = count)

/** The page margin and the gap above every tile but a group's first, as [SettingsGroup] spaces them. */
fun Modifier.settingsItemPadding(index: Int): Modifier = padding(
    start = 16.dp,
    end = 16.dp,
    top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp,
)

/**
 * A plain tile: tapping it does [onClick], or nothing at all when [onClick] is null.
 *
 * There is deliberately no slot for a button: in a settings list the row *is* the button.
 * [trailing] is for status (a warning, an external-link mark), not for a second action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsItem(
    shapes: ListItemShapes,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    /** In place of [summary], for a summary that is more than one plain string. */
    summaryContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val supporting: (@Composable () -> Unit)? = summary?.let { { Text(it) } } ?: summaryContent
    // Centred, not M3's top alignment for multi-line items: with summaries of two or three
    // lines, a top-pinned icon and switch drift away from the row they belong to. Android 16's
    // settings centre them.
    val alignment = Alignment.CenterVertically
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            verticalAlignment = alignment,
            colors = SettingsDefaults.itemColors(),
            content = { Text(title) },
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            verticalAlignment = alignment,
            colors = SettingsDefaults.itemColors(),
            content = { Text(title) },
        )
    }
}

/**
 * A tile that is an on/off setting. The whole tile toggles, and it is announced as a switch.
 *
 * SegmentedListItem's own checked/onCheckedChange overload is not used: it announces the row
 * as a checkbox and paints it in the selected colours whenever it is on — right for picking
 * items out of a list, wrong for a setting. The clickable overload with switch semantics added
 * is what a settings toggle is.
 */
@Composable
fun SettingsSwitchItem(
    shapes: ListItemShapes,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        shapes = shapes,
        title = title,
        summary = summary,
        leading = leading,
        enabled = enabled,
        // onCheckedChange = null: the tile is the one toggle, so TalkBack gets one focus stop
        // that reads the label and the state together.
        trailing = { SettingsSwitch(checked = checked, enabled = enabled) },
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
    )
}

/**
 * One choice of several, made right on the page rather than in a dialog. Put the group in a
 * `Modifier.selectableGroup()` so TalkBack reads "n of m". As in the official single-selection
 * sample: the radio is display-only, the tile is the target, and the chosen tile takes the
 * selected colour, so which one is on reads at a glance without hunting for the small dot.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRadioItem(
    shapes: ListItemShapes,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    /** For a summary that is user text of any length (a flow's description), not a fixed hint. */
    summaryMaxLines: Int = Int.MAX_VALUE,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = summary?.let {
            { Text(it, maxLines = summaryMaxLines, overflow = TextOverflow.Ellipsis) }
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = SettingsDefaults.itemColors(),
        content = { Text(title) },
    )
}

/**
 * The colours for a plain [androidx.compose.material3.ListItem] inside a dialog or bottom sheet.
 * ListItem paints its own `surface` container, which inside a dialog's brighter
 * surfaceContainerHigh (or a sheet's surfaceContainerLow) shows up as a darker band behind the
 * options. Transparent lets the options share their container's colour.
 */
@Composable
fun ListItemDefaults.inContainerColors(): ListItemColors = colors(containerColor = Color.Transparent)

/**
 * The switch with a mark in its thumb — a check when on, a cross when off — as Android 16's
 * settings draw it. Display only by default, the tile around it handling the toggle; a card
 * whose own click does something else (a flow card opens the flow) passes [onCheckedChange].
 */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            // Not focusable, so no content description (as in the official thumb-icon sample).
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
