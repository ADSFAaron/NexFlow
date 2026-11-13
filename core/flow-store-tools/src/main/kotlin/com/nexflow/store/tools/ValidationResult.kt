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
package com.nexflow.store.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `validate --json` writes. The store's report-validate workflow reads it to comment on the
 * pull request; that workflow treats every string in here as untrusted text, because a pull request
 * from a fork can change validate.yml and so write whatever it likes into this file.
 */
@Serializable
data class ValidationResult(
    val ok: Boolean,
    val flows: List<Flow>,
    /** Problems that belong to no single flow: a removed flow, a bad featured.json. */
    val problems: List<String> = emptyList(),
) {
    @Serializable
    data class Flow(
        val slug: String,
        val version: Int? = null,
        val ok: Boolean,
        val problems: List<String> = emptyList(),
        @SerialName("min_app_version") val minAppVersion: String? = null,
        val flavors: List<String> = emptyList(),
        val risks: List<String> = emptyList(),
        /** What the flow does with every action stubbed, one step per line. */
        @SerialName("dry_run") val dryRun: List<String> = emptyList(),
    )
}
