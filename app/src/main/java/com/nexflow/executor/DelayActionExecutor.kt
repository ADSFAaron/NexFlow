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
        val ms = action.config["duration_ms"]?.toLongOrNull()?.coerceIn(0L, 300_000L) ?: 1_000L
        delay(ms)
        return ActionResult.Success
    }
}
