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
package com.nexflow.service

import android.util.Log
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.trigger.TimeTriggerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow as KFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Edge case: one misbehaving trigger handler must NOT crash the engine.
 *
 * The engine merges every enabled trigger's stream into a single collector. A handler whose
 * `observe()` errors (the real-world case: GeofenceTriggerHandler when Play Services returns
 * GEOFENCE_NOT_AVAILABLE) used to propagate the exception through the merge and tear down the
 * whole engine — killing every other trigger and crashing the app. FlowEngine now isolates each
 * stream with `.catch`, so a failing trigger is logged and dropped while the rest keep firing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowEngineTriggerIsolationTest {

    @BeforeEach
    fun mockLog() {
        // android.util.Log is a stub that throws in JVM unit tests; the engine's catch logs through
        // it, so make it a no-op.
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun unmockLog() = unmockkStatic(Log::class)

    @Test
    fun `a trigger that throws does not crash the engine and other triggers still fire`() = runTest {
        val goodFlow = testFlow(id = "good", triggerType = TriggerType.MANUAL)
        val badFlow = testFlow(id = "bad", triggerType = TriggerType.GEOFENCE)

        val repository = mockk<FlowRepository>(relaxed = true)
        // Stays open like the real Room-backed flow (a completing flowOf would make collectLatest
        // tear down its block before the triggered run finishes).
        every { repository.observeEnabled() } returns flow {
            emit(listOf(goodFlow, badFlow))
            awaitCancellation()
        }
        coEvery { repository.getById("good") } returns goodFlow
        coEvery { repository.getById("bad") } returns badFlow

        val scheduler = mockk<TimeTriggerScheduler>(relaxed = true)

        // GEOFENCE handler explodes the moment it's observed — mimics a failed geofence registration.
        val explodingHandler = handler(TriggerType.GEOFENCE) {
            flow { throw RuntimeException("geofence registration failed") }
        }
        // MANUAL handler fires one event for its flow.
        val firingHandler = handler(TriggerType.MANUAL) {
            flowOf(TriggerEvent(triggerId = "t-good", flowId = "good"))
        }

        val engine = FlowEngine(
            repository = repository,
            triggerHandlerSet = setOf(explodingHandler, firingHandler),
            actionExecutorSet = emptySet(),
            timeTriggerScheduler = scheduler,
        )

        engine.start(this)
        advanceUntilIdle()

        // The healthy trigger fired and the engine dispatched its run (it fetched the flow to
        // execute) — proving the exploding GEOFENCE trigger did not take the engine down with it.
        coVerify(atLeast = 1) { repository.getById("good") }
        // The exploding trigger never produced a run.
        coVerify(exactly = 0) { repository.getById("bad") }

        engine.stop()
    }

    private fun handler(
        type: TriggerType,
        stream: (Trigger) -> KFlow<TriggerEvent>,
    ): TriggerHandler = object : TriggerHandler {
        override val supportedType = type
        override fun observe(trigger: Trigger): KFlow<TriggerEvent> = stream(trigger)
    }

    private fun testFlow(id: String, triggerType: TriggerType) = Flow(
        id = id,
        schemaVersion = 1,
        name = id,
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = listOf(Trigger(id = "$id-trigger", type = triggerType, config = emptyMap())),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = emptyList(),
        variables = emptyList(),
    )
}
