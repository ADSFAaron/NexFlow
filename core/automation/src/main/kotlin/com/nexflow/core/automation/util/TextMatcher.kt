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
            MODE_REGEX -> regexMatches(needle, text)
            else -> text.contains(needle, ignoreCase = true)
        }
    }

    /** Longest pattern accepted; real keyword filters are a few dozen characters. */
    const val MAX_PATTERN_LENGTH = 500

    /**
     * Character reads one match may make before it is abandoned. Ordinary patterns on a long
     * notification need a few thousand; a catastrophically backtracking one like `(a+)+$` blows
     * through a million in milliseconds, which is the point of counting reads instead of time.
     */
    const val MAX_CHAR_READS = 1_000_000

    /**
     * java.util.regex backtracks, so a hostile pattern (shipped inside a shared flow) plus a
     * crafted notification can pin the trigger stream for minutes. The pattern and text both come
     * from outside the app, so the match gets a budget, and running out means "no match".
     */
    private fun regexMatches(pattern: String, text: String): Boolean {
        if (pattern.length > MAX_PATTERN_LENGTH) return false
        val regex = runCatching { Regex(pattern) }.getOrElse { return false }
        return try {
            regex.containsMatchIn(BudgetedCharSequence(text, ReadBudget(MAX_CHAR_READS)))
        } catch (e: ReadBudgetExceeded) {
            false
        } catch (e: StackOverflowError) {
            // Deeply nested groups recurse in the matcher; same verdict as running out of budget.
            false
        }
    }

    private class ReadBudgetExceeded : RuntimeException(null, null, false, false)

    private class ReadBudget(private var remaining: Int) {
        fun spend() {
            if (--remaining < 0) throw ReadBudgetExceeded()
        }
    }

    private class BudgetedCharSequence(
        private val text: CharSequence,
        private val budget: ReadBudget,
    ) : CharSequence {
        override val length: Int get() = text.length
        override fun get(index: Int): Char {
            budget.spend()
            return text[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            BudgetedCharSequence(text.subSequence(startIndex, endIndex), budget)
        override fun toString(): String = text.toString()
    }
}
