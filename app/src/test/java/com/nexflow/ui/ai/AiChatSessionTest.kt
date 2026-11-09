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
package com.nexflow.ui.ai

import android.content.Context
import android.content.SharedPreferences
import com.nexflow.R
import com.nexflow.ai.AiChatOrchestrator
import com.nexflow.ai.AiImageStore
import com.nexflow.ai.AppShortcutsSource
import com.nexflow.ai.GeminiClient
import com.nexflow.ai.InstalledAppsSource
import com.nexflow.ai.OpenAiCompatibleClient
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.data.local.dao.AiConversationDao
import com.nexflow.data.local.entity.AiConversationEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Which conversation the AI chat shows, as the user moves between the Flows tab's AI entry,
 * each flow's "edit with AI", and "start over".
 *
 * The session is one app-wide object holding one conversation at a time, and each of these
 * moves swaps it. Every case below is one the app got wrong without a test noticing: a flow's
 * transcript left on screen under another flow's name, the free chat opening on the last
 * flow edit, and "start over" there reloading that flow instead of giving a blank chat.
 *
 * Real store, orchestrator and session over an in-memory DAO, so what is checked is what the
 * user would see after leaving and coming back — not just the session's own fields.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiChatSessionTest {

    /** The database table, in memory. */
    private class FakeDao : AiConversationDao {
        val rows = java.util.concurrent.ConcurrentHashMap<String, AiConversationEntity>()
        override suspend fun getByFlowId(flowId: String) = rows[flowId]
        override suspend fun allMessagesJson() = rows.values.map { it.messagesJson }
        override suspend fun upsert(entity: AiConversationEntity) {
            rows[entity.flowId] = entity
        }
        override suspend fun deleteByFlowId(flowId: String) {
            rows.remove(flowId)
        }
        override suspend fun deleteOrphans() = Unit
    }

    private val dao = FakeDao()
    private val store = AiConversationStore(dao)
    private val images = mockk<AiImageStore>(relaxed = true)

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
        every { getString(any()) } returns "x"
        every { getString(R.string.ai_editing_flow_intro, *anyVararg()) } answers {
            "intro:${(it.invocation.args[1] as Array<*>)[0]}"
        }
    }

    private fun flow(id: String, name: String) = Flow(
        id = id, schemaVersion = 1, name = name, description = "", author = null,
        icon = null, iconColor = null, tags = emptyList(), enabled = true,
        createdAt = 0L, updatedAt = 0L, triggers = emptyList(), triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(), actions = emptyList(), variables = emptyList(),
    )

    private val flowA = flow("a", "Morning")
    private val flowB = flow("b", "Driving")

    /**
     * Holds flow B's load open until a test releases it. On a phone the flow comes out of Room
     * and there is a moment between "opened B" and "B is here"; without the gate the fake
     * answers instantly, that moment never exists, and a transcript left over from A would
     * never be seen — the first version of these tests passed against the bug for that reason.
     */
    private val flowBGate = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var gateB = flowBGate
    private val flows = mockk<FlowRepository> {
        coEvery { getById("a") } returns flowA
        coEvery { getById("b") } coAnswers {
            gateB.await()
            flowB
        }
    }

    private fun newSession() = AiChatSession(
        orchestrator = AiChatOrchestrator(
            mockk<GeminiClient>(),
            mockk<OpenAiCompatibleClient>(),
            mockk<InstalledAppsSource>(),
            mockk<AppShortcutsSource>(),
            mockk<GlobalVariableRepository>(),
            images,
            context,
        ),
        flowRepository = flows,
        store = store,
        images = images,
        context = context,
    )

    private fun stored(text: String, tokens: Int = 100) = StoredConversation(
        messages = listOf(ChatMessage.UserText(text)),
        history = emptyList(),
        savedFlowId = null,
        tokensUsed = tokens,
    )

    private fun AiChatSession.texts() = messages.value.map {
        when (it) {
            is ChatMessage.UserText -> it.text
            is ChatMessage.AssistantText -> it.text
            else -> it::class.simpleName
        }
    }

    /** The store hops to Dispatchers.Default, so outcomes land on another thread a little later. */
    private fun eventually(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > deadline) fail<Unit>("timed out waiting for: $what")
            Thread.sleep(10)
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening a flow resumes the conversation stored for it`() {
        runBlocking { store.save("a", stored("old question about A")) }
        val session = newSession()

        session.startFlowEdit("a")

        eventually("A's transcript") { session.texts() == listOf("old question about A") }
        assertEquals("Morning", session.editingFlow.value?.name)
        assertEquals(100, session.tokensUsed)
    }

    @Test
    fun `opening another flow takes the first one's transcript off the screen at once`() {
        runBlocking { store.save("a", stored("old question about A")) }
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's transcript") { session.texts().isNotEmpty() }
        val before = session.conversationId.value
        gateB = CompletableDeferred()

        session.startFlowEdit("b")

        // B is still loading. Nothing of A may be showing now, under what is about to be B.
        assertEquals(emptyList<String?>(), session.texts(), "A's transcript lingered while B loaded")
        assertTrue(session.editingFlow.value?.name != "Morning", "A's name stayed in the bar")
        assertNotEquals(before, session.conversationId.value, "the screen was not told to drop the old list")

        gateB.complete(Unit)
        eventually("B's intro") { session.texts() == listOf("intro:Driving") }
        assertEquals("Driving", session.editingFlow.value?.name)
        assertEquals(0, session.tokensUsed)
    }

    @Test
    fun `the Flows tab's AI entry leaves a flow edit for the free chat and its own transcript`() {
        runBlocking {
            store.save(null, stored("free chat question"))
            store.save("a", stored("old question about A"))
        }
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's transcript") { session.texts().isNotEmpty() }

        session.restoreFreeChat()

        eventually("the free chat") { session.texts() == listOf("free chat question") }
        assertNull(session.editingFlow.value, "still scoped to flow A")
        assertNull(session.baselineFlow.value)
    }

    @Test
    fun `the free chat after a flow edit is blank when it has nothing stored`() {
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's intro") { session.texts() == listOf("intro:Morning") }

        session.restoreFreeChat()

        assertEquals(emptyList<String?>(), session.texts())
        assertNull(session.editingFlow.value)
    }

    @Test
    fun `start over in the free chat gives a blank chat that stays blank after a restart`() {
        runBlocking { store.save(null, stored("free chat question")) }
        val session = newSession()
        session.restoreFreeChat()
        eventually("the free chat") { session.texts().isNotEmpty() }

        session.newChat()

        assertEquals(emptyList<String?>(), session.texts())
        assertNull(session.editingFlow.value)
        eventually("the stored free chat to be cleared") { dao.rows[AiConversationEntity.FREE_CHAT] == null }

        val afterRestart = newSession()
        afterRestart.restoreFreeChat()
        Thread.sleep(100)
        assertEquals(emptyList<String?>(), afterRestart.texts(), "the old free chat came back")
    }

    @Test
    fun `start over in a flow edit clears that flow's transcript and starts it afresh`() {
        runBlocking { store.save("a", stored("old question about A", tokens = 4_000)) }
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's transcript") { session.texts().isNotEmpty() }

        session.newChat()

        eventually("A's intro") { session.texts() == listOf("intro:Morning") }
        assertEquals("Morning", session.editingFlow.value?.name, "start over left the flow")
        assertEquals(0, session.tokensUsed)
        eventually("A's stored transcript to be cleared") { dao.rows["a"] == null }

        val afterRestart = newSession()
        afterRestart.startFlowEdit("a")
        eventually("A's intro after a restart") { afterRestart.texts() == listOf("intro:Morning") }
    }

    @Test
    fun `start over keeps other flows' transcripts`() {
        runBlocking {
            store.save("a", stored("old question about A"))
            store.save("b", stored("old question about B"))
        }
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's transcript") { session.texts().isNotEmpty() }

        session.newChat()
        eventually("A's stored transcript to be cleared") { dao.rows["a"] == null }

        session.startFlowEdit("b")
        eventually("B's transcript") { session.texts() == listOf("old question about B") }
    }

    @Test
    fun `reopening the flow being edited keeps the transcript where it is`() {
        val session = newSession()
        session.startFlowEdit("a")
        eventually("A's intro") { session.texts().isNotEmpty() }
        val before = session.conversationId.value

        session.startFlowEdit("a")

        assertEquals(before, session.conversationId.value, "re-entering the same flow reset the list")
        assertEquals(listOf("intro:Morning"), session.texts())
    }
}
