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

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.LogText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The run log is shown in the reader's language. Failure reasons used to be English sentences
 * fixed at the moment of failure, so a Chinese UI read "Unknown global variable 'g:x' — …".
 */
@RunWith(AndroidJUnit4::class)
class LogMessagesTest {

    private val base = ApplicationProvider.getApplicationContext<Context>()

    private fun localized(tag: String): Context =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })

    private val locales = listOf("en", "zh-TW", "zh-CN", "ja")

    /**
     * Every code the app writes must have words, or the log falls back to the raw code. Read off the
     * source, so a new message without a translation fails here rather than in a user's log.
     */
    @Test
    fun everyCodeTheAppWritesHasWords() {
        val roots = listOf("src/main/java", "src/github/java", "src/play/java", "../core/automation/src/main/kotlin")
            .map(::File).filter(File::isDirectory)
        assertTrue("source roots not found from ${File(".").absolutePath}", roots.isNotEmpty())
        val pattern = Regex("""(?:LogText\.of|failureFrom|failure)\("([a-z_]+)"""")
        val written = roots.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                pattern.findAll(file.readText()).map { it.groupValues[1] }.toList()
            }.toList()
        }.toSet()
        assertTrue("found no codes at all — has the pattern drifted?", written.size > 50)
        assertEquals(emptySet<String>(), written - LogMessages.SPECS.keys)
        assertEquals("specs no code writes any more", emptySet<String>(), LogMessages.SPECS.keys - written)
    }

    /** Each message, in each language, with as many arguments as any of them takes. */
    @Test
    fun everyMessageRendersInEveryLanguage() {
        val args = arrayOf("SIMULATE_TAP", "x", "y", "z")
        for (tag in locales) {
            val context = localized(tag)
            for (code in LogMessages.SPECS.keys) {
                val stored = LogText.of(code, *args)
                val text = LogMessages.render(context, stored)!!
                assertFalse("$tag/$code fell back to the raw code: $text", text == LogText.debug(stored))
                assertFalse("$tag/$code left a placeholder: $text", Regex("%\\d\\\$s").containsMatchIn(text))
            }
        }
    }

    @Test
    fun aLogFromBeforeCodesIsShownAsItIs() {
        assertEquals("SMS failed: radio off", LogMessages.render(localized("zh-TW"), "SMS failed: radio off"))
    }

    @Test
    fun anUnknownCodeStillShowsWhatHappened() {
        assertEquals("from_the_future(a)", LogMessages.render(base, LogText.of("from_the_future", "a")))
    }

    @Test
    fun theFailureThatPromptedThisReadsInChinese() {
        val text = LogMessages.render(localized("zh-TW"), LogText.of("unknown_global", "g:lowcount"))!!
        assertEquals("找不到全域變數「g:lowcount」，請先到「設定 → 全域變數」建立", text)
    }

    /** Type names in arguments become the labels the user sees in the editor. */
    @Test
    fun actionAndConditionNamesAreShownAsTheirLabels() {
        val zh = localized("zh-TW")
        val tap = LogMessages.render(zh, LogText.of("needs_accessibility", "SIMULATE_TAP"))!!
        assertFalse(tap, "SIMULATE_TAP" in tap)
        val charging = LogMessages.render(zh, LogText.of("cond_not_met", "CHARGING"))!!
        assertFalse(charging, "CHARGING" in charging)
    }

    /** The top of a JSON document has no path; it is called "the response" in each language. */
    @Test
    fun aJsonPathAtTheTopIsTheResponse() {
        val stored = LogText.of("json_missing_key", "https://api.test", "", "pressure", "temp")
        assertTrue(LogMessages.render(base, stored)!!.contains("in the response"))
        assertTrue(LogMessages.render(localized("zh-TW"), stored)!!.contains("回應"))
        val nested = LogText.of("json_missing_key", "https://api.test", "main", "pressure", "temp")
        assertTrue(LogMessages.render(localized("zh-TW"), nested)!!.contains("「main」"))
    }
}
