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
import android.view.KeyEvent
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.MEDIA_PLAY_PAUSE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val mediaAction = action.config["action"]?.uppercase() ?: "TOGGLE"
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (mediaAction) {
            "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ActionResult.Success
    }
}
