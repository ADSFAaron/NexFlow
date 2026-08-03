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
package com.nexflow.core.automation.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextMatcherTest {

    @Test
    fun `a blank pattern matches everything`() {
        // Every keyword field in the UI is optional — an empty one must not silence the trigger.
        assertTrue(TextMatcher.matches("anything", null))
        assertTrue(TextMatcher.matches("anything", ""))
        assertTrue(TextMatcher.matches("anything", "   ", TextMatcher.MODE_REGEX))
    }

    @Test
    fun `contains ignores case and is the fallback for an unknown mode`() {
        assertTrue(TextMatcher.matches("Your code is 1234", "CODE"))
        assertFalse(TextMatcher.matches("Your code is 1234", "otp"))
        assertTrue(TextMatcher.matches("Your code is 1234", "code", "nonsense"))
        assertTrue(TextMatcher.matches("Your code is 1234", "code", null))
    }

    @Test
    fun `exact compares the whole trimmed text`() {
        assertTrue(TextMatcher.matches("  Alert ", "alert", TextMatcher.MODE_EXACT))
        assertFalse(TextMatcher.matches("Alert now", "alert", TextMatcher.MODE_EXACT))
    }

    @Test
    fun `regex matches anywhere and never throws on a bad pattern`() {
        assertTrue(TextMatcher.matches("code 1234", """\d{4}""", TextMatcher.MODE_REGEX))
        assertFalse(TextMatcher.matches("code abcd", """\d{4}""", TextMatcher.MODE_REGEX))
        // A typo in the pattern must fail the match, not tear down the trigger's flow collector.
        assertFalse(TextMatcher.matches("code 1234", "[unclosed", TextMatcher.MODE_REGEX))
    }
}
