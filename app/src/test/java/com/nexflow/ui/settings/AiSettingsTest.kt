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
package com.nexflow.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.R
import com.nexflow.ai.CustomEndpoint
import com.nexflow.prefs.AiPrefs
import com.nexflow.prefs.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The AI settings page edits one field per dialog and stores it straight away, so what these
 * guard is what reaches AiPrefs: an address that would send a key and every flow's settings in
 * clear text never does, a service missing its model stays "not set up", and the optional key
 * can be taken away again.
 */
@RunWith(AndroidJUnit4::class)
class AiSettingsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var saved = false

    private fun text(id: Int) = context.getString(id)

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("nexflow_ai", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun edit(field: AiField, input: String) {
        rule.setContent { AiFieldDialog(field = field, onDismiss = {}, onSaved = { saved = true }) }
        rule.onNode(hasSetTextAction()).performTextReplacement(input)
        rule.onNodeWithText(text(R.string.action_save)).performClick()
        rule.waitForIdle()
    }

    private val fieldShowsError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    @Test
    fun `plain http to another host is refused and nothing is stored`() {
        edit(AiField.CUSTOM_URL, "http://192.168.0.5:11434/v1")

        rule.onNode(hasSetTextAction()).assert(fieldShowsError)
        assertFalse(saved)
        assertNull(AiPrefs.getCustomBaseUrl(context))
    }

    @Test
    fun `a model name is required`() {
        edit(AiField.CUSTOM_MODEL, "   ")

        rule.onNodeWithText(text(R.string.settings_ai_custom_model_error)).assertExists()
        assertFalse(saved)
        assertNull(AiPrefs.getCustomModel(context))
    }

    @Test
    fun `a local server needs no key, and is set up once address and model are there`() {
        AiPrefs.setProvider(context, AiProvider.CUSTOM)
        edit(AiField.CUSTOM_URL, "http://localhost:11434/v1/")
        assertTrue(saved)
        // Half-filled is stored but not usable yet.
        assertFalse(AiPrefs.isConfigured(context))

        AiPrefs.setCustomModel(context, "qwen3")
        assertEquals(CustomEndpoint("http://localhost:11434/v1", null, "qwen3"), AiPrefs.getCustomEndpoint(context))
        assertTrue(AiPrefs.isConfigured(context))
    }

    @Test
    fun `an existing value opens selected, so one paste replaces it`() {
        AiPrefs.setApiKey(context, "AIza-old-key")
        rule.setContent { AiFieldDialog(field = AiField.GEMINI_KEY, onDismiss = {}, onSaved = {}) }

        rule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, "AIza-old-key".length)),
        )
    }

    @Test
    fun `clearing the optional key removes it`() {
        AiPrefs.setCustomApiKey(context, "sk-old")
        edit(AiField.CUSTOM_KEY, "")

        assertTrue(saved)
        assertNull(AiPrefs.getCustomApiKey(context))
    }

    @Test
    fun `the services are radio choices and picking one reports it`() {
        var picked: AiProvider? = null
        rule.setContent {
            AiSettingsContent(
                state = AiSettingsState.read(context),
                showProviderChoice = true,
                contentPadding = PaddingValues(),
                onProviderSelected = { picked = it },
                onEdit = {},
                onPickGeminiModel = {},
                onGetGeminiKey = {},
            )
        }
        rule.onNodeWithText(text(R.string.settings_ai_provider_custom))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        assertEquals(AiProvider.CUSTOM, picked)
    }

    @Test
    fun `without the choice (release builds) there is no service to pick`() {
        rule.setContent {
            AiSettingsContent(
                state = AiSettingsState.read(context),
                showProviderChoice = false,
                contentPadding = PaddingValues(),
                onProviderSelected = {},
                onEdit = {},
                onPickGeminiModel = {},
                onGetGeminiKey = {},
            )
        }
        rule.onNodeWithText(text(R.string.settings_ai_provider_custom)).assertDoesNotExist()
        // ...and Gemini's own settings are what the page shows.
        rule.onNodeWithText(text(R.string.settings_ai_api_key)).assertExists()
    }
}
