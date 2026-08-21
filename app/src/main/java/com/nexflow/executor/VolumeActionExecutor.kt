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
import kotlin.math.roundToInt

/**
 * Sets a stream's volume from a percentage of that stream's range on *this* device.
 *
 * Percent rather than a step number, because a step number does not mean the same thing twice:
 * on the phone this was written against, media has 30 steps and the ringer has 7, and other
 * phones differ again. A flow that stored "15" was asking for full volume on a 15-step device
 * and half volume on a 30-step one — and flows are made to be exported, imported and generated.
 */
class VolumeActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.VOLUME_ADJUST

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val stream = streamOf(action.config["stream"])
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = am.getStreamMaxVolume(stream)

        val percent = action.config[PERCENT_KEY]?.toIntOrNull()
        val target = when {
            percent != null -> stepsFor(percent, maxVolume)
            // Flows written before this action stored a percentage. Their number is a step on
            // this device's scale, which is what it always was — reinterpreting it as a percent
            // would turn every one of them down to a tenth on the spot.
            else -> action.config[LEGACY_LEVEL_KEY]?.toIntOrNull()
                ?: return ActionResult.Failure("No volume level specified")
        }

        return try {
            am.setStreamVolume(stream, target.coerceIn(0, maxVolume), 0)
            ActionResult.Success
        } catch (e: SecurityException) {
            ActionResult.Failure("Volume adjust blocked — grant Do Not Disturb access in Settings: ${e.message}", e)
        }
    }

    companion object {
        const val PERCENT_KEY = "volume_percent"

        /** What the pre-percentage editor wrote: an absolute step. */
        const val LEGACY_LEVEL_KEY = "level"

        /**
         * The old editor's slider only ever went to 15, so a stored 15 meant "as far as this
         * goes" no matter what the device's real maximum was. That is the only reading the old
         * UI supports, and it is what a legacy value converts as when a flow is next edited.
         */
        const val LEGACY_SLIDER_MAX = 15

        fun streamOf(name: String?): Int = when (name?.uppercase()) {
            "RING" -> AudioManager.STREAM_RING
            "ALARM" -> AudioManager.STREAM_ALARM
            "NOTIFICATION" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }

        /**
         * Rounds rather than truncates: on a 7-step ringer, 100% must land on 7 and 50% on 4,
         * where truncating would quietly cap the slider one notch below the top.
         */
        fun stepsFor(percent: Int, maxVolume: Int): Int =
            (percent.coerceIn(0, 100) / 100f * maxVolume).roundToInt()
    }
}
