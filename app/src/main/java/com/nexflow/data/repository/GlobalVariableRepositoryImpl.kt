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
package com.nexflow.data.repository

import com.nexflow.core.automation.model.GlobalVariable
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.data.local.dao.GlobalVariableDao
import com.nexflow.data.local.entity.GlobalVariableEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GlobalVariableRepositoryImpl @Inject constructor(
    private val dao: GlobalVariableDao,
) : GlobalVariableRepository {

    override fun observeAll(): Flow<List<GlobalVariable>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun currentValues(): Map<String, String> =
        dao.getAll().associate { it.name to it.currentValue }

    override suspend fun save(variable: GlobalVariable, originalName: String?) {
        // A rename can't be an UPDATE (name is the primary key): drop the old row first.
        if (originalName != null && originalName != variable.name) {
            dao.deleteByName(originalName)
        }
        dao.upsert(
            GlobalVariableEntity(
                name = variable.name,
                type = variable.type.name,
                defaultValue = variable.defaultValue,
                currentValue = variable.currentValue,
            ),
        )
    }

    override suspend fun updateValue(name: String, value: String) =
        dao.updateCurrentValue(name, value)

    override suspend fun delete(name: String) = dao.deleteByName(name)

    private fun GlobalVariableEntity.toDomain() = GlobalVariable(
        name = name,
        type = runCatching { VariableType.valueOf(type) }.getOrDefault(VariableType.STRING),
        defaultValue = defaultValue,
        currentValue = currentValue,
    )
}
