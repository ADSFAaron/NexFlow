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

/**
 * One action's outcome inside one run — the rows the per-run build log is drawn from.
 *
 * The CASCADE to [ExecutionLogEntity] is what keeps this table bounded: `LogPrunerWorker` only
 * ever deletes parent logs, and without the cascade every step it left behind would accumulate
 * forever with no query anywhere that could still reach it.
 *
 * `(log_id, seq)` is the primary key rather than a generated row id: seq is already unique within
 * a run and is the order the steps must be read back in, so the key doubles as the sort index.
 */
@Entity(
    tableName = "execution_steps",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["log_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["log_id", "seq"],
    indices = [Index(value = ["log_id"])],
)
data class ExecutionStepEntity(
    @ColumnInfo(name = "log_id") val logId: String,
    val seq: Int,
    @ColumnInfo(name = "action_id") val actionId: String,
    /** [com.nexflow.core.automation.model.ActionType] name; stored as text so an unknown
     *  type from a downgraded install reads back as an unrecognized row instead of crashing. */
    @ColumnInfo(name = "action_type") val actionType: String,
    val depth: Int,
    val iteration: Int,
    /** SUCCESS | FAIL | SKIPPED */
    val status: String,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    /** Localization token for a structural remark — see [com.nexflow.core.automation.model.ExecutionStep.note]. */
    val note: String?,
    /** Post-substitution config; only written when the user turned on detailed logging. */
    @ColumnInfo(name = "resolved_config") val resolvedConfig: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)
