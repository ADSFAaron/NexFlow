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
package com.nexflow.ui.globalvars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.GlobalVariable
import com.nexflow.core.automation.repository.GlobalVariableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalVariablesViewModel @Inject constructor(
    private val repository: GlobalVariableRepository,
) : ViewModel() {

    val variables: StateFlow<List<GlobalVariable>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Create or update a global variable. On create, currentValue starts at the default;
     * on edit we preserve the running value unless the caller reset it.
     */
    fun save(originalName: String?, variable: GlobalVariable) {
        viewModelScope.launch { repository.save(variable, originalName) }
    }

    fun delete(name: String) {
        viewModelScope.launch { repository.delete(name) }
    }

    fun resetToDefault(variable: GlobalVariable) {
        viewModelScope.launch { repository.updateValue(variable.name, variable.defaultValue) }
    }
}
