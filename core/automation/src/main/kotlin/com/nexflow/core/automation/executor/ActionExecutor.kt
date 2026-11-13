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
package com.nexflow.core.automation.executor

import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType

interface ActionExecutor {
    val supportedType: ActionType
    suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult
}

sealed class ActionResult {
    data object Success : ActionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ActionResult()
    /**
     * Did not run, without that being an error. [note] is a log token saying why (see the NOTE_
     * constants in FlowInterpreter); null leaves the choice of wording to the interpreter.
     */
    data class Skipped(val note: String? = null) : ActionResult()
}
