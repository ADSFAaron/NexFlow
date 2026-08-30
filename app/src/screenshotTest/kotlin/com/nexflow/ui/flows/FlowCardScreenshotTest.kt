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
package com.nexflow.ui.flows

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.PreviewSurface

/**
 * The Flows list row — the app's primary surface, and the one place a layout regression is
 * visible on every launch.
 *
 * Most of a row's text is user data rather than resources, so unlike
 * [com.nexflow.ui.onboarding.OnboardingScreenScreenshotTest] the four locales here are mainly
 * guarding the theme and the layout under pressure, not translation length. Two variants only:
 * the ordinary row, and the row carrying everything at once.
 */

private fun sampleFlow(
    name: String,
    description: String,
    enabled: Boolean = true,
    icon: String? = "Bolt",
    iconColor: String? = "#FF6750A4",
) = Flow(
    id = "preview",
    schemaVersion = 1,
    name = name,
    description = description,
    author = null,
    icon = icon,
    iconColor = iconColor,
    tags = emptyList(),
    enabled = enabled,
    createdAt = 0L,
    updatedAt = 0L,
    triggers = emptyList(),
    triggerLogic = TriggerLogic.ANY,
    conditions = emptyList(),
    actions = emptyList(),
    variables = emptyList(),
)

@Composable
private fun FlowCardPreview(
    flow: Flow,
    permissionWarning: Boolean = false,
    running: Boolean = false,
) {
    PreviewSurface {
        FlowCard(
            flow = flow,
            permissionWarning = permissionWarning,
            onClick = {},
            onToggle = {},
            onRun = {},
            onWarningClick = {},
            running = running,
        )
    }
}

@PreviewTest
@LocaleThemePreviews
@Composable
fun FlowCardEnabled() {
    FlowCardPreview(
        sampleFlow(
            name = "Morning routine",
            description = "Turn on Wi-Fi and read out today's weather",
        ),
    )
}

/**
 * Everything at once: disabled (the card dims, which is where dark-mode contrast slips), no
 * icon, a name long enough to wrap, and the missing-permission warning — the only string in
 * this row that comes from resources, so this is also the variant the four locales exercise.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun FlowCardDisabledWithWarning() {
    FlowCardPreview(
        flow = sampleFlow(
            name = "Arrive at the office and switch the phone to work mode",
            description = "Geofence entry, weekdays only, mutes notifications from personal apps",
            enabled = false,
            icon = null,
            iconColor = null,
        ),
        permissionWarning = true,
    )
}

/**
 * The running state: the Run button becomes a spinner plus a label, which is the row's widest
 * badge and the one place a long translation of "Running" could collide with the trigger chips.
 *
 * layoutlib renders the indeterminate spinner at animation frame 0, so in the reference image it
 * is a bare sliver rather than an arc. That is stable across runs (the images are byte-identical
 * on a re-render) — it is the still frame of a real animation, not a broken indicator.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun FlowCardRunning() {
    FlowCardPreview(
        flow = sampleFlow(
            name = "Morning routine",
            description = "Turn on Wi-Fi and read out today's weather",
        ),
        running = true,
    )
}
