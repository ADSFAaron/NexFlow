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
package com.nexflow.ui.flows.detail.config

sealed class ConfigField {
    abstract val key: String
    abstract val label: String

    data class TextInput(
        override val key: String,
        override val label: String,
        val hint: String = "",
        val multiline: Boolean = false,
    ) : ConfigField()

    data class Dropdown(
        override val key: String,
        override val label: String,
        /** List of (storedValue, displayLabel) pairs. */
        val options: List<Pair<String, String>>,
    ) : ConfigField()

    data class Slider(
        override val key: String,
        override val label: String,
        val min: Int,
        val max: Int,
        val unit: String = "",
    ) : ConfigField()

    data class TimePicker(
        override val key: String,
        override val label: String,
    ) : ConfigField()
}
