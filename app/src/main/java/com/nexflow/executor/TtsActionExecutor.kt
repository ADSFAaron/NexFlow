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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class TtsActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.TTS

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val text = action.config["text"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Skipped

        return suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    cont.resume(ActionResult.Failure("TTS initialisation failed (status=$status)"))
                    return@TextToSpeech
                }
                val engine = tts ?: return@TextToSpeech
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        engine.stop(); engine.shutdown()
                        cont.resume(ActionResult.Success)
                    }
                    @Deprecated("Deprecated in API level 21")
                    override fun onError(id: String?) {
                        engine.stop(); engine.shutdown()
                        cont.resume(ActionResult.Failure("TTS speech error"))
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nf_${System.currentTimeMillis()}")
            }
            cont.invokeOnCancellation { tts?.stop(); tts?.shutdown() }
        }
    }
}
