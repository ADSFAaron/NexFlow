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

import android.app.NotificationManager
import android.content.Context
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DndActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.DND_TOGGLE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult.Failure(
                "Do Not Disturb access not granted — enable in Settings > Special app access > Do Not Disturb",
            )
        }

        val currentlyActive =
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val targetOn = when (action.config["state"]?.uppercase()) {
            "ON" -> true
            "OFF" -> false
            else -> !currentlyActive
        }

        nm.setInterruptionFilter(
            if (targetOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return ActionResult.Success
    }
}
