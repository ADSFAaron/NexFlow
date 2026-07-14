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
package com.nexflow.ui.flows.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.ui.flows.detail.config.ConfigField
import com.nexflow.ui.flows.detail.config.info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real ConfigDialog for every trigger and action type selectable in
 * the Flows UI, asserting that each declared field's component actually appears.
 */
@RunWith(AndroidJUnit4::class)
class ConfigDialogRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = androidx.test.platform.app.InstrumentationRegistry
        .getInstrumentation().targetContext

    private data class DialogSpec(val name: String, val title: String, val fields: List<ConfigField>)

    private fun runRenderPass(specs: List<DialogSpec>) {
        var current by mutableStateOf<DialogSpec?>(null)
        rule.setContent {
            current?.let { spec ->
                key(spec.name) {
                    ConfigDialog(
                        title = spec.title,
                        fields = spec.fields,
                        initialValues = emptyMap(),
                        availableVariables = listOf("battery"),
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }

        specs.forEach { spec ->
            rule.runOnIdle { current = spec }
            rule.waitForIdle()

            assertDisplayed(spec.name, context.getString(com.nexflow.R.string.action_save))
            assertDisplayed(spec.name, context.getString(com.nexflow.R.string.action_cancel))
            spec.fields.forEach { field -> assertFieldRendered(spec.name, field) }
        }
    }

    private fun assertFieldRendered(owner: String, field: ConfigField) {
        when (field) {
            // Hidden until its showWhen condition is met — not expected with empty config
            is ConfigField.DayPicker -> if (field.showWhenKey != null) return else assertDisplayed(owner, field.label)
            is ConfigField.AppPicker -> assertDisplayed(owner, context.getString(com.nexflow.R.string.fd_choose_app))
            is ConfigField.ShortcutPicker -> assertDisplayed(owner, context.getString(com.nexflow.R.string.fd_choose_shortcut))
            is ConfigField.InfoText -> assertDisplayed(owner, field.body)
            else -> assertDisplayed(owner, field.label)
        }
    }

    private fun assertDisplayed(owner: String, text: String) {
        val nodes = rule.onAllNodesWithText(text).fetchSemanticsNodes()
        assertTrue("[$owner] expected text \"$text\" to be rendered", nodes.isNotEmpty())
    }

    @Test
    fun everyTriggerTypeConfigDialogRenders() {
        runRenderPass(
            TriggerType.entries.map { val i = it.info(context); DialogSpec(it.name, i.label, i.fields) },
        )
    }

    @Test
    fun everyActionTypeConfigDialogRenders() {
        runRenderPass(
            ActionType.entries.map { val i = it.info(context); DialogSpec(it.name, i.label, i.fields) },
        )
    }

    @Test
    fun savePropagatesTypedValues() {
        var saved: Map<String, String>? = null
        rule.setContent {
            ConfigDialog(
                title = context.getString(com.nexflow.R.string.act_if_label),
                fields = ActionType.IF_BLOCK.info(context).fields,
                initialValues = emptyMap(),
                availableVariables = emptyList(),
                onConfirm = { saved = it },
                onDismiss = {},
            )
        }

        rule.onAllNodesWithText(context.getString(com.nexflow.R.string.cfg_condition))[0]
            .performTextInput("{{battery}} < 20")
        rule.onNodeWithText(context.getString(com.nexflow.R.string.action_save)).performClick()
        rule.waitForIdle()

        assertEquals("{{battery}} < 20", saved?.get("expression"))
    }
}
