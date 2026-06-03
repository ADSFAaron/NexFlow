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
import com.nexflow.data.local.entity.VariableEntity

@Dao
interface VariableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VariableEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<VariableEntity>)

    @Query("SELECT * FROM variables WHERE flow_id = :flowId")
    suspend fun getByFlowId(flowId: String): List<VariableEntity>

    @Query("UPDATE variables SET current_value = :value WHERE flow_id = :flowId AND name = :name")
    suspend fun updateCurrentValue(flowId: String, name: String, value: String)

    @Query("DELETE FROM variables WHERE flow_id = :flowId")
    suspend fun deleteByFlowId(flowId: String)
}
