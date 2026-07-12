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
package com.nexflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.ai.GeminiClient
import com.nexflow.ai.GeminiModelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the model picker in Settings: fetches the models available to the user's API key. */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val geminiClient: GeminiClient,
) : ViewModel() {

    sealed interface ModelsState {
        data object Idle : ModelsState
        data object Loading : ModelsState
        data class Loaded(val models: List<GeminiModelInfo>) : ModelsState
        data class Error(val message: String?) : ModelsState
    }

    private val _modelsState = MutableStateFlow<ModelsState>(ModelsState.Idle)
    val modelsState: StateFlow<ModelsState> = _modelsState.asStateFlow()

    fun loadModels(apiKey: String) {
        if (_modelsState.value is ModelsState.Loading) return
        _modelsState.value = ModelsState.Loading
        viewModelScope.launch {
            _modelsState.value = geminiClient.listModels(apiKey).fold(
                onSuccess = { ModelsState.Loaded(GeminiClient.chatModels(it)) },
                onFailure = { ModelsState.Error(it.message) },
            )
        }
    }

    /** Drop stale results, e.g. after the API key changes or the sheet closes. */
    fun resetModels() {
        _modelsState.value = ModelsState.Idle
    }
}
