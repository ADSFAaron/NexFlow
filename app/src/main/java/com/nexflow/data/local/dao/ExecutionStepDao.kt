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
import com.nexflow.data.local.entity.ExecutionStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionStepDao {

    /** One call per run: a flow with thirty steps must not become thirty transactions. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExecutionStepEntity>)

    @Query("SELECT * FROM execution_steps WHERE log_id = :logId ORDER BY seq ASC")
    fun observeForLog(logId: String): Flow<List<ExecutionStepEntity>>

    // No delete here on purpose: steps are never removed on their own. They go when the run they
    // belong to goes, via the entity's CASCADE, so there is exactly one way for a step to die and
    // no path that can leave a run showing a partial log.
}
