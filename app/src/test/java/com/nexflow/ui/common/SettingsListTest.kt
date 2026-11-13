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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings tiles' behaviour for TalkBack and touch. SettingsSwitchItem builds a switch out
 * of SegmentedListItem's clickable overload because the checked overload says "checkbox"; these
 * are what that choice has to deliver.
 */
@RunWith(AndroidJUnit4::class)
class SettingsListTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aSwitchTileIsOneSwitchThatTogglesAsAWhole() {
        var checked by mutableStateOf(false)
        rule.setContent {
            SettingsGroup(title = null) {
                item { shapes ->
                    SettingsSwitchItem(
                        shapes = shapes,
                        title = "Auto-start",
                        summary = "Resume after a restart",
                        checked = checked,
                        onCheckedChange = { checked = it },
                    )
                }
            }
        }
        // The title is merged into the tile, so this is the tile itself.
        val tile = rule.onNodeWithText("Auto-start")
        tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        tile.assertIsOff()

        // Tapping anywhere on it (here: its title) toggles; nothing needs to aim at the switch.
        tile.performClick()
        rule.waitForIdle()
        tile.assertIsOn()
        assert(checked)
    }

    @Test
    fun aSwitchTileIsNotACheckbox() {
        rule.setContent {
            SettingsGroup(title = null) {
                item { shapes ->
                    SettingsSwitchItem(shapes = shapes, title = "Toast", checked = true, onCheckedChange = {})
                }
            }
        }
        rule.onNodeWithText("Toast")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test
    fun aTileWithoutAnActionIsNotClickable() {
        // e.g. the ADB row once WRITE_SECURE_SETTINGS is granted: nothing left to do there.
        rule.setContent {
            SettingsGroup(title = null) {
                item { shapes -> SettingsItem(shapes = shapes, title = "Granted", onClick = null) }
            }
        }
        rule.onNodeWithText("Granted").assertHasNoClickAction()
    }
}
