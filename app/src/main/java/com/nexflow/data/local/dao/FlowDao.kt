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
import androidx.room.Update
import com.nexflow.data.local.entity.FlowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {

    @Query("SELECT * FROM flows ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE enabled = 1 ORDER BY updated_at DESC")
    fun observeEnabled(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE id = :id")
    suspend fun getById(id: String): FlowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FlowEntity)

    @Query("DELETE FROM flows WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Deliberately leaves `updated_at` alone. The list is ordered by it, so bumping it here made
     * flicking a flow's switch fire the flow up to the top of the list and out from under the
     * user's finger — which reads as the flow having vanished. Flipping a switch is not an edit
     * to the flow.
     */
    @Query("UPDATE flows SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
