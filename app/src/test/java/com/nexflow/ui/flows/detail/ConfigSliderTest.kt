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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ConfigSlider] adapts Material3's state-holding Slider to a value owned by the config map.
 * What could go wrong in that adaptation is exactly what these pin down: the slider ignoring a
 * value written by another control, keeping a stale track range after a unit switch, or not
 * reporting the user's drag back to the map.
 */
@RunWith(AndroidJUnit4::class)
class ConfigSliderTest {
    @get:Rule
    val rule = createComposeRule()

    private val slider get() = rule.onNodeWithTag("slider")

    @Test
    fun followsAValueWrittenElsewhere() {
        var value by mutableFloatStateOf(10f)
        rule.setContent {
            ConfigSlider(value = value, onValueChange = {}, valueRange = 0f..100f, modifier = Modifier.testTag("slider"))
        }
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(10f, 0f..100f))

        // e.g. typed into the number field beside it
        value = 75f
        rule.waitForIdle()
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(75f, 0f..100f))
    }

    @Test
    fun takesTheNewRangeWhenTheUnitChanges() {
        var range by mutableStateOf(0f..3600f)
        var value by mutableFloatStateOf(1800f)
        rule.setContent {
            ConfigSlider(value = value, onValueChange = {}, valueRange = range, modifier = Modifier.testTag("slider"))
        }

        // seconds -> minutes: the unit switch resets the value to the new unit's minimum
        range = 1f..60f
        value = 1f
        rule.waitForIdle()
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(1f, 1f..60f))
    }

    @Test
    fun reportsTheUsersChange() {
        var reported = -1f
        var value by mutableFloatStateOf(0f)
        rule.setContent {
            ConfigSlider(
                value = value,
                onValueChange = { reported = it; value = it },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("slider"),
            )
        }

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        rule.waitForIdle()
        assertEquals(42f, reported, 0.01f)
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(42f, 0f..100f))
    }
}
