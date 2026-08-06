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
package com.nexflow.core.macrodroid

import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.macrodroid.model.MdrItem
import com.nexflow.core.macrodroid.model.MdrMacro
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The class types and option keys used here are the ones MacroDroid actually writes — see
 * docs/MACRODROID_IMPORT.md for where each was read from.
 */
class MdrToFlowConverterTest {

    private fun options(json: String) = Json.decodeFromString<JsonObject>(json)

    private fun action(classType: String, options: String = "{}") =
        MdrItem(classType = classType, options = options(options))

    private fun convertAction(classType: String, options: String = "{}") =
        MdrToFlowConverter.convert(MdrMacro(name = "test", actionList = listOf(action(classType, options))))

    private fun convertTrigger(classType: String, options: String = "{}") =
        MdrToFlowConverter.convert(
            MdrMacro(name = "test", triggerList = listOf(MdrItem(classType = classType, options = options(options)))),
        )

    private fun ActionJson.value(key: String) = config[key]?.jsonPrimitive?.content

    // ------------------------------------------------------------------ type mapping

    @Test
    fun `airplane mode, wallpaper, speakerphone and shortcut actions are recognised`() {
        assertEquals("AIRPLANE_TOGGLE", convertAction("SetAirplaneModeAction").flow.actions[0].type)
        assertEquals("SET_WALLPAPER", convertAction("SetWallpaperAction").flow.actions[0].type)
        assertEquals("SPEAKERPHONE", convertAction("SpeakerPhoneAction").flow.actions[0].type)
        assertEquals("LAUNCH_SHORTCUT", convertAction("LaunchShortcutAction").flow.actions[0].type)
    }

    @Test
    fun `shake and ambient light triggers are recognised`() {
        assertEquals("SHAKE", convertTrigger("ShakeDeviceTrigger").flow.triggers[0].type)
        assertEquals("AMBIENT_LIGHT", convertTrigger("LightSensorTrigger").flow.triggers[0].type)
    }

    @Test
    fun `an unknown class is reported instead of arriving as a blank action`() {
        val result = convertAction("SomeFutureAction")

        assertEquals("UNSUPPORTED", result.flow.actions[0].type)
        // Unknown types degrade to a toast on import, so the toast has to say what it replaced.
        assertEquals(
            "Unsupported MacroDroid action: SomeFutureAction",
            result.flow.actions[0].value("message"),
        )
        assertTrue(result.warnings.any { it.contains("SomeFutureAction") }, "got ${result.warnings}")
    }

    // ------------------------------------------------------------------ tap and swipe

    @Test
    fun `the legacy touch action becomes a tap with its coordinates`() {
        val action = convertAction("TouchScreenAction", """{"m_xLocation":540,"m_yLocation":1200}""")
            .flow.actions[0]

        assertEquals("SIMULATE_TAP", action.type)
        assertEquals("540", action.value("x"))
        assertEquals("1200", action.value("y"))
    }

    @Test
    fun `a UI interaction is a tap or a swipe depending on its configuration`() {
        val tap = convertAction(
            "UIInteractionAction",
            """{"uiInteractionConfiguration":{"type":"Click","xyPoint":{"x":100,"y":200}}}""",
        ).flow.actions[0]
        assertEquals("SIMULATE_TAP", tap.type)
        assertEquals("100", tap.value("x"))

        val swipe = convertAction(
            "UIInteractionAction",
            """{"uiInteractionConfiguration":{"type":"Gesture","startX":10,"startY":20,"endX":30,"endY":40,"durationMs":250}}""",
        ).flow.actions[0]
        assertEquals("SIMULATE_SWIPE", swipe.type)
        assertEquals("10", swipe.value("x1"))
        assertEquals("40", swipe.value("y2"))
        assertEquals("250", swipe.value("duration"))
    }

    @Test
    fun `tap and swipe warn that only the GitHub build can run them`() {
        val result = convertAction("TouchScreenAction", """{"m_xLocation":1,"m_yLocation":2}""")

        assertTrue(
            result.warnings.any { it.contains("GitHub build") },
            "expected a flavor warning, got ${result.warnings}",
        )
    }

    // ------------------------------------------------------------------ option mapping

    @Test
    fun `a timer trigger arrives with NexFlow's own time keys, not MacroDroid's`() {
        val trigger = convertTrigger(
            "TimerTrigger",
            """{"m_hour":7,"m_minute":5,"m_daysOfWeek":[true,true,true,true,true,false,false],"m_useAlarm":true}""",
        ).flow.triggers[0]

        assertEquals("07:05", trigger.config["time"]?.jsonPrimitive?.content)
        assertEquals("WEEKDAYS", trigger.config["repeat"]?.jsonPrimitive?.content)
        assertNull(trigger.config["m_hour"], "MacroDroid's own keys must not leak through")
    }

