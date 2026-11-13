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

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.common.FlowPickerPage
import com.nexflow.ui.theme.NexFlowTheme

/**
 * The pages behind placing a widget or the Quick Settings tile. Flow names are user data; what
 * the locales exercise is the chrome — the title, and on the overview page the two modes'
 * explanations, the longest translated text on either page.
 */

private fun flow(
    id: String,
    name: String,
    description: String = "",
    icon: String = "bolt",
    color: String = "#FF6750A4",
) = Flow(
    id = id,
    schemaVersion = 1,
    name = name,
    description = description,
    author = null,
    icon = icon,
    iconColor = color,
    tags = emptyList(),
    enabled = true,
    createdAt = 0L,
    updatedAt = 0L,
    triggers = emptyList(),
    triggerLogic = TriggerLogic.ANY,
    conditions = emptyList(),
    actions = emptyList(),
    variables = emptyList(),
)

private val flows = listOf(
    // Long enough to wrap past two lines: the picker cuts a description at two.
    flow(
        "a",
        "Morning routine",
        "Turn on Wi-Fi, read out today's weather and calendar, raise the media volume, " +
            "open the news app and start the coffee machine through its web hook",
    ),
    flow("b", "Arrive at the office", "Geofence entry, weekdays only", icon = "work", color = "#FF00658E"),
    flow("c", "Bedtime", icon = "bedtime", color = "#FF7D5260"),
    flow("d", "Charging reminder", "Unplug at 80% to save the battery"),
)

/** Reconfiguring a widget or tile: the flow it runs now is the one already marked. */
@PreviewTest
@LocaleThemePreviews
@Composable
fun FlowPickerReconfigure() {
    NexFlowTheme(dynamicColor = false) {
        FlowPickerPage(flows = flows, selectedId = "b", onPick = {}, onBack = {})
    }
}

/** Picking by hand, two flows ticked: they move to the top, numbered in widget order. */
@PreviewTest
@LocaleThemePreviews
@Composable
fun OverviewWidgetPickByHand() {
    NexFlowTheme(dynamicColor = false) {
        ConfigureScreen(
            flows = flows,
            manual = true,
            picked = listOf("c", "a"),
            onManualChange = {},
            onToggle = {},
            onSave = {},
            onBack = {},
        )
    }
}
