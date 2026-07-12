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
package com.nexflow.ui.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Screen-scoped wrapper around the system [SpeechRecognizer] (create with `remember { ... }`
 * and release via `DisposableEffect { onDispose { destroy() } }`). SpeechRecognizer is
 * main-thread-only and must not outlive the screen, so it can't live in a ViewModel.
 *
 * Partial results stream into [VoiceState.Listening]; the final transcript lands in
 * [VoiceState.Result] for the caller to put into the text field (never auto-send).
 */
class VoiceInputController(private val context: Context) {

    sealed interface VoiceState {
        data object Idle : VoiceState
        data class Listening(val partialText: String) : VoiceState
        data class Result(val text: String) : VoiceState
        data object Error : VoiceState
        data object Unavailable : VoiceState
    }

    var state: VoiceState by mutableStateOf(VoiceState.Idle)
        private set

    val isListening: Boolean get() = state is VoiceState.Listening

    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state = VoiceState.Unavailable
            return
        }
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        state = VoiceState.Listening(partialText = "")
        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                // Follows the in-app language (AppCompat per-app locales update the default).
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            },
        )
    }

    fun stop() {
        recognizer?.stopListening()
    }

    /** Call after consuming [VoiceState.Result]/[VoiceState.Error]. */
    fun consumeState() {
        if (!isListening) state = VoiceState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        state = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) state = VoiceState.Listening(partialText = text)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            state = if (text.isBlank()) VoiceState.Error else VoiceState.Result(text)
        }

        override fun onError(error: Int) {
            // NO_MATCH / SPEECH_TIMEOUT just mean nothing was heard — reset quietly.
            state = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT,
                -> VoiceState.Idle
                else -> VoiceState.Error
            }
        }
    }
}
