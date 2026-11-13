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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.ui.theme.NexFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The flow editor's four sections each open with a header, and the triggers' header alone can
 * carry the any/all toggle. When that header grew to fit the toggle, its tiles sat lower under
 * their title than every other section's did — a mismatch the user saw at once on a phone and
 * no test had looked at. Every header is the same height, toggle or not.
 */
@RunWith(AndroidJUnit4::class)
class SectionHeaderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun headerCarryingTheTriggerLogicToggle_isAsTallAsAPlainOne() {
        rule.setContent {
            NexFlowTheme(dynamicColor = false) {
                Column {
                    Box(Modifier.testTag("plain")) { SectionHeader("Only if") }
                    Box(Modifier.testTag("toggle")) {
                        SectionHeader("When") { TriggerLogicToggle(logic = TriggerLogic.ANY, onSelect = {}) }
                    }
                }
            }
        }

        val plain = rule.onNodeWithTag("plain").getBoundsInRoot()
        val withToggle = rule.onNodeWithTag("toggle").getBoundsInRoot()
        assertEquals(withToggle.height, plain.height)
    }
}
