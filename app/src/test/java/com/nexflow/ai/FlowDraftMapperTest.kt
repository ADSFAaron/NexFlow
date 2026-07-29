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
package com.nexflow.ai

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowDraftMapperTest {

    private val context = mockk<Context> {
        every { getString(any()) } returns "x"
        every { getString(any(), *anyVararg()) } returns "x"
    }

    private val uuidRegex =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    private fun validArgs(
        actions: List<Pair<String, JsonObject>> = listOf(
            "TOAST" to buildJsonObject { put("message", "hi") },
        ),
    ): JsonObject = buildJsonObject {
        put("name", "Test flow")
        put("description", "A test")
        put("trigger_logic", "ANY")
        putJsonArray("triggers") {
            add(
                buildJsonObject {
                    put("type", "MANUAL")
                    put("config", buildJsonObject { })
                },
            )
        }
        putJsonArray("actions") {
            actions.forEach { (type, config) ->
                add(
                    buildJsonObject {
                        put("type", type)
                        put("config", config)
                    },
                )
            }
        }
    }

    @Test
    fun `valid args produce a schema-valid disabled flow with injected ids`() {
        val result = FlowDraftMapper.fromArgs(validArgs(), context)

        assertTrue(result.isValid, "expected valid draft, got: ${result.errors}")
        assertNotNull(result.flow)
        val flow = result.flow!!
        assertFalse(flow.enabled, "AI-generated flows must be saved disabled")
        assertEquals(1, flow.schemaVersion)
        assertTrue(uuidRegex.matches(flow.id), "flow id must be a v4 UUID, was ${flow.id}")
        flow.triggers.forEach { assertTrue(uuidRegex.matches(it.id)) }
        flow.actions.forEachIndexed { i, action ->
            assertTrue(uuidRegex.matches(action.id))
            assertEquals(i, action.order, "action order must be its list index")
            assertTrue(action.enabled)
        }
        assertTrue(flow.createdAt.isNotBlank())
        assertEquals(flow.createdAt, flow.updatedAt)
    }

    @Test
    fun `config given as a JSON-encoded string is accepted`() {
        val args = buildJsonObject {
            put("name", "Str config")
            putJsonArray("triggers") {
                add(buildJsonObject { put("type", "MANUAL") })
            }
            putJsonArray("actions") {
                add(
                    buildJsonObject {
                        put("type", "TOAST")
                        put("config", """{"message":"hi"}""")
                    },
                )
            }
        }
        val result = FlowDraftMapper.fromArgs(args, context)
        assertTrue(result.isValid, "expected valid draft, got: ${result.errors}")
        assertEquals("\"hi\"", result.flow!!.actions[0].config["message"].toString())
    }

    @Test
    fun `unknown action type is an error listing valid types`() {
        val result = FlowDraftMapper.fromArgs(
            validArgs(actions = listOf("FROBNICATE" to buildJsonObject { })),
            context,
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "unknown action type \"FROBNICATE\"" in it })
        assertTrue(result.errors.any { "TOAST" in it }, "error must list valid types for repair")
    }

    @Test
    fun `writing an undeclared global is an error, a declared one and a local are fine`() {
        val setGlobal = listOf(
            "SET_VARIABLE" to buildJsonObject { put("variable_name", "g:counter"); put("value", "1") },
        )

        val undeclared = FlowDraftMapper.fromArgs(validArgs(actions = setGlobal), context)
        assertFalse(undeclared.isValid)
        assertTrue(
            undeclared.errors.any { "g:counter" in it && "does not exist" in it },
            "error must name the global so Gemini can repair it, got: ${undeclared.errors}",
        )

        val declared = FlowDraftMapper.fromArgs(
            validArgs(actions = setGlobal),
            context,
            knownGlobals = setOf("counter"),
        )
        assertTrue(declared.isValid, "expected valid draft, got: ${declared.errors}")

        val local = FlowDraftMapper.fromArgs(
            validArgs(
                actions = listOf(
                    "SET_VARIABLE" to buildJsonObject { put("variable_name", "counter"); put("value", "1") },
                ),
            ),
            context,
        )
        assertTrue(local.isValid, "a local variable may be created on the fly, got: ${local.errors}")
    }

    @Test
    fun `unknown config key is an error naming the allowed keys`() {
        val result = FlowDraftMapper.fromArgs(
            validArgs(actions = listOf("TOAST" to buildJsonObject { put("msg", "hi") })),
            context,
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "unknown key \"msg\"" in it && "message" in it })
    }

    @Test
    fun `enum config value outside the allowed set is an error`() {
        val result = FlowDraftMapper.fromArgs(
            validArgs(actions = listOf("WIFI_TOGGLE" to buildJsonObject { put("state", "MAYBE") })),
            context,
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "invalid value \"MAYBE\"" in it && "TOGGLE" in it })
    }

    @Test
    fun `missing name, triggers or actions are errors`() {
        val result = FlowDraftMapper.fromArgs(buildJsonObject { }, context)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.startsWith("name:") })
        assertTrue(result.errors.any { it.startsWith("triggers:") })
        assertTrue(result.errors.any { it.startsWith("actions:") })
    }

    @Test
    fun `invalid trigger_logic is an error`() {
        val args = buildJsonObject {
            validArgs().forEach { (k, v) -> if (k != "trigger_logic") put(k, v) }
            put("trigger_logic", "SOMETIMES")
        }
        val result = FlowDraftMapper.fromArgs(args, context)
        assertTrue(result.errors.any { "trigger_logic" in it })
    }

    @Test
    fun `unbalanced IF block fails schema validation`() {
        val result = FlowDraftMapper.fromArgs(
            validArgs(
                actions = listOf(
                    "IF_BLOCK" to buildJsonObject { },
                    "TOAST" to buildJsonObject { put("message", "hi") },
                ),
            ),
            context,
        )
        assertFalse(result.isValid, "IF_BLOCK without END_IF must not validate")
    }

    @Test
    fun `menu markers must be balanced`() {
        val orphanCase = FlowDraftMapper.fromArgs(
            validArgs(actions = listOf("MENU_CASE" to buildJsonObject { })),
            context,
        )
        assertTrue(orphanCase.errors.any { "MENU_CASE outside" in it })

        val unclosed = FlowDraftMapper.fromArgs(
            validArgs(
                actions = listOf(
                    "SHOW_MENU" to buildJsonObject { },
                    "MENU_CASE" to buildJsonObject { },
                ),
            ),
            context,
        )
        assertTrue(unclosed.errors.any { "unclosed SHOW_MENU" in it })
    }

    @Test
    fun `lowercase type names are normalized`() {
        val args = buildJsonObject {
            put("name", "Case test")
            putJsonArray("triggers") {
                add(buildJsonObject { put("type", "manual") })
            }
            putJsonArray("actions") {
                add(
                    buildJsonObject {
                        put("type", "toast")
                        put("config", buildJsonObject { put("message", "hi") })
                    },
                )
            }
        }
        val result = FlowDraftMapper.fromArgs(args, context)
        assertTrue(result.isValid, "expected valid draft, got: ${result.errors}")
        assertEquals("TOAST", result.flow!!.actions[0].type)
    }
}
