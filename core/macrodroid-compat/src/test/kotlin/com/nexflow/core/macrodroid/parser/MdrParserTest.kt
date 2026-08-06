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
package com.nexflow.core.macrodroid.parser

import com.nexflow.core.macrodroid.MdrToFlowConverter
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reads the fixture in src/test/resources, whose shape comes from a real MacroDroid backup. */
class MdrParserTest {

    private val sample: String =
        checkNotNull(javaClass.getResourceAsStream("/sample.mdr")) { "sample.mdr missing" }
            .bufferedReader().readText()

    @Test
    fun `a real backup parses into macros with their triggers and actions`() {
        val root = MdrParser.parse(sample).getOrThrow()

        assertEquals(1, root.macros.size)
        val macro = root.macros[0]
        assertEquals("Morning routine", macro.name)
        assertEquals(listOf("TimerTrigger"), macro.triggerList.map { it.classType })
        assertEquals(
            listOf("SetAirplaneModeAction", "PauseAction", "ControlMediaAction"),
            macro.actionList.map { it.classType },
        )
        // Settings sit flat next to m_classType in the file and must survive the split.
        assertEquals(7, macro.triggerList[0].options["m_hour"]?.jsonPrimitive?.content?.toInt())
        // Bookkeeping keys are not settings and must not reach the mappers.
        assertFalse(macro.actionList[0].options.containsKey("m_SIGUID"))
    }

    @Test
    fun `a single-macro export is read the same way`() {
        val singleMacro = """
            {"macro":{"m_name":"Kiwi launch","m_actionList":[{"m_classType":"KeepAwakeAction"}],
            "m_triggerList":[{"m_packageNameList":["com.kiwibrowser.browser"],"m_launched":true,
            "m_classType":"ApplicationLaunchedTrigger"}]},"macroExportVersion":1}
        """.trimIndent()

        val root = MdrParser.parse(singleMacro).getOrThrow()

        assertEquals(1, root.macros.size)
        assertEquals("Kiwi launch", root.macros[0].name)
    }

    @Test
    fun `a MacroDroid file is told apart from a NexFlow flow`() {
        assertTrue(MdrParser.looksLikeMacroDroid(sample))
        assertFalse(MdrParser.looksLikeMacroDroid("""{"schema_version":1,"id":"x","name":"flow"}"""))
    }

    @Test
    fun `converting the fixture fills in the settings, not just the types`() {
        val macro = MdrParser.parse(sample).getOrThrow().macros[0]

        val result = MdrToFlowConverter.convert(macro)
        val flow = result.flow

        assertEquals(listOf("TIME"), flow.triggers.map { it.type })
        assertEquals("07:30", flow.triggers[0].config["time"]?.jsonPrimitive?.content)
        assertEquals("WEEKDAYS", flow.triggers[0].config["repeat"]?.jsonPrimitive?.content)

        assertEquals(listOf("AIRPLANE_TOGGLE", "DELAY", "MEDIA_PLAY_PAUSE"), flow.actions.map { it.type })
        assertEquals("OFF", flow.actions[0].config["state"]?.jsonPrimitive?.content)
        assertEquals("7", flow.actions[1].config["duration_value"]?.jsonPrimitive?.content)
        assertEquals("PLAY", flow.actions[2].config["action"]?.jsonPrimitive?.content)

        assertEquals(listOf("BATTERY_LEVEL"), flow.conditions.map { it.type })
        assertEquals("ABOVE", flow.conditions[0].config["direction"]?.jsonPrimitive?.content)
    }
}
