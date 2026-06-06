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
package com.nexflow.executor

import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import kotlinx.coroutines.delay
import javax.inject.Inject

class DelayActionExecutor @Inject constructor() : ActionExecutor {

    override val supportedType = ActionType.DELAY

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        // New keys: duration_value + duration_unit. Fallback to legacy duration_ms.
        val rawValue = action.config["duration_value"] ?: action.config["duration_ms"] ?: "0"
        val resolved = resolveVariables(rawValue, variables)
        val value = resolved.toDoubleOrNull() ?: 0.0
        val unit = action.config["duration_unit"] ?: "MS"
        val durationMs = when (unit) {
            "SEC" -> (value * 1_000.0).toLong()
            else -> value.toLong()
        }.coerceIn(0L, 3_600_000L)

        if (durationMs > 0) delay(durationMs)
        return ActionResult.Success
    }
}

/** Replaces {{variable_name}} tokens with values from the variables map. */
internal fun resolveVariables(value: String, variables: Map<String, String>): String =
    Regex("""\{\{([^}]+)}}""").replace(value) { mr ->
        variables[mr.groupValues[1].trim()] ?: mr.value
    }
