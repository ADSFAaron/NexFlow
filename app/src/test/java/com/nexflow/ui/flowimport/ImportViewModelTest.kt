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

import com.nexflow.BuildConfig
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.flowschema.GlobalVariableJson
import com.nexflow.core.flowschema.ImportWarnings
import com.nexflow.core.flowschema.TriggerJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A file that fails the schema is refused outright; one with only warnings still imports. And
 * importing never replaces a flow the user already has: a file whose id is already on this device
 * (your own export read back, a friend's flow shared twice) is saved as a new flow beside it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val saved = mutableListOf<Flow>()
    private val flows = mockk<FlowRepository> {
        coEvery { save(any()) } coAnswers { saved += firstArg<Flow>() }
        coEvery { getById(any()) } coAnswers { saved.find { it.id == firstArg<String>() } }
    }
    private val globals = mockk<GlobalVariableRepository>(relaxed = true) {
        coEvery { currentValues() } returns emptyMap()
    }
    private val vm by lazy { ImportViewModel(flows, globals) }

    @BeforeEach fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun reset() = Dispatchers.resetMain()

    private var n = 0
    private fun uuid() = "00000000-0000-4000-8000-%012d".format(n++)

    private fun file(
        id: String = uuid(),
        triggerLogic: String = "ANY",
        withTriggers: Boolean = true,
        triggerType: String = "TIME",
        actionType: String = "TOAST",
    ) = FlowSerializer.encode(
        FlowJson(
            schemaVersion = 1, id = id, name = "Morning", description = "", tags = emptyList(),
            enabled = false, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            triggers = if (withTriggers) listOf(TriggerJson(uuid(), triggerType, JsonObject(emptyMap()))) else emptyList(),
            triggerLogic = triggerLogic, conditions = emptyList(),
            actions = listOf(ActionJson(uuid(), actionType, JsonObject(emptyMap()), order = 0, enabled = true)),
            variables = emptyList(),
            globalVariables = listOf(GlobalVariableJson("count", "INTEGER", "0")),
        ),
    )

    @Test
    fun `a file with a schema error is not saved, and creates no globals`() {
        vm.importFlowJson(file(triggerLogic = "SOME"))

        val result = vm.result.value!!
        assertTrue(result.failed)
        assertEquals(0, result.imported)
        assertTrue(result.rejected.single().startsWith("trigger_logic:"), "${result.rejected}")
        assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { globals.save(any(), any()) }
    }

    @Test
    fun `a file with only warnings still imports, with the warning shown`() {
        // A flow with no triggers yet is something the editor itself allows.
        vm.importFlowJson(file(withTriggers = false))

        val result = vm.result.value!!
        assertEquals(false, result.failed)
        assertEquals(1, result.imported)
        assertEquals(1, saved.size)
        assertTrue(result.warnings.any { it.code == ImportWarnings.SCHEMA_ERROR && "triggers" in it.args })
    }

    @Test
    fun `types from a newer NexFlow are reported, not silently swapped`() {
        vm.importFlowJson(file(triggerType = "FUTURE_TRIGGER", actionType = "FUTURE_ACTION"))

        val result = vm.result.value!!
        assertEquals(1, result.imported)
        val byCode = result.warnings.associateBy { it.code }
        assertEquals(listOf("FUTURE_TRIGGER"), byCode.getValue(ImportWarnings.UNKNOWN_TRIGGER_TYPE).args)
        assertEquals(listOf("FUTURE_ACTION"), byCode.getValue(ImportWarnings.UNKNOWN_ACTION_TYPE).args)
        assertTrue(listOf(ImportWarnings.UNKNOWN_TRIGGER_TYPE, ImportWarnings.UNKNOWN_ACTION_TYPE).all { byCode.getValue(it).isFixable })
    }

    @Test
    fun `a type this flavor hides is reported only where it is hidden`() {
        vm.importFlowJson(file(triggerType = "SMS_RECEIVED", actionType = "SEND_SMS"))

        val hidden = vm.result.value!!.warnings.filter { it.code == ImportWarnings.NOT_IN_THIS_BUILD }
        // Runs in both flavors: the Play build hides SMS, the GitHub build has it.
        val expected = if (BuildConfig.FLAVOR == "play") listOf("SMS_RECEIVED", "SEND_SMS") else emptyList()
        assertEquals(expected, hidden.map { it.args.single() })
    }

    @Test
    fun `a flow new to this device keeps the id from the file`() {
        vm.importFlowJson(file(id = SHARED))

        assertEquals(listOf(SHARED), saved.map { it.id })
    }

    @Test
    fun `a flow whose id is already taken is saved as a new flow beside it`() {
        vm.importFlowJson(file(id = SHARED))
        // No triggers: a warning, so there is something to check the flow id of.
        vm.importFlowJson(file(id = SHARED, withTriggers = false))

        assertEquals(2, saved.size)
        assertEquals(SHARED, saved[0].id)
        assertNotEquals(SHARED, saved[1].id, "the existing flow must not be overwritten")
        // Warnings point at the flow that was actually saved, so the review UI opens the copy.
        val warnings = vm.result.value!!.warnings
        assertTrue(warnings.isNotEmpty())
        warnings.forEach { assertEquals(saved[1].id, it.flowId) }
    }

    private companion object {
        const val SHARED = "11111111-1111-4111-8111-111111111111"
    }
}
