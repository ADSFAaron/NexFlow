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
import android.media.AudioManager
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VolumeActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.VOLUME_ADJUST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val level = action.config["level"]?.toIntOrNull()
            ?: return ActionResult.Failure("No volume level specified")
        val stream = when (action.config["stream"]?.uppercase()) {
            "RING" -> AudioManager.STREAM_RING
            "ALARM" -> AudioManager.STREAM_ALARM
            "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = am.getStreamMaxVolume(stream)
        return try {
            am.setStreamVolume(stream, level.coerceIn(0, maxVolume), 0)
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Volume adjust blocked — grant Do Not Disturb access in Settings: ${e.message}", e)
        }
    }
}
