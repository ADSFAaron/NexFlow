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
package com.nexflow.core.automation.repository

import com.nexflow.core.automation.model.ExecutionLog
import com.nexflow.core.automation.model.ExecutionStep
import com.nexflow.core.automation.model.Flow
import kotlinx.coroutines.flow.Flow as KFlow

interface FlowRepository {
    fun observeAll(): KFlow<List<Flow>>
    fun observeEnabled(): KFlow<List<Flow>>
    suspend fun getById(id: String): Flow?
    suspend fun save(flow: Flow)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Writes a finished run: the summary and, in the same transaction, the per-action steps it
     * was made of. Together — a summary saved without its steps would render as an empty build
     * log with no way to tell that from a run that genuinely recorded nothing.
     */
    suspend fun saveExecutionLog(log: ExecutionLog, steps: List<ExecutionStep> = emptyList())
    fun observeLogsForFlow(flowId: String): KFlow<List<ExecutionLog>>
    fun observeRecentLogs(limit: Int = 100): KFlow<List<ExecutionLog>>

    /** One run's summary; null once it is pruned or cleared while being viewed. */
    fun observeLog(logId: String): KFlow<ExecutionLog?>

    /** The steps of one run, in execution order. Empty for runs recorded before v1.3. */
    fun observeStepsForLog(logId: String): KFlow<List<ExecutionStep>>
    suspend fun deleteOldLogs(keepCount: Int, olderThanMs: Long)
    suspend fun deleteAllLogs()
}
