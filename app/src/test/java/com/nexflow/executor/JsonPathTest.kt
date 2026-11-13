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
package com.nexflow.executor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.nexflow.core.automation.LogText

class JsonPathTest {

    private val weather = """
        {
          "name": "Taipei",
          "main": { "temp": 31.2, "humidity": 78 },
          "weather": [ { "id": 500, "main": "Rain" }, { "id": 501, "main": "Drizzle" } ],
          "rain": null,
          "ok": true
        }
    """.trimIndent()

    private fun extract(path: String, json: String = weather) = JsonPath.extract(json, path)

    private fun value(path: String, json: String = weather) = extract(path, json).getOrThrow()

    /** The failure as code + arguments; the words are LogMessages' business. */
    private fun error(path: String, json: String = weather): LogText.Parsed =
        LogText.parse(extract(path, json).exceptionOrNull()!!.message)!!

    @Test
    fun `reads a nested field`() {
        assertEquals("31.2", value("main.temp"))
        assertEquals("78", value("main.humidity"))
    }

    @Test
    fun `indexes into an array`() {
        assertEquals("Rain", value("weather.0.main"))
        assertEquals("501", value("weather.1.id"))
    }

    /** A string must lose its quotes, or every comparison against it would have to include them. */
    @Test
    fun `strings come back unquoted`() {
        assertEquals("Taipei", value("name"))
    }

    @Test
    fun `booleans and nulls come back as text`() {
        assertEquals("true", value("ok"))
        assertEquals("null", value("rain"))
    }

    /** So a second action can narrow further, and so the value is at least inspectable in the log. */
    @Test
    fun `an object or array leaf comes back as compact json`() {
        assertEquals("""{"temp":31.2,"humidity":78}""", value("main"))
        assertTrue(value("weather").startsWith("["))
    }

    @Test
    fun `a blank path returns the whole document`() {
        assertEquals(weather, value(""))
        assertEquals(weather, value("   "))
    }

    /** JSONPath habit: the leading root marker is tolerated rather than treated as a key. */
    @Test
    fun `a leading dollar sign is ignored`() {
        assertEquals("31.2", value("$.main.temp"))
        assertEquals("Taipei", value("\$name"))
    }

    @Test
    fun `a missing key names the keys that are there`() {
        val failure = error("main.pressure")
        assertEquals("json_missing_key", failure.code)
        assertEquals(listOf("main", "pressure"), failure.args.take(2))
        assertTrue("temp" in failure.args[2] && "humidity" in failure.args[2], failure.toString())
    }

    @Test
    fun `an out-of-range index says how many items there were`() {
        val failure = error("weather.5.main")
        assertEquals("json_out_of_range", failure.code)
        assertEquals(listOf("weather", "2", "5"), failure.args)
    }

    @Test
    fun `a non-numeric segment on an array is rejected`() {
        val failure = error("weather.first")
        assertEquals("json_not_number", failure.code)
        assertEquals(listOf("weather", "first"), failure.args)
    }

    @Test
    fun `reading through a primitive is rejected`() {
        val failure = error("name.length")
        assertEquals("json_scalar", failure.code)
        assertEquals(listOf("name", "length"), failure.args)
    }

    @Test
    fun `a non-json response is reported as such`() {
        assertEquals("json_invalid", error("main.temp", json = "<html>Gateway Timeout</html>").code)
    }
}
