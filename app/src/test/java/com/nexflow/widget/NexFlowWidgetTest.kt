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
package com.nexflow.widget

import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the widget can still draw from whatever its stored state happens to be. A widget already
 * sitting on a home screen keeps its state across an app update, so this is the one place where
 * two encodings have to coexist.
 */
class NexFlowWidgetTest {

    @Test
    fun `cards are read back from the stored json`() {
        val encoded = """
            [{"id":"a","name":"Morning","icon":"alarm","color":"#FF3E63DD","subtitle":"Time"}]
        """.trimIndent()

        val flow = NexFlowWidget.decodeFlows(encoded).single()

        assertEquals("a", flow.id)
        assertEquals("Morning", flow.name)
        assertEquals("alarm", flow.icon)
        assertEquals("#FF3E63DD", flow.color)
        assertEquals("Time", flow.subtitle)
    }

    @Test
    fun `state written before the card layout still resolves to flows`() {
        // A widget updated to this version but not yet re-synced still holds "id|name" pairs.
        // Reading them is what keeps it from going blank until its next update.
        val flows = NexFlowWidget.decodeFlows("id-1|Morning,id-2|Commute")

        assertEquals(listOf("id-1", "id-2"), flows.map { it.id })
        assertEquals(listOf("Morning", "Commute"), flows.map { it.name })
        assertTrue(flows.all { it.icon == null && it.subtitle.isEmpty() })
    }

    @Test
    fun `a name containing the old separators survives the json encoding`() {
        // The reason the format changed: "Home, work" split into two entries under the old
        // encoding, and neither half named a flow that existed.
        val encoded = """[{"id":"a","name":"Home, work"},{"id":"b","name":"A|B"}]"""

        val flows = NexFlowWidget.decodeFlows(encoded)

        assertEquals(listOf("Home, work", "A|B"), flows.map { it.name })
    }

    @Test
    fun `no state and blank state both mean no cards`() {
        assertTrue(NexFlowWidget.decodeFlows(null).isEmpty())
        assertTrue(NexFlowWidget.decodeFlows("").isEmpty())
        assertTrue(NexFlowWidget.decodeFlows("   ").isEmpty())
    }

    @Test
    fun `unparseable state degrades to no cards rather than throwing`() {
        // provideGlance has nowhere to report an exception to; the launcher would simply show
        // the error view where the widget used to be.
        assertTrue(NexFlowWidget.decodeFlows("{not json at all").isEmpty())
    }

    @Test
    fun `cards are laid out two to a row with the last row left short`() {
        // The grid is a LazyColumn over chunks, so this is the shape the rows are built from.
        val flows = List(3) { NexFlowWidget.WidgetFlow(id = "$it", name = "Flow $it") }

        val rows = flows.chunked(2)

        assertEquals(2, rows.size)
        assertEquals(2, rows[0].size)
        assertEquals(1, rows[1].size, "a lone trailing card must not become a full-width row")
    }

    @Test
    fun `automatic puts recent runs first and tops up with the rest`() {
        val all = listOf(flow("a"), flow("b"), flow("c"))

        val shown = NexFlowWidget.pickFlows(pinnedIds = null, recent = listOf(flow("c")), all = all)

        assertEquals(listOf("c", "a", "b"), shown.map { it.id })
    }

    @Test
    fun `a pick shows exactly those flows in the picked order and nothing else`() {
        val all = listOf(flow("a"), flow("b"), flow("c"), flow("d"))

        val shown = NexFlowWidget.pickFlows(listOf("d", "b"), recent = listOf(flow("a")), all = all)

        assertEquals(listOf("d", "b"), shown.map { it.id }, "recent runs must not leak into a pick")
    }

    @Test
    fun `a picked flow that was deleted drops out instead of drawing a dead card`() {
        val shown = NexFlowWidget.pickFlows(listOf("gone", "a"), recent = emptyList(), all = listOf(flow("a")))

        assertEquals(listOf("a"), shown.map { it.id })
    }

    @Test
    fun `an empty pick stays empty rather than falling back to automatic`() {
        val shown = NexFlowWidget.pickFlows(emptyList(), recent = listOf(flow("a")), all = listOf(flow("a")))

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `stored pick round-trips and absence means automatic`() {
        val ids = listOf("x", "y")

        assertEquals(ids, NexFlowWidget.decodePinnedIds(NexFlowWidget.encodePinnedIds(ids)))
        assertNull(NexFlowWidget.decodePinnedIds(null))
        // Corrupt state keeps the widget in pick mode, showing the "choose flows" hint, rather
        // than silently switching it back to automatic.
        assertEquals(emptyList<String>(), NexFlowWidget.decodePinnedIds("{broken"))
    }

    private fun flow(id: String) = Flow(
        id = id,
        schemaVersion = 1,
        name = id,
        description = "",
        author = null,
        tags = emptyList(),
        enabled = true,
        createdAt = 0,
        updatedAt = 0,
        triggers = emptyList(),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = emptyList(),
        variables = emptyList(),
    )
}
