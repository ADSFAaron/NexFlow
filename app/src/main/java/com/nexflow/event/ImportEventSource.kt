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
package com.nexflow.event

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges MainActivity share/view intents → FlowsScreen import without Hilt coupling. */
object ImportEventSource {
    private val _pendingContent = MutableStateFlow<String?>(null)
    val pendingContent: StateFlow<String?> = _pendingContent.asStateFlow()

    fun push(content: String) { _pendingContent.value = content }
    fun clear() { _pendingContent.value = null }
}
