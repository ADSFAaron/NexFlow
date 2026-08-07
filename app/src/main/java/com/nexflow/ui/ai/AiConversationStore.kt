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

import android.util.Log
import com.nexflow.ai.GeminiContent
import com.nexflow.data.local.dao.AiConversationDao
import com.nexflow.data.local.entity.AiConversationEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A conversation as it lives outside the database. */
data class StoredConversation(
    val messages: List<ChatMessage>,
    val history: List<GeminiContent>,
    val savedFlowId: String?,
    val tokensUsed: Int,
)

/**
 * Reads and writes whole conversations. Both halves travel together — the visible transcript
 * and the model's own history — because restoring one without the other produces an assistant
 * that has apparently forgotten a conversation the user can still see.
 *
 * A row that fails to decode (an old schema, a truncated write) is dropped rather than thrown:
 * losing a transcript is bad, but refusing to open the chat at all is worse.
 */
@Singleton
class AiConversationStore @Inject constructor(
    private val dao: AiConversationDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val messagesSerializer = ListSerializer(ChatMessage.serializer())
    private val historySerializer = ListSerializer(GeminiContent.serializer())

    suspend fun load(flowId: String?): StoredConversation? {
        val row = dao.getByFlowId(flowId ?: AiConversationEntity.FREE_CHAT) ?: return null
        // Parsing a long transcript is real work; the caller lives on the main thread.
        return runCatching {
            withContext(Dispatchers.Default) {
                StoredConversation(
                    messages = json.decodeFromString(messagesSerializer, row.messagesJson),
                    history = json.decodeFromString(historySerializer, row.historyJson),
                    savedFlowId = row.savedFlowId,
                    tokensUsed = row.tokensUsed,
                )
            }
        }.getOrElse {
            Log.w(TAG, "dropping unreadable conversation for flow=$flowId", it)
            dao.deleteByFlowId(flowId ?: AiConversationEntity.FREE_CHAT)
            null
        }
    }

    suspend fun save(flowId: String?, conversation: StoredConversation) {
        // An empty conversation is the absence of one — don't resurrect a cleared chat.
        if (conversation.messages.isEmpty()) {
            dao.deleteByFlowId(flowId ?: AiConversationEntity.FREE_CHAT)
            return
        }
        runCatching {
            val entity = withContext(Dispatchers.Default) {
                AiConversationEntity(
                    flowId = flowId ?: AiConversationEntity.FREE_CHAT,
                    messagesJson = json.encodeToString(messagesSerializer, conversation.messages),
                    historyJson = json.encodeToString(historySerializer, conversation.history),
                    savedFlowId = conversation.savedFlowId,
                    tokensUsed = conversation.tokensUsed,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            dao.upsert(entity)
        }.onFailure {
            // Persistence is a convenience; a failure here must never break the live chat.
            Log.w(TAG, "could not persist conversation for flow=$flowId", it)
        }
    }

    suspend fun clear(flowId: String?) {
        dao.deleteByFlowId(flowId ?: AiConversationEntity.FREE_CHAT)
    }

    /** Forgets conversations whose flow has since been deleted. */
    suspend fun purgeOrphans() {
        runCatching { dao.deleteOrphans() }
            .onFailure { Log.w(TAG, "orphan purge failed", it) }
    }

    private companion object {
        const val TAG = "AiConversationStore"
    }
}
