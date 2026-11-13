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

/**
 * A message for the run log — why an action failed, why a run was skipped — stored as a code
 * plus its arguments and put into words only when it is shown.
 *
 * Messages used to be English sentences written at the moment of failure, so a Traditional
 * Chinese user read "Unknown global variable 'g:x' — create it in Settings → Global Variables
 * first" in an otherwise Chinese app. Storing the code instead means the log is shown in the
 * reader's language, including after they switch languages: the same reason step notes are
 * tokens rather than text.
 *
 * The stored form is [MARK], the code, then each argument after [SEP]. Both are control
 * characters nobody types, so text that does not start with [MARK] — a log written before this
 * existed, or a platform exception's own message — is shown as it is.
 *
 * Arguments are plain values (a variable name, an app package, the system's exception message).
 * A message is never an argument of another: [isEncoded] lets a caller pass an already-coded
 * message through instead of wrapping it, which could not be split apart again.
 */
object LogText {

    const val MARK = '\u0001'
    const val SEP = '\u001F'

    data class Parsed(val code: String, val args: List<String>)

    fun of(code: String, vararg args: Any?): String = buildString {
        append(MARK)
        append(code)
        args.forEach { arg ->
            append(SEP)
            // A separator inside an argument would shift every argument after it.
            append(arg?.toString().orEmpty().replace(SEP, ' ').replace(MARK, ' '))
        }
    }

    fun isEncoded(text: String?): Boolean = text != null && text.startsWith(MARK)

    /** The code and arguments of an encoded message; null for plain text. */
    fun parse(text: String?): Parsed? {
        if (!isEncoded(text)) return null
        val parts = text!!.substring(1).split(SEP)
        return Parsed(parts.first(), parts.drop(1))
    }

    /**
     * [text] with [first] put in front of its arguments, keeping its code — how a caller adds
     * context (the URL a JSON path was read from) without nesting one message inside another.
     * Plain text gets the same treatment the old way, as "first: text".
     */
    fun prefixed(text: String?, first: Any?): String {
        val parsed = parse(text) ?: return "$first: ${text.orEmpty()}"
        return of(parsed.code, first, *parsed.args.toTypedArray())
    }

    /**
     * A readable, untranslated form for logcat and test failures: `code(arg1, arg2)`. Plain text
     * passes through unchanged.
     */
    fun debug(text: String?): String {
        val parsed = parse(text) ?: return text.orEmpty()
        return if (parsed.args.isEmpty()) parsed.code else "${parsed.code}(${parsed.args.joinToString(", ")})"
    }
}
