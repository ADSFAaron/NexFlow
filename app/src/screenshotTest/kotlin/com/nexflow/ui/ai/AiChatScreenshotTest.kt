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
package com.nexflow.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.PreviewSurface
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.TriggerJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.nexflow.R

/**
 * The AI chat composer in each of the states it moves through, stacked so one image shows them
 * side by side: empty (no send button — it arrives with the first character), one line typed,
 * a draft long enough to wrap (the buttons must stay pinned to the bottom edge), and a turn in
 * progress (send has become stop, attach and mic are disabled).
 *
 * The placeholder is the only resource string here, so the four locales mainly guard that it
 * fits beside the buttons; the dark variants guard the surface against the screen behind it.
 */
@Composable
private fun Bar(text: String, isThinking: Boolean = false) {
    ChatInputBar(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = {},
        pendingImages = emptyList(),
        onRemoveImage = {},
        isThinking = isThinking,
        isListening = false,
        canSend = text.isNotBlank(),
        onAttach = {},
        onMic = {},
        onSendOrStop = {},
    )
}

@PreviewTest
@LocaleThemePreviews
@Composable
fun ChatInputBarStates() {
    PreviewSurface(padding = 0.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Bar("")
            Bar("Mute my phone at 11pm")
            Bar(
                "When I get to the office, turn on Do Not Disturb and open my calendar.\n" +
                    "Weekdays only, and skip public holidays.",
            )
            Bar("Mute my phone at 11pm", isThinking = true)
        }
    }
}

/**
 * A new chat before anything is said: the greeting headline, the suggestions under it, and
 * the composer at the bottom — the whole blank page, since what matters is how the three sit
 * together. The suggestions are resource strings, so the locales check that the longest of
 * them still reads as a single tappable card.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun AiChatEmptyState() {
    // On the page colour the chat really uses, since the suggestion tiles' contrast against it
    // is what this guards.
    PreviewSurface(padding = 0.dp, background = { SettingsDefaults.pageColor }) {
        Column {
            GreetingContent(
                suggestions = stringArrayResource(R.array.ai_suggestions_new).toList(),
                onPick = {},
                modifier = Modifier.weight(1f),
            )
            Bar("")
        }
    }
}

/**
 * The flow-edit chat's openers, which follow the assistant's intro as quick replies. The four
 * are of different lengths per locale, so this is where the wrap onto a second row shows up.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun AiChatQuickReplies() {
    PreviewSurface {
        QuickReplies(
            suggestions = stringArrayResource(R.array.ai_suggestions_edit).toList(),
            onPick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun config(vararg pairs: Pair<String, String>) =
    JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

private fun flowJson(actions: List<ActionJson>) = FlowJson(
    schemaVersion = 1,
    id = "drive",
    name = "Driving mode",
    description = "Turn the media volume up and start the music",
    tags = emptyList(),
    enabled = false,
    createdAt = "",
    updatedAt = "",
    triggers = listOf(TriggerJson("t", "MANUAL", JsonObject(emptyMap()))),
    triggerLogic = "ANY",
    conditions = emptyList(),
    actions = actions,
    variables = emptyList(),
)

/**
 * The assistant proposing a change to an existing flow: the same sections and tiles as the flow
 * editor, with every kind of difference at once — a step changed (with its before → after line),
 * one removed (struck through), one added, and one left as it was (dimmed). The change words
 * and the section titles are the translated text here.
 */
@PreviewTest
@LocaleThemePreviews
@Composable
fun AiChatFlowProposal() {
    val volume = { percent: String -> config("stream" to "MEDIA", "volume_percent" to percent) }
    val current = flowJson(
        listOf(
            // The delay comes after the app, not between the two volume steps: the diff pairs a
            // removal and an addition into one edit only when they are adjacent.
            ActionJson("v", "VOLUME_ADJUST", volume("50"), 0, true),
            ActionJson("s", "OPEN_APP", config("package_name" to "com.spotify.music"), 1, true),
            ActionJson("d", "DELAY", config("duration_value" to "3", "duration_unit" to "SEC"), 2, true),
        ),
    )
    val proposed = flowJson(
        listOf(
            ActionJson("v", "VOLUME_ADJUST", volume("100"), 0, true),
            ActionJson("s", "OPEN_APP", config("package_name" to "com.spotify.music"), 1, true),
            ActionJson("n", "NOTIFICATION", config("title" to "Drive safe"), 2, true),
        ),
    )
    PreviewSurface(padding = 16.dp, background = { SettingsDefaults.pageColor }) {
        FlowPreviewCard(
            flow = proposed,
            saved = false,
            isUpdate = true,
            baseline = current,
            onSave = {},
        )
    }
}
