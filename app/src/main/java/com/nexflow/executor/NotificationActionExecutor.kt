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
import androidx.core.app.NotificationCompat
import com.nexflow.R
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NotificationActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.NOTIFICATION

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val title = action.config["title"]?.takeIf { it.isNotBlank() } ?: "NexFlow"
        val message = action.config["message"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Skipped

        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
        return ActionResult.Success
    }

    companion object {
        const val CHANNEL_ACTIONS = "nexflow_actions"
    }
}