    @Test
    fun `custom days are listed individually`() {
        val trigger = convertTrigger(
            "TimerTrigger",
            """{"m_hour":22,"m_minute":0,"m_daysOfWeek":[true,false,true,false,false,false,true]}""",
        ).flow.triggers[0]

        assertEquals("CUSTOM", trigger.config["repeat"]?.jsonPrimitive?.content)
        assertEquals("MON,WED,SUN", trigger.config["days"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a battery trigger keeps its level and direction`() {
        val trigger = convertTrigger(
            "BatteryLevelTrigger",
            """{"m_batteryLevel":15,"m_decreasesTo":true,"m_option":0}""",
        ).flow.triggers[0]

        assertEquals("15", trigger.config["level"]?.jsonPrimitive?.content)
        assertEquals("BELOW", trigger.config["direction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a pause becomes a delay in the matching unit`() {
        val seconds = convertAction("PauseAction", """{"m_delayInSeconds":7,"m_delayInMilliSeconds":0}""")
            .flow.actions[0]
        assertEquals("7", seconds.value("duration_value"))
        assertEquals("SEC", seconds.value("duration_unit"))

        val millis = convertAction("PauseAction", """{"m_delayInSeconds":0,"m_delayInMilliSeconds":150}""")
            .flow.actions[0]
        assertEquals("150", millis.value("duration_value"))
        assertEquals("MS", millis.value("duration_unit"))
    }

    @Test
    fun `airplane mode carries its on-off state`() {
        assertEquals("ON", convertAction("SetAirplaneModeAction", """{"m_state":0}""").flow.actions[0].value("state"))
        assertEquals("OFF", convertAction("SetAirplaneModeAction", """{"m_state":1}""").flow.actions[0].value("state"))
    }

    @Test
    fun `a dropped MacroDroid setting is named in the warnings`() {
        // m_prePopulate has no NexFlow equivalent; m_number and m_messageContent do.
        val result = convertAction(
            "SendSMSAction",
            """{"m_number":"+886900000000","m_messageContent":"hi","m_prePopulate":true}""",
        )

        assertEquals("+886900000000", result.flow.actions[0].value("number"))
        assertTrue(
            result.warnings.any { it.contains("pre-filled") },
            "expected the pre-populate difference to be reported, got ${result.warnings}",
        )
    }

    @Test
    fun `a recognised type with no mapper says its settings need setting up`() {
        // The volume action's stream/level arrays have no sensible single mapping.
        val result = convertAction("SetVolumeAction", """{"m_volume":7}""")

        assertEquals("VOLUME_ADJUST", result.flow.actions[0].type)
        assertTrue(
            result.warnings.any { it.contains("settings were not") },
            "expected a settings warning, got ${result.warnings}",
        )
    }

    @Test
    fun `a geofence says the coordinates did not come across`() {
        val result = convertTrigger("GeofenceTrigger", """{"m_geofenceId":"abc","m_enterArea":true}""")

        assertEquals("ENTER", result.flow.triggers[0].config["event"]?.jsonPrimitive?.content)
        assertTrue(result.warnings.any { it.contains("coordinates") }, "got ${result.warnings}")
    }

    // ------------------------------------------------------------------ menus

    @Test
    fun `an option dialog expands into a menu block with consecutive order`() {
        val macro = MdrMacro(
            name = "menu",
            actionList = listOf(
                action("ToastAction", """{"m_messageText":"before"}"""),
                action("OptionDialogAction", """{"m_title":"Pick one","m_buttonNames":["A","B","C"]}"""),
                action("ToastAction", """{"m_messageText":"after"}"""),
            ),
        )

        val actions = MdrToFlowConverter.convert(macro).flow.actions

        assertEquals(
            listOf("TOAST", "SHOW_MENU", "MENU_CASE", "MENU_CASE", "MENU_CASE", "END_MENU", "TOAST"),
            actions.map { it.type },
        )
        assertEquals(actions.indices.toList(), actions.map { it.order })
        assertEquals("Pick one", actions[1].value("title"))
        assertEquals("""["A","B","C"]""", actions[1].value("options"))
        assertEquals(listOf("A", "B", "C"), actions.filter { it.type == "MENU_CASE" }.map { it.value("option") })
    }

    @Test
    fun `a menu warns that each option's actions did not come across`() {
        val result = convertAction("OptionDialogAction", """{"m_title":"t","m_buttonNames":["A","B"]}""")

        assertTrue(
            result.warnings.any { it.contains("separate MacroDroid macro") },
            "got ${result.warnings}",
        )
    }

    // ------------------------------------------------------------------ conditions

    @Test
    fun `a battery constraint keeps its level and direction`() {
        val macro = MdrMacro(
            name = "test",
            constraintList = listOf(action("BatteryLevelConstraint", """{"m_batteryLevel":30,"m_greaterThan":true}""")),
        )

        val condition = MdrToFlowConverter.convert(macro).flow.conditions[0]

        assertEquals("BATTERY_LEVEL", condition.type)
        assertEquals("30", condition.config["level"]?.jsonPrimitive?.content)
        assertEquals("ABOVE", condition.config["direction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `an unsupported constraint is reported as blocking the flow`() {
        val macro = MdrMacro(name = "test", constraintList = listOf(action("CellTowerConstraint")))

        val result = MdrToFlowConverter.convert(macro)

        assertEquals("UNKNOWN", result.flow.conditions[0].type)
        assertTrue(result.warnings.any { it.contains("will not run") }, "got ${result.warnings}")
    }

    // ------------------------------------------------------------------ macro level

    @Test
    fun `a disabled action stays disabled`() {
        val macro = MdrMacro(
            name = "test",
            actionList = listOf(MdrItem(classType = "ToastAction", disabled = true)),
        )

        assertEquals(false, MdrToFlowConverter.convert(macro).flow.actions[0].enabled)
    }
}
