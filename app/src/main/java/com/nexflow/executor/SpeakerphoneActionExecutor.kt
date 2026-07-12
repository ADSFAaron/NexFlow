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
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Routes communication audio to the built-in speaker (or back). API 31+ uses
 * setCommunicationDevice; older versions the deprecated setSpeakerphoneOn.
 *
 * Best-effort by design: whether this affects an ongoing cellular call is up to the OEM's
 * telephony stack — non-telecom apps have no guaranteed control over in-call routing on
 * modern Android. Works most reliably right after the call is answered (e.g. an
 * INCOMING_CALL-triggered flow with a short DELAY before this action).
 */
class SpeakerphoneActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.SPEAKERPHONE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val on = action.config["state"]?.uppercase() != "OFF"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val speaker = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?: return ActionResult.Failure("No built-in speaker available for communication audio")
                audioManager.setCommunicationDevice(speaker)
            } else {
                audioManager.clearCommunicationDevice()
                true
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = on
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn == on
        }

        return if (ok) ActionResult.Success
        else ActionResult.Failure("Speakerphone routing was rejected by the system")
    }
}
