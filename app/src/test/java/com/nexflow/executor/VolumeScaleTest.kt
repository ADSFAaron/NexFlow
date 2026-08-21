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
package com.nexflow.executor

import com.nexflow.core.automation.model.ActionType
import com.nexflow.ui.flows.detail.config.normalizeConfigForEditing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Volume is stored as a percentage because a step number means a different loudness on every
 * phone: the device this was written against has 30 media steps and 7 ringer steps.
 */
class VolumeScaleTest {

    @Test
    fun `full and silent land on the ends of whatever range the device has`() {
        listOf(30, 15, 7).forEach { max ->
            assertEquals(max, VolumeActionExecutor.stepsFor(100, max), "100% must be the top of $max")
            assertEquals(0, VolumeActionExecutor.stepsFor(0, max), "0% must be silent on $max")
        }
    }

    @Test
    fun `the same percentage is the same loudness on different scales`() {
        // Half is half, which is the whole point: the old absolute 15 was full volume on a
        // 15-step phone and half volume on a 30-step one.
        assertEquals(15, VolumeActionExecutor.stepsFor(50, 30))
        assertEquals(8, VolumeActionExecutor.stepsFor(50, 15)) // 7.5 rounds up
    }

    @Test
    fun `rounding does not cap a short scale one notch below the top`() {
        // 7 steps: truncating 90% would give 6, so "nearly all the way up" would never reach it.
        assertEquals(6, VolumeActionExecutor.stepsFor(90, 7))
        assertEquals(4, VolumeActionExecutor.stepsFor(50, 7))
    }

    @Test
    fun `out of range percentages are clamped rather than wrapped`() {
        assertEquals(30, VolumeActionExecutor.stepsFor(500, 30))
        assertEquals(0, VolumeActionExecutor.stepsFor(-20, 30))
    }

    @Test
    fun `editing a pre-percentage action reads its step against the old slider, not the device`() {
        // The old slider stopped at 15, so a stored 15 was the user asking for the maximum.
        val migrated = normalizeConfigForEditing(
            ActionType.VOLUME_ADJUST,
            mapOf("stream" to "MEDIA", "level" to "15"),
        )

        assertEquals("100", migrated[VolumeActionExecutor.PERCENT_KEY])
        assertEquals(null, migrated[VolumeActionExecutor.LEGACY_LEVEL_KEY], "the two must not both survive")
        assertEquals("MEDIA", migrated["stream"], "the rest of the config is untouched")
    }

    @Test
    fun `an action already storing a percentage is left alone`() {
        val config = mapOf("stream" to "RING", VolumeActionExecutor.PERCENT_KEY to "40")

        assertEquals(config, normalizeConfigForEditing(ActionType.VOLUME_ADJUST, config))
    }

    @Test
    fun `other action types are never rewritten`() {
        val config = mapOf("level" to "15")

        assertEquals(config, normalizeConfigForEditing(ActionType.BRIGHTNESS_ADJUST, config))
    }
}
