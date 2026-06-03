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
package com.nexflow.core.automation.trigger

import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import kotlinx.coroutines.flow.Flow

interface TriggerHandler {
    val supportedType: TriggerType
    fun observe(trigger: Trigger): Flow<TriggerEvent>
    fun canHandle(type: TriggerType): Boolean = type == supportedType
}

data class TriggerEvent(
    val triggerId: String,
    val flowId: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)
