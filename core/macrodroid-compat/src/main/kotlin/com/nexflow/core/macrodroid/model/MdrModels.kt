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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Partial representation of the MacroDroid .mdr format (community-reversed, not official).
 * Fields not recognised by the current parser are silently ignored.
 */
@Serializable
data class MdrRoot(
    @SerialName("macro_list") val macroList: List<MdrMacro> = emptyList(),
)

@Serializable
data class MdrMacro(
    val guid: String = "",
    val name: String = "",
    val description: String = "",
    @SerialName("trigger_list") val triggerList: List<MdrTrigger> = emptyList(),
    @SerialName("action_list") val actionList: List<MdrAction> = emptyList(),
    @SerialName("constraint_list") val constraintList: List<MdrConstraint> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class MdrTrigger(
    @SerialName("class_type") val classType: String = "",
    @SerialName("m_GUID") val guid: String = "",
    val options: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class MdrAction(
    @SerialName("class_type") val classType: String = "",
    @SerialName("m_GUID") val guid: String = "",
    val options: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class MdrConstraint(
    @SerialName("class_type") val classType: String = "",
    @SerialName("m_GUID") val guid: String = "",
    val negate: Boolean = false,
    val options: JsonObject = JsonObject(emptyMap()),
)
