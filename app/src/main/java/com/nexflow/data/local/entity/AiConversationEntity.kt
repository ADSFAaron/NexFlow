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
package com.nexflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved Gemini conversation, so it survives the app being killed in the background.
 *
 * Stored as two JSON blobs rather than a row per message: a conversation is only ever read and
 * written whole, and the model-side history has no sensible relational shape anyway.
 *
 * Both blobs matter. [messagesJson] alone would restore a transcript the user can read while
 * the model remembers nothing of it — worse than starting over, because it looks like it works.
 */
@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    /** The flow this conversation edits, or [FREE_CHAT] for the build-something-new chat. */
    @PrimaryKey @ColumnInfo(name = "flow_id") val flowId: String,
    /** Serialized `List<ChatMessage>` — what the screen shows. */
    @ColumnInfo(name = "messages_json") val messagesJson: String,
    /** Serialized `List<GeminiContent>` — what the model is told it already said. */
    @ColumnInfo(name = "history_json") val historyJson: String,
    /** Flow this conversation has already saved; later saves update it in place. */
    @ColumnInfo(name = "saved_flow_id") val savedFlowId: String?,
    @ColumnInfo(name = "tokens_used") val tokensUsed: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        /** Primary key for the conversation that isn't about any particular flow. */
        const val FREE_CHAT = ""
    }
}
