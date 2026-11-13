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
package com.nexflow.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.data.local.NexFlowDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writing a run's log, against a real (in-memory) Room database with its foreign keys on.
 *
 * Found by the api36 instrumented run: a test deleted its flow the moment the run's log
 * appeared, the engine then wrote the run's steps for a log the delete had cascaded away, and
 * the foreign-key failure — uncaught in the engine's coroutine — killed the app. On a phone that
 * is the user deleting a flow just as it finishes running.
 */
@RunWith(AndroidJUnit4::class)
class SaveExecutionLogTest {

    private lateinit var db: NexFlowDatabase
    private lateinit var repository: FlowRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FlowRepositoryImpl(db, db.flowDao(), db.executionLogDao(), db.executionStepDao())
    }

    @After
    fun tearDown() = db.close()

    private fun flow(id: String) = Flow(
        id = id, schemaVersion = 1, name = "F", description = "", author = null,
        icon = null, iconColor = null, tags = emptyList(), enabled = true,
        createdAt = 0L, updatedAt = 0L, triggers = emptyList(), triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(), actions = emptyList(), variables = emptyList(),
    )

    private fun log(flowId: String) = ExecutionLog(
        id = "log-1", flowId = flowId, triggeredAt = 1L, status = ExecutionStatus.SUCCESS,
        errorMessage = null, executionDurationMs = 5,
    )

    private val steps = listOf(
        ExecutionStep(
            logId = "log-1", seq = 0, actionId = "a", actionType = ActionType.SET_VARIABLE, depth = 0,
            iteration = 0, status = ExecutionStatus.SUCCESS, errorMessage = null, note = null,
            resolvedConfig = null, durationMs = 1,
        ),
    )

    @Test
    fun aRunsLogAndStepsAreStoredTogether() = runBlocking {
        repository.save(flow("f"))

        repository.saveExecutionLog(log("f"), steps)

        assertEquals(1, repository.observeLogsForFlow("f").first().size)
        assertEquals(1, repository.observeStepsForLog("log-1").first().size)
    }

    @Test
    fun theLogOfARunWhoseFlowWasDeleted_isDroppedInsteadOfCrashing() = runBlocking {
        // No flow "gone": deleted before the run got to write its log.
        repository.saveExecutionLog(log("gone"), steps)

        assertTrue(repository.observeLogsForFlow("gone").first().isEmpty())
        assertTrue(repository.observeStepsForLog("log-1").first().isEmpty())
    }

    /**
     * Saving a flow that already exists is an edit, and an edit must not touch its history. With
     * `@Insert(REPLACE)` SQLite deleted the old row first, the delete cascaded, and every change in
     * the editor — a renamed action, a drag to reorder — emptied the flow's run log.
     */
    @Test
    fun savingAnExistingFlowAgain_keepsItsLogsAndSteps() = runBlocking {
        repository.save(flow("f"))
        repository.saveExecutionLog(log("f"), steps)

        repository.save(flow("f").copy(name = "Edited"))

        assertEquals("Edited", repository.getById("f")?.name)
        assertEquals(1, repository.observeLogsForFlow("f").first().size)
        assertEquals(1, repository.observeStepsForLog("log-1").first().size)
    }

    @Test
    fun deletingAFlow_takesItsLogsAndStepsWithIt() = runBlocking {
        repository.save(flow("f"))
        repository.saveExecutionLog(log("f"), steps)

        repository.delete("f")

        assertTrue(repository.observeLogsForFlow("f").first().isEmpty())
        assertTrue(repository.observeStepsForLog("log-1").first().isEmpty())
    }
}
