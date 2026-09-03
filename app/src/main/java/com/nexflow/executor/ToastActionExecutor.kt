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

import android.content.Context
import android.widget.Toast
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ToastActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.TOAST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val message = action.config["message"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Skipped
        withContext(Dispatchers.Main) {
            // Toasts queue rather than replace: a loop that toasts three times plays out over
            // six seconds, still popping messages long after the flow itself has finished, which
            // reads as the run being stuck. Cancelling the one on screen before posting the next
            // keeps what the user sees in step with where the flow actually is.
            onScreen?.cancel()
            onScreen = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
        }
        return ActionResult.Success
    }

    private companion object {
        /** The toast this app last posted. Only ever touched on the main thread. */
        var onScreen: Toast? = null
    }
}
