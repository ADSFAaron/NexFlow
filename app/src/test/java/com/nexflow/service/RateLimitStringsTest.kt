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
package com.nexflow.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.R
import com.nexflow.core.automation.interpreter.RateLimitCategory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * The notice takes the count first (plural selector) and the flow name second; a translation that
 * swaps the two placeholders would crash at format time, so every locale is formatted here.
 */
@RunWith(AndroidJUnit4::class)
class RateLimitStringsTest {

    @Test
    fun `every locale formats the limit notices with the flow name and the count`() {
        for (locale in listOf("en", "zh-rTW", "zh-rCN", "ja")) {
            RuntimeEnvironment.setQualifiers(locale)
            val res = ApplicationProvider.getApplicationContext<Context>().resources
            for (id in listOf(
                R.plurals.rate_limit_notification_sms,
                R.plurals.rate_limit_notification_call,
                R.plurals.rate_limit_notification_http,
            )) {
                val n = RateLimitCategory.SMS.perMinute
                val text = res.getQuantityString(id, n, n, "Morning flow")
                assertTrue("$locale: $text", "Morning flow" in text && "$n" in text)
            }
            val step = res.getQuantityString(R.plurals.run_step_rate_limited, 60, 60)
            assertTrue("$locale: $step", "60" in step)
        }
    }
}
