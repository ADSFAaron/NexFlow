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

import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.data.toFlowJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Importing must never replace a flow the user already has. Saving is an upsert, so a file whose
 * id is already on this device — your own export read back, a friend's flow shared twice — used
 * to overwrite the existing flow, and every change made to it since, without a word.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val repository = mockk<FlowRepository>(relaxed = true)
    private val globals = mockk<GlobalVariableRepository>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { globals.currentValues() } returns emptyMap()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun flow(id: String) = Flow(
        id = id, schemaVersion = 1, name = "Shared", description = "", author = null,
        icon = null, iconColor = null, tags = emptyList(), enabled = false,
        createdAt = 0L, updatedAt = 0L, triggers = emptyList(), triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(), actions = emptyList(), variables = emptyList(),
    )

    private fun fileOf(id: String) = FlowSerializer.encode(flow(id).toFlowJson())

    @Test
    fun `a flow new to this device keeps the id from the file`() {
        coEvery { repository.getById("abc") } returns null
        val saved = slot<Flow>()
        coEvery { repository.save(capture(saved)) } returns Unit

        ImportViewModel(repository, globals).importFlowJson(fileOf("abc"))

        assertEquals("abc", saved.captured.id)
    }

    @Test
    fun `a flow whose id is already taken is saved as a new flow beside it`() {
        coEvery { repository.getById("abc") } returns flow("abc")
        val saved = slot<Flow>()
        coEvery { repository.save(capture(saved)) } returns Unit

        val vm = ImportViewModel(repository, globals)
        vm.importFlowJson(fileOf("abc"))

        assertNotEquals("abc", saved.captured.id, "the existing flow must not be overwritten")
        coVerify(exactly = 0) { repository.save(match { it.id == "abc" }) }
        // Warnings point at the flow that was actually saved, so the review UI opens the copy.
        vm.result.value?.warnings?.forEach { assertEquals(saved.captured.id, it.flowId) }
    }
}
