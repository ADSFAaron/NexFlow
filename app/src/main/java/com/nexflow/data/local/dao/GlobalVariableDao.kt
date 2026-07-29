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
import com.nexflow.data.local.entity.GlobalVariableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlobalVariableDao {

    @Query("SELECT * FROM global_variables ORDER BY name")
    fun observeAll(): Flow<List<GlobalVariableEntity>>

    @Query("SELECT * FROM global_variables")
    suspend fun getAll(): List<GlobalVariableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GlobalVariableEntity)

    @Query("UPDATE global_variables SET current_value = :value WHERE name = :name")
    suspend fun updateCurrentValue(name: String, value: String)

    @Query("DELETE FROM global_variables WHERE name = :name")
    suspend fun deleteByName(name: String)
}
