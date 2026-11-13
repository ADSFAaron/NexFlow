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
package com.nexflow.ui.flowimport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.data.local.NexFlowDatabase
import com.nexflow.data.toFlowJson
import com.nexflow.data.repository.FlowRepositoryImpl
import com.nexflow.data.repository.GlobalVariableRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Importing the same file twice must add a second flow, not overwrite the first one. Saving is an
 * upsert, so a reused id would replace the user's flow with the file's copy; before 1.7.2 it was a
 * REPLACE and took the flow's run history with it (ON DELETE CASCADE). Runs against a real
 * (in-memory) Room database, so both the row and its logs are checked for real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ImportReimportTest {

    private lateinit var db: NexFlowDatabase
    private lateinit var repository: FlowRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NexFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FlowRepositoryImpl(db, db.flowDao(), db.executionLogDao(), db.executionStepDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** A new view model per import, as the screen gets one per visit. */
    private fun import(content: String): ImportResult = runBlocking {
        val vm = ImportViewModel(repository, GlobalVariableRepositoryImpl(db.globalVariableDao()))
        vm.importAuto(content)
        withTimeout(5_000) { vm.result.first { it != null } }!!
    }

    private fun allFlows() = runBlocking { repository.observeAll().first() }

    private fun logsOf(flowId: String) = runBlocking { repository.observeLogsForFlow(flowId).first() }

    @Test
    fun `importing a flow file twice keeps the first copy and its run history`() {
        val id = "11111111-1111-4111-8111-111111111111"
        val file = FlowSerializer.encode(
            FlowJson(
                schemaVersion = 1, id = id, name = "Morning", description = "", tags = emptyList(),
                enabled = false, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
                triggers = listOf(TriggerJson("22222222-2222-4222-8222-222222222222", "TIME", JsonObject(emptyMap()))),
                triggerLogic = "ANY", conditions = emptyList(),
                actions = listOf(
                    ActionJson("33333333-3333-4333-8333-333333333333", "TOAST", JsonObject(emptyMap()), order = 0, enabled = true),
                ),
                variables = emptyList(),
            ),
        )

        assertEquals(1, import(file).imported)
        runBlocking {
            repository.saveExecutionLog(ExecutionLog("log1", id, 0L, ExecutionStatus.SUCCESS, null, 5L))
        }

        val second = import(file)

        assertEquals(1, second.imported)
        val flows = allFlows()
        assertEquals(2, flows.size)
        assertEquals("the first copy keeps its id", 1, flows.count { it.id == id })
        assertEquals("and its run history", listOf("log1"), logsOf(id).map { it.id })
        val copy = flows.single { it.id != id }
        assertEquals(
            "item ids are scoped to their flow and stay as they were",
            listOf("33333333-3333-4333-8333-333333333333"),
            copy.actions.map { it.id },
        )
    }

    @Test
    fun `importing the same MacroDroid file twice adds a second flow`() {
        // The flow id comes from m_GUID, so both imports ask for "4242".
        val mdr = """
            {"macro":{"m_name":"Kiwi launch","m_GUID":4242,"m_actionList":[{"m_classType":"KeepAwakeAction"}],
            "m_triggerList":[{"m_packageNameList":["com.kiwibrowser.browser"],"m_launched":true,
            "m_classType":"ApplicationLaunchedTrigger"}]},"macroExportVersion":1}
        """.trimIndent()

        import(mdr)
        runBlocking {
            repository.saveExecutionLog(ExecutionLog("log1", "4242", 0L, ExecutionStatus.SUCCESS, null, 5L))
        }
        val second = import(mdr)

        val flows = allFlows()
        assertEquals(2, flows.size)
        assertEquals(listOf("log1"), logsOf("4242").map { it.id })
        val newId = flows.single { it.id != "4242" }.id
        // Warnings must point at the copy that was actually saved, or "Fix" opens the wrong flow.
        second.warnings.mapNotNull { it.flowId }.forEach { assertNotEquals("4242", it); assertEquals(newId, it) }
    }

    @Test
    fun `a flow exported from a MacroDroid import imports back`() {
        // Numeric ids everywhere: the flow store rejects these, a device does not.
        import(
            """
            {"macro":{"m_name":"Kiwi launch","m_GUID":4242,"m_actionList":[{"m_classType":"KeepAwakeAction"}],
            "m_triggerList":[{"m_packageNameList":["com.kiwibrowser.browser"],"m_launched":true,
            "m_classType":"ApplicationLaunchedTrigger"}]},"macroExportVersion":1}
            """.trimIndent(),
        )
        val exported = runBlocking { repository.getById("4242")!! }
        val file = FlowSerializer.encode(exported.toFlowJson())

        val result = import(file)

        assertEquals(false, result.failed)
        assertEquals(2, allFlows().size)
    }
}
