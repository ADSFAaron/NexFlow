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
package com.nexflow.permissions

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.trigger.TimeTriggerScheduler
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A TIME flow that cannot schedule exact alarms still runs — just late, because the scheduler
 * falls back to an inexact alarm the system batches into a Doze maintenance window. That made it
 * the one failure the user could not see: enabled flow, successful log, wrong time. These tests
 * pin the reporting path that surfaces it, from the checker through to the Settings intent the
 * wizard sends the user to.
 *
 * Robolectric because the permission label comes from resources.
 */
@RunWith(AndroidJUnit4::class)
class ExactAlarmPermissionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun checker(canScheduleExact: Boolean) = FlowPermissionChecker(
        context = context,
        timeTriggerScheduler = mockk<TimeTriggerScheduler> {
            every { canScheduleExact() } returns canScheduleExact
        },
    )

    private fun timeFlow() = flowWith(
        Trigger(id = "t1", type = TriggerType.TIME, config = mapOf("time" to "09:00")),
    )

    private fun flowWith(vararg triggers: Trigger) = Flow(
        id = "f1",
        schemaVersion = 1,
        name = "morning",
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = triggers.toList(),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = emptyList(),
        variables = emptyList(),
    )

    @Test
    fun `a TIME flow without exact-alarm permission is reported as missing it`() {
        val missing = checker(canScheduleExact = false).missingPermissions(timeFlow())

        val entry = missing.singleOrNull()
        assertNotNull("a TIME trigger with no exact-alarm right must be reported", entry)
        // Special access, not a runtime permission: there is no dialog for this one, the user
        // has to be walked to the "Alarms & reminders" Settings page.
        assertEquals(SpecialAccess.EXACT_ALARM, entry!!.special)
        assertTrue(entry.runtimePermissions.isEmpty())
    }

    @Test
    fun `a TIME flow with exact-alarm permission reports nothing`() {
        assertTrue(checker(canScheduleExact = true).missingPermissions(timeFlow()).isEmpty())
    }

    @Test
    fun `only TIME triggers ask for exact alarms`() {
        // MANUAL is the default trigger on every newly created flow — if it picked this up,
        // every flow in the list would show the warning badge.
        val manual = flowWith(Trigger(id = "t1", type = TriggerType.MANUAL, config = emptyMap()))
        assertTrue(checker(canScheduleExact = false).missingPermissions(manual).isEmpty())
    }

    @Test
    fun `the wizard sends the user to the Alarms and reminders page for this app`() {
        val intent = PermissionIntents.forSpecial(context, SpecialAccess.EXACT_ALARM)

        assertNotNull("EXACT_ALARM must have somewhere to send the user", intent)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent!!.action)
        // Without the package uri the intent opens the all-apps list rather than this app's toggle.
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
    }
}
