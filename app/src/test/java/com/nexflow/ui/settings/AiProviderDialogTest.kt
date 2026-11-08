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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** An AlertDialog can't be screenshot-tested (layoutlib draws it in its own window). */
@RunWith(AndroidJUnit4::class)
class AiProviderDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var saved = false

    private fun text(id: Int) = context.getString(id)

    @Before
    fun openDialog() {
        rule.setContent { AiProviderDialog(onDismiss = {}, onSaved = { saved = true }) }
        rule.onNodeWithText(text(R.string.settings_ai_provider_custom)).performClick()
    }

    private fun fill(url: String, model: String) {
        rule.onNodeWithText(text(R.string.settings_ai_custom_url)).performTextInput(url)
        rule.onNodeWithText(text(R.string.settings_ai_custom_model)).performTextInput(model)
        rule.onNodeWithText(text(R.string.action_save)).performClick()
    }

    @Test
    fun `plain http to another host is refused and nothing is stored`() {
        fill("http://192.168.0.5:11434/v1", "qwen3")

        rule.onNodeWithText(text(R.string.settings_ai_custom_url_error)).assertExists()
        assertFalse(saved)
        assertNull(AiPrefs.getCustomEndpoint(context))
        assertEquals(AiProvider.GEMINI, AiPrefs.getProvider(context))
    }

    @Test
    fun `a model name is required`() {
        fill("https://api.openai.com/v1", "")

        rule.onNodeWithText(text(R.string.settings_ai_custom_model_error)).assertExists()
        assertFalse(saved)
    }

    @Test
    fun `a local server needs no key, and saving selects it`() {
        fill("http://localhost:11434/v1/", "qwen3")

        assertTrue(saved)
        assertEquals(AiProvider.CUSTOM, AiPrefs.getProvider(context))
        assertEquals(CustomEndpoint("http://localhost:11434/v1", null, "qwen3"), AiPrefs.getCustomEndpoint(context))
        assertTrue(AiPrefs.isConfigured(context))
    }
}
