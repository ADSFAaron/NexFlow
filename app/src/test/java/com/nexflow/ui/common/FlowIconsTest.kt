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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Icon keys are persisted in flows and in exported JSON. A key that stops resolving does not
 * fail loudly — the flow silently falls back to the default bolt — so the keys written by the
 * original 47-icon catalog are pinned here.
 */
class FlowIconsTest {

    /** Every key the pre-catalog picker could write. */
    private val legacyKeys = listOf(
        "bolt", "sparkle", "play", "star", "heart", "rocket", "alarm", "schedule", "timer",
        "bedtime", "sunny", "home", "work", "school", "location", "car", "flight", "wifi",
        "bluetooth", "nfc", "battery", "power", "brightness", "volume", "dnd", "airplane",
        "notification", "sms", "call", "music", "headphones", "camera", "screenshot", "share",
        "link", "cloud", "code", "file", "folder", "lock", "settings", "lightbulb", "fitness",
        "coffee", "pets", "shopping", "game",
    )

    @Test
    fun `every legacy key still resolves to its own icon`() {
        val bolt = Icons.Outlined.Bolt
        legacyKeys.filter { it != "bolt" }.forEach { key ->
            assertNotEquals(bolt.name, FlowIcons.vector(key).name, "legacy key '$key' fell back")
        }
        assertEquals(bolt.name, FlowIcons.vector("bolt").name)
    }

    @Test
    fun `unknown and null keys fall back to the default`() {
        assertEquals(Icons.Outlined.Bolt.name, FlowIcons.vector(null).name)
        assertEquals(Icons.Outlined.Bolt.name, FlowIcons.vector("no_such_icon").name)
        assertEquals(FlowIcons.DEFAULT_KEY, "bolt")
    }

    @Test
    fun `catalog keys are unique and cover the legacy aliases`() {
        assertEquals(FlowIcons.keys.size, FlowIcons.keys.toSet().size)
        assertTrue(FlowIcons.keys.size > 2000, "expected the full Material set, got ${FlowIcons.keys.size}")
        assertTrue("directions_car" in FlowIcons.keys)
        assertTrue("play_arrow" in FlowIcons.keys)
    }

    @Test
    fun `search ignores separators and matches anywhere in the key`() {
        assertTrue("wb_sunny" in FlowIcons.search("wb sunny"))
        assertTrue("wb_sunny" in FlowIcons.search("wbsunny"))
        assertTrue("wb_sunny" in FlowIcons.search("WB_Sunny"))
        assertTrue("directions_car" in FlowIcons.search("car"))
        assertEquals(FlowIcons.keys, FlowIcons.search("  "))
        assertTrue(FlowIcons.search("zzzznope").isEmpty())
    }
}
