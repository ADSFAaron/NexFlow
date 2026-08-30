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
package com.nexflow.ui.flows.detail.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.ActionType
import com.nexflow.executor.VolumeActionExecutor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The line under "Volume" on the flow detail screen.
 *
 * When the stored value moved from an absolute step (`level`) to a percentage
 * (`volume_percent`), this summary kept asking for the old key and every volume action —
 * including ones saved by the current editor — rendered as "Media: ?".
 *
 * Robolectric rather than [ActionConfigTest]'s mocked Context: that one answers every
 * getString with a placeholder, so a summary built entirely from the wrong key still looks
 * "not blank" to it. Only real resources can tell the value apart from the fallback.
 */
@RunWith(AndroidJUnit4::class)
class VolumeSummaryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun summaryOf(config: Map<String, String>): String =
        ActionType.VOLUME_ADJUST.configSummary(context, config)

    private val notSet get() = context.getString(com.nexflow.R.string.sum_not_set)

    @Test
    fun `a percentage is shown as a percentage`() {
        val summary = summaryOf(
            mapOf("stream" to "MEDIA", VolumeActionExecutor.PERCENT_KEY to "100"),
        )

        assertTrue("expected the percentage in \"$summary\"", summary.contains("100%"))
        assertTrue("must not fall back to \"not set\": \"$summary\"", !summary.contains(notSet))
    }

    @Test
    fun `a flow saved before the percentage change reads as the volume it will run at`() {
        // 15 was the old slider's maximum, so a stored 15 meant "all the way up" — the same
        // reading the editor uses. Without going through the migration the user would see the
        // fallback on a flow that is perfectly valid and has simply not been re-saved.
        val summary = summaryOf(
            mapOf("stream" to "MEDIA", VolumeActionExecutor.LEGACY_LEVEL_KEY to "15"),
        )

        assertTrue("expected 100% for a legacy full-scale level, got \"$summary\"", summary.contains("100%"))
    }

    @Test
    fun `the stream is named alongside the level`() {
        val ring = summaryOf(mapOf("stream" to "RING", VolumeActionExecutor.PERCENT_KEY to "40"))

        assertTrue(ring.contains(context.getString(com.nexflow.R.string.opt_ringtone)))
        assertTrue(ring.contains("40%"))
    }

    @Test
    fun `a volume action with no level set says so instead of guessing`() {
        assertTrue(summaryOf(mapOf("stream" to "MEDIA")).contains(notSet))
    }
}
