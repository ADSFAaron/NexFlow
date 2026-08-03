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

/**
 * Keyword matching shared by the content filters of the notification and SMS triggers.
 *
 * A blank pattern means "no filter" and matches everything — every filter field in the UI is
 * optional, and an empty box must never silence a trigger.
 */
object TextMatcher {

    const val MODE_CONTAINS = "CONTAINS"
    const val MODE_EXACT = "EXACT"
    const val MODE_REGEX = "REGEX"

    /** Config values accepted by [matches], in the order the picker shows them. */
    val MODES: List<String> = listOf(MODE_CONTAINS, MODE_EXACT, MODE_REGEX)

    /**
     * @param mode one of [MODES]; anything else (including null) falls back to [MODE_CONTAINS],
     *   so a hand-edited or imported flow with a bogus mode still filters instead of dropping
     *   every event.
     * @return true when [pattern] is blank, or when [text] matches it under [mode].
     *   CONTAINS/EXACT ignore case. An invalid REGEX never matches — a typo in the pattern must
     *   not throw inside a trigger stream and tear the handler down.
     */
    fun matches(text: String, pattern: String?, mode: String? = MODE_CONTAINS): Boolean {
        val needle = pattern?.trim().orEmpty()
        if (needle.isEmpty()) return true
        return when (mode?.trim()?.uppercase()) {
            MODE_EXACT -> text.trim().equals(needle, ignoreCase = true)
            MODE_REGEX -> runCatching { Regex(needle).containsMatchIn(text) }.getOrDefault(false)
            else -> text.contains(needle, ignoreCase = true)
        }
    }
}
