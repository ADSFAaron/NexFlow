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

/*
 * The flow store's files (docs/flow-store/DESIGN.md, "商店的檔案格式"). Shared by the store CLI,
 * which writes and checks them, and the app, which reads them.
 */

/** `flows/<slug>/meta.json`: what a person writes next to a flow. */
@Serializable
data class StoreMeta(
    val slug: String,
    val version: Int,
    val languages: Map<String, Text>,
    val tags: List<String> = emptyList(),
    val setup: List<SetupField> = emptyList(),
) {
    @Serializable
    data class Text(val title: String, val description: String = "")
}

/** One value the user fills in at install; it replaces the whole of one config field. */
@Serializable
data class SetupField(
    val id: String,
    val target: Target,
    /** Language code to label. */
    val label: Map<String, String>,
    val secret: Boolean = false,
) {
    @Serializable
    data class Target(
        /** `trigger`, `condition` or `action`. */
        val kind: String,
        /** The step's id in flow.json. */
        val item: String,
        val key: String,
    )
}

/** `revoked.json`. A revoked slug can never be used again. */
@Serializable
data class StoreRevoked(val revoked: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(
        val slug: String,
        /** `malicious` (the app disables the flow) or `broken` (it only tells the user). */
        val reason: String,
        val date: String,
        val note: String = "",
    )
}

/** `index.json`: what the app downloads first, signed at publish. */
@Serializable
data class StoreIndex(
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("index_version")
    val indexVersion: Int = CURRENT_VERSION,
    @SerialName("generated_at") val generatedAt: String,
    val flows: List<Entry>,
    val revoked: List<StoreRevoked.Entry> = emptyList(),
) {
    @Serializable
    data class Entry(
        val slug: String,
        val version: Int,
        val author: String,
        val languages: Map<String, StoreMeta.Text>,
        val tags: List<String>,
        @SerialName("min_app_version") val minAppVersion: String,
        val flavors: List<String>,
        val risks: List<String>,
        @SerialName("setup_count") val setupCount: Int,
        /** Path of the [StoreBundle] file, relative to the index. */
        val bundle: String,
        val sha256: String,
    )

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** One download per flow: its meta and flow together, so one sha256 covers both. */
@Serializable
data class StoreBundle(val meta: StoreMeta, val flow: FlowJson)

/** The flow store's JSON dialect: stable, readable output, and tolerant input. */
object StoreJson {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Store languages, in the order the app falls back through them. */
    val LANGUAGES = listOf("en", "zh-TW", "zh-CN", "ja")
}
