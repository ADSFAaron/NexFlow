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
package com.nexflow.trigger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nexflow.NotificationChannels
import com.nexflow.R
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.service.FlowExecutionService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A TIME trigger end to end on a real system: AlarmManager delivers the alarm, the receiver
 * starts the execution service, the engine runs the flow and the run lands in the log.
 *
 * Meant for the api36 managed device. Everything Android has added around this path since 12 —
 * exact alarms denied by default, foreground services needing a declared type, background
 * service starts refused — exists only there; on api30 (the minSdk floor, and once the only
 * emulator CI ran) none of it applies, so a break in it would pass every test.
 *
 * What this cannot show: under instrumentation the app counts as in the foreground, so the
 * system never refuses the service start here. That refusal, and what the app does about it, is
 * covered on the JVM by TimeAlarmHandlerTest.
 */
@HiltAndroidTest
class TimeTriggerEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: FlowRepository

    @Inject
    lateinit var scheduler: TimeTriggerScheduler

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val flowId = "e2e-time-flow"
    private val triggerId = "e2e-time-trigger"
    private var serviceWasEnabled = false

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        // HiltTestApplication skips NexFlowApplication.onCreate, where the app makes these;
        // without them the execution service's foreground notification is refused.
        NotificationChannels.create(context)
        serviceWasEnabled = ServiceEnabledPrefs.get(context)
        // A fresh install on API 33+ is denied exact alarms; the flow editor would walk the user
        // to the grant screen before enabling a TIME flow, so the realistic state has it.
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
    }

    @After
    fun tearDown() = runBlocking {
        scheduler.cancel(triggerId)
        repository.delete(flowId)
        ServiceEnabledPrefs.set(context, serviceWasEnabled)
        FlowExecutionService.stop(context)
    }

    private suspend fun saveFlowAndAlarmIn(millis: Long) {
        repository.save(
            Flow(
                id = flowId, schemaVersion = 1, name = "E2E time", description = "", author = null,
                icon = null, iconColor = null, tags = emptyList(), enabled = true,
                createdAt = 0L, updatedAt = 0L,
                triggers = listOf(Trigger(triggerId, TriggerType.TIME, mapOf("time" to "00:00", "repeat" to "ONCE"))),
                triggerLogic = TriggerLogic.ANY,
                conditions = emptyList(),
                actions = listOf(
                    Action("e2e-action", ActionType.SET_VARIABLE, mapOf("variable_name" to "x", "value" to "1"), 0, true),
                ),
                variables = emptyList(),
            ),
        )
        // A running engine reschedules every enabled TIME trigger when the flow set changes;
        // let that settle first, or it would replace the near alarm with one for midnight.
        delay(1_500)
        scheduler.scheduleAt(flowId, triggerId, System.currentTimeMillis() + millis)
    }

    private suspend fun awaitLog() = withTimeout(45_000) {
        repository.observeLogsForFlow(flowId).first { it.isNotEmpty() }.single()
    }

    @Test
    fun exactAlarm_runsTheFlowFromTheAlarmAndLogsTheRun() = runBlocking {
        assertTrue("exact alarms should be granted for this test", scheduler.canScheduleExact())
        ServiceEnabledPrefs.set(context, true)

        saveFlowAndAlarmIn(3_000)

        val log = awaitLog()
        assertEquals(ExecutionStatus.SUCCESS, log.status)
    }

    @Test
    fun withAutomationOff_theAlarmLeavesASkipInTheLog() = runBlocking {
        ServiceEnabledPrefs.set(context, false)

        saveFlowAndAlarmIn(3_000)

        val log = awaitLog()
        assertEquals(ExecutionStatus.SKIPPED, log.status)
        assertEquals(context.getString(R.string.time_skipped_service_off), log.errorMessage)
    }
}
