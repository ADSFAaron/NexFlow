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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "execution_logs",
    foreignKeys = [
        ForeignKey(
            entity = FlowEntity::class,
            parentColumns = ["id"],
            childColumns = ["flow_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["flow_id"]), Index(value = ["triggered_at"])],
)
data class ExecutionLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "flow_id") val flowId: String,
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long,
    /** SUCCESS | FAIL | SKIPPED */
    val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "execution_duration_ms") val executionDurationMs: Long,
)
