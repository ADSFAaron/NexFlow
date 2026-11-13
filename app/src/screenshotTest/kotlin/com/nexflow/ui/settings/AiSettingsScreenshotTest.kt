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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.nexflow.R
import com.nexflow.prefs.AiProvider
import com.nexflow.ui.LocaleThemePreviews
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsTopAppBar
import com.nexflow.ui.theme.NexFlowTheme

/**
 * The AI settings page in its two shapes. The service choice is shown in both (as in debug
 * builds): the chosen tile's selected colour is the thing to judge in light and dark. Gemini is
 * set up; the custom service is half filled in, so its missing model carries the warning mark
 * and its footer — the longest text here — is in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsPreview(state: AiSettingsState) {
    NexFlowTheme(dynamicColor = false) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            containerColor = SettingsDefaults.pageColor,
            topBar = { SettingsTopAppBar(stringResource(R.string.settings_section_ai), scrollBehavior, onBack = {}) },
        ) { innerPadding ->
            AiSettingsContent(
                state = state,
                showProviderChoice = true,
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = 24.dp),
                onProviderSelected = {},
                onEdit = {},
                onPickGeminiModel = {},
                onGetGeminiKey = {},
            )
        }
    }
}

@PreviewTest
@LocaleThemePreviews
@Composable
fun AiSettingsGemini() {
    AiSettingsPreview(
        AiSettingsState(
            provider = AiProvider.GEMINI,
            geminiKeySet = true,
            geminiModel = "gemini-3.5-flash",
            customUrl = null,
            customKeySet = false,
            customModel = null,
        ),
    )
}

@PreviewTest
@LocaleThemePreviews
@Composable
fun AiSettingsCustomHalfSetUp() {
    AiSettingsPreview(
        AiSettingsState(
            provider = AiProvider.CUSTOM,
            geminiKeySet = true,
            geminiModel = "gemini-3.5-flash",
            customUrl = "http://localhost:11434/v1",
            customKeySet = false,
            customModel = null,
        ),
    )
}
