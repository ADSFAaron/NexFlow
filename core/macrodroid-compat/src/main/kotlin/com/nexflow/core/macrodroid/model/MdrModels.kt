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
package com.nexflow.core.macrodroid.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Partial representation of the MacroDroid export format (community-reversed, not official).
 *
 * Verified against a real backup — see docs/MACRODROID_IMPORT.md for the source files. Two
 * shapes exist and both land here:
 *
 * - `.mdr` (full backup): `{ "macroList": [ <macro>, … ], … }`
 * - `.macro` (single macro shared from the app): `{ "macro": <macro>, "macroExportVersion": 1 }`
 *
 * Inside a macro, a trigger/action/constraint is a flat JSON object: its class is `m_classType`
 * and its settings sit as sibling keys (`m_hour`, `m_minute`, …) — there is no nested `options`
 * object. [MdrItem] splits that back apart.
 */
@Serializable
data class MdrRoot(
    @SerialName("macroList") val macroList: List<MdrMacro> = emptyList(),
    @SerialName("macro") val singleMacro: MdrMacro? = null,
) {
    /** Every macro in the file, whichever of the two export shapes it used. */
    val macros: List<MdrMacro> get() = macroList.ifEmpty { listOfNotNull(singleMacro) }
}

@Serializable
data class MdrMacro(
    /** MacroDroid GUIDs are signed 64-bit numbers, not UUID strings. */
    @SerialName("m_GUID") val guid: Long = 0L,
    @SerialName("m_name") val name: String = "",
    @SerialName("m_description") val description: String = "",
    @SerialName("m_enabled") val enabled: Boolean = true,
    @SerialName("m_category") val category: String = "",
    @SerialName("m_triggerList") val triggerList: List<MdrItem> = emptyList(),
    @SerialName("m_actionList") val actionList: List<MdrItem> = emptyList(),
    @SerialName("m_constraintList") val constraintList: List<MdrItem> = emptyList(),
)

/**
 * One trigger, action or constraint. The three are structurally identical in the file — they
 * differ only by which list they appear in — so one type covers all of them.
 *
 * [options] is the raw object minus the bookkeeping keys every item carries, i.e. exactly the
 * settings the user configured.
 */
@Serializable(with = MdrItemSerializer::class)
data class MdrItem(
    val classType: String,
    val guid: String = "",
    val disabled: Boolean = false,
    val options: JsonObject = JsonObject(emptyMap()),
)

/**
 * Reads an item without knowing its class: the field set differs per class type, so the object
 * is taken as-is and the settings are whatever is left after the shared keys are removed.
 */
internal object MdrItemSerializer : KSerializer<MdrItem> {

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): MdrItem {
        val raw = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return MdrItem(
            classType = raw["m_classType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            guid = raw["m_SIGUID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            disabled = raw["m_isDisabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            options = JsonObject(raw - STRUCTURAL_KEYS),
        )
    }

    override fun serialize(encoder: Encoder, value: MdrItem): Nothing =
        throw UnsupportedOperationException("NexFlow only reads MacroDroid files, it never writes them")

    /**
     * Present on every item regardless of class: identity, the per-item constraint list (NexFlow
     * has no equivalent — its conditions are per-flow), and editor cosmetics.
     */
    private val STRUCTURAL_KEYS = setOf(
        "m_classType",
        "m_SIGUID",
        "m_isDisabled",
        "m_isOrCondition",
        "m_constraintList",
        "m_comment",
        "fakeIcon",
    )
}
