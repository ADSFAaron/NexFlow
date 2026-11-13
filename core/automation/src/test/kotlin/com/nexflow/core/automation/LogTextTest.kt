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
package com.nexflow.core.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Run-log messages are stored as a code and arguments, and turned into words only when shown. */
class LogTextTest {

    @Test
    fun `a message keeps its code and arguments`() {
        val stored = LogText.of("app_launch_failed", "com.example", null, 42)
        assertEquals(LogText.Parsed("app_launch_failed", listOf("com.example", "", "42")), LogText.parse(stored))
    }

    /** A log written before codes existed — or a platform message — is shown as it is. */
    @Test
    fun `plain text is not a coded message`() {
        assertFalse(LogText.isEncoded("Write file failed: disk full"))
        assertNull(LogText.parse("Write file failed: disk full"))
        assertEquals("Write file failed: disk full", LogText.debug("Write file failed: disk full"))
    }

    /**
     * Arguments come from outside (an SMS, an HTTP header line). A separator inside one would shift
     * every argument after it into the wrong place of the sentence.
     */
    @Test
    fun `a separator inside an argument cannot split it`() {
        val sneaky = "a${LogText.SEP}b${LogText.MARK}c"
        val parsed = LogText.parse(LogText.of("http_bad_header", sneaky, "next"))!!
        assertEquals(2, parsed.args.size)
        assertEquals("next", parsed.args[1])
    }

    /** Adding context keeps one message, never a message inside another that can't be split. */
    @Test
    fun `prefixing puts an argument in front and keeps the code`() {
        val inner = LogText.of("json_missing_key", "main", "pressure", "temp, humidity")
        val outer = LogText.prefixed(inner, "https://api.test")
        assertEquals(
            LogText.Parsed("json_missing_key", listOf("https://api.test", "main", "pressure", "temp, humidity")),
            LogText.parse(outer),
        )
        assertEquals("https://api.test: boom", LogText.prefixed("boom", "https://api.test"))
    }

    @Test
    fun `the debug form is readable in logcat`() {
        assertEquals("unknown_global(g:x)", LogText.debug(LogText.of("unknown_global", "g:x")))
        assertTrue(LogText.isEncoded(LogText.of("tts_error")))
        assertEquals("tts_error", LogText.debug(LogText.of("tts_error")))
    }
}
