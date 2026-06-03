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

import kotlinx.serialization.json.Json

val FlowJson_Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    coerceInputValues = true
}

object FlowSerializer {
    fun decode(json: String): Result<FlowJson> = runCatching {
        FlowJson_Json.decodeFromString<FlowJson>(json)
    }

    fun encode(flow: FlowJson): String =
        FlowJson_Json.encodeToString(FlowJson.serializer(), flow)
}
