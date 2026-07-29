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

import com.nexflow.core.automation.model.GlobalVariable
import kotlinx.coroutines.flow.Flow as KFlow

/** Store for global (cross-flow) variables. */
interface GlobalVariableRepository {
    fun observeAll(): KFlow<List<GlobalVariable>>

    /** Snapshot of every global as name -> current value, for a single flow run. */
    suspend fun currentValues(): Map<String, String>

    /** Create or replace a global variable (keyed by [GlobalVariable.name]). */
    suspend fun save(variable: GlobalVariable, originalName: String? = null)

    /** Update just the runtime value of an existing global; no-op if it no longer exists. */
    suspend fun updateValue(name: String, value: String)

    suspend fun delete(name: String)
}
