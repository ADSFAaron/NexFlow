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
package com.nexflow.core.flowschema

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What every trigger, condition and action accepts, as checked-in data (`docs/flow-catalog.json`).
 *
 * The app's editors are the source of truth, but they need an Android Context; the flow store CLI
 * runs on a plain JVM in another repo's CI. A test in the app regenerates this file from the
 * editors and fails when it is out of date, so the two can never quietly disagree.
 *
 * `since` is the first app version that knows a step or key; the CLI derives a flow's
 * `min_app_version` from it.
 */
@Serializable
data class FlowCatalog(
    // Always written: the CLI refuses a catalog newer than it understands.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("catalog_version")
    val catalogVersion: Int = CURRENT_VERSION,
    val triggers: Map<String, StepEntry>,
    val conditions: Map<String, StepEntry>,
    val actions: Map<String, StepEntry>,
) {
    @Serializable
    data class StepEntry(
        val since: String,
        /** Flavors that can run it: `github`, `play`. */
        val flavors: List<String>,
        val keys: Map<String, KeyEntry>,
    )

    @Serializable
    data class KeyEntry(
        val since: String,
        /** The editor control that writes it, e.g. `Dropdown`, `Slider`, `AppPicker`. */
        val input: String,
        /** The only values allowed, for dropdowns, toggles and unit pickers. */
        val values: List<String>? = null,
        val min: Int? = null,
        val max: Int? = null,
        /** For a value whose range depends on a unit: unit id to `[min, max]`. */
        @SerialName("unit_ranges") val unitRanges: Map<String, List<Int>>? = null,
        val personal: Boolean = false,
        val sink: Boolean = false,
    )

    fun encode(): String = JSON.encodeToString(serializer(), this) + "\n"

    companion object {
        const val CURRENT_VERSION = 1

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val JSON = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = false
            explicitNulls = false
        }

        fun decode(text: String): FlowCatalog = JSON.decodeFromString(serializer(), text)
    }
}
