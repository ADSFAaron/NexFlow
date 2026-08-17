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
package com.nexflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexflow.data.local.entity.ExecutionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExecutionLogEntity)

    @Query("SELECT * FROM execution_logs WHERE flow_id = :flowId ORDER BY triggered_at DESC")
    fun observeByFlowId(flowId: String): Flow<List<ExecutionLogEntity>>

    /** Emits null once the pruner (or "clear all") removes the run being viewed. */
    @Query("SELECT * FROM execution_logs WHERE id = :logId")
    fun observeById(logId: String): Flow<ExecutionLogEntity?>

    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun totalCount(): Int

    /**
     * Trims the table to at most [keepCount] most-recent rows, then removes rows older than
     * [olderThanMs] epoch milliseconds. Called periodically from WorkManager.
     */
    @Query(
        """
        DELETE FROM execution_logs WHERE id NOT IN (
            SELECT id FROM execution_logs ORDER BY triggered_at DESC LIMIT :keepCount
        )
        """,
    )
    suspend fun trimToCount(keepCount: Int)

    @Query("DELETE FROM execution_logs WHERE triggered_at < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)

    @Query("SELECT * FROM execution_logs ORDER BY triggered_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionLogEntity>>

    @Query("DELETE FROM execution_logs WHERE flow_id = :flowId")
    suspend fun deleteByFlowId(flowId: String)

    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()
}
