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

import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStatus
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.model.Variable
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.flowschema.ActionJson
import com.nexflow.core.flowschema.ConditionJson
import com.nexflow.core.flowschema.TriggerJson
import com.nexflow.core.flowschema.VariableJson
import com.nexflow.data.local.dao.ExecutionLogDao
import com.nexflow.data.local.dao.FlowDao
import com.nexflow.data.local.entity.ExecutionLogEntity
import com.nexflow.data.local.entity.FlowEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class FlowRepositoryImpl @Inject constructor(
    private val flowDao: FlowDao,
    private val logDao: ExecutionLogDao,
) : FlowRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun observeAll(): Flow<List<AutomationFlow>> =
        flowDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEnabled(): Flow<List<AutomationFlow>> =
        flowDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): AutomationFlow? =
        flowDao.getById(id)?.toDomain()

    override suspend fun save(flow: AutomationFlow) {
        flowDao.upsert(flow.toEntity())
    }

    override suspend fun delete(id: String) {
        flowDao.deleteById(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        flowDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    override suspend fun saveExecutionLog(log: ExecutionLog) {
        logDao.insert(log.toEntity())
    }

    override fun observeLogsForFlow(flowId: String): Flow<List<ExecutionLog>> =
        logDao.observeByFlowId(flowId).map { list -> list.map { it.toDomain() } }

    override fun observeRecentLogs(limit: Int): Flow<List<ExecutionLog>> =
        logDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun deleteOldLogs(keepCount: Int, olderThanMs: Long) {
        logDao.trimToCount(keepCount)
        logDao.deleteOlderThan(olderThanMs)
    }

    // --- Entity → Domain ---

    private fun FlowEntity.toDomain(): AutomationFlow = AutomationFlow(
        id = id,
        schemaVersion = schemaVersion,
        name = name,
        description = description,
        author = author,
        tags = runCatching { json.decodeFromString<List<String>>(tags) }.getOrDefault(emptyList()),
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        triggers = runCatching {
            json.decodeFromString<List<TriggerJson>>(triggers).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        triggerLogic = runCatching { TriggerLogic.valueOf(triggerLogic) }.getOrDefault(TriggerLogic.ANY),
        conditions = runCatching {
            json.decodeFromString<List<ConditionJson>>(conditions).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        actions = runCatching {
            json.decodeFromString<List<ActionJson>>(actions).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        variables = runCatching {
            json.decodeFromString<List<VariableJson>>(variables).map { it.toDomain() }
        }.getOrDefault(emptyList()),
    )

    private fun TriggerJson.toDomain() = Trigger(
        id = id,
        type = runCatching { TriggerType.valueOf(type) }.getOrDefault(TriggerType.MANUAL),
        config = config.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: v.toString())
        },
    )

    private fun ConditionJson.toDomain() = Condition(
        id = id,
        type = type,
        config = config.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: v.toString())
        },
        negate = negate,
    )

    private fun ActionJson.toDomain() = Action(
        id = id,
        type = runCatching { ActionType.valueOf(type) }.getOrDefault(ActionType.TOAST),
        config = config.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.contentOrNull ?: v.toString())
        },
        order = order,
        enabled = enabled,
    )

    private fun VariableJson.toDomain() = Variable(
        name = name,
        type = runCatching { VariableType.valueOf(type) }.getOrDefault(VariableType.STRING),
        defaultValue = defaultValue.jsonPrimitive.contentOrNull ?: defaultValue.toString(),
    )

    private fun ExecutionLogEntity.toDomain() = ExecutionLog(
        id = id,
        flowId = flowId,
        triggeredAt = triggeredAt,
        status = runCatching { ExecutionStatus.valueOf(status) }.getOrDefault(ExecutionStatus.FAIL),
        errorMessage = errorMessage,
        executionDurationMs = executionDurationMs,
    )

    // --- Domain → Entity ---

    private fun AutomationFlow.toEntity() = FlowEntity(
        id = id,
        schemaVersion = schemaVersion,
        name = name,
        description = description,
        author = author,
        tags = json.encodeToString(tags),
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        triggers = json.encodeToString(triggers.map { it.toJson() }),
        triggerLogic = triggerLogic.name,
        conditions = json.encodeToString(conditions.map { it.toJson() }),
        actions = json.encodeToString(actions.map { it.toJson() }),
        variables = json.encodeToString(variables.map { it.toJson() }),
    )

    private fun Trigger.toJson() = TriggerJson(
        id = id,
        type = type.name,
        config = JsonObject(config.mapValues { JsonPrimitive(it.value) }),
    )

    private fun Condition.toJson() = ConditionJson(
        id = id,
        type = type,
        config = JsonObject(config.mapValues { JsonPrimitive(it.value) }),
        negate = negate,
    )

    private fun Action.toJson() = ActionJson(
        id = id,
        type = type.name,
        config = JsonObject(config.mapValues { JsonPrimitive(it.value) }),
        order = order,
        enabled = enabled,
    )

    private fun Variable.toJson() = VariableJson(
        name = name,
        type = type.name,
        defaultValue = JsonPrimitive(defaultValue),
    )

    private fun ExecutionLog.toEntity() = ExecutionLogEntity(
        id = id,
        flowId = flowId,
        triggeredAt = triggeredAt,
        status = status.name,
        errorMessage = errorMessage,
        executionDurationMs = executionDurationMs,
    )
}
