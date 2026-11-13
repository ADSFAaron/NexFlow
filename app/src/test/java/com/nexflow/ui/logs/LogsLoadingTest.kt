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
package com.nexflow.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexflow.R
import com.nexflow.ui.theme.NexFlowTheme
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Logs tab used to start from an empty list, so every visit showed "No logs yet" for a beat
 * before the real runs replaced it — read as "my runs are gone" by anyone looking for one.
 * While the log is still being read, the page says nothing; only a read that found nothing
 * says there is nothing.
 */
@RunWith(AndroidJUnit4::class)
class LogsLoadingTest {

    @get:Rule
    val rule = createComposeRule()

    private val empty = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.logs_empty_title)

    private fun show(loading: Boolean) = rule.setContent {
        NexFlowTheme(dynamicColor = false) {
            LogsContent(
                days = emptyList(),
                today = LocalDate.of(2026, 11, 9),
                zone = ZoneOffset.UTC,
                onEntryClick = {},
                onClearAll = {},
                loading = loading,
            )
        }
    }

    @Test
    fun whileTheLogIsBeingRead_itDoesNotClaimThereAreNoLogs() {
        show(loading = true)
        rule.onNodeWithText(empty).assertDoesNotExist()
    }

    @Test
    fun aReadThatFoundNothing_saysSo() {
        show(loading = false)
        rule.onNodeWithText(empty).assertIsDisplayed()
    }
}
