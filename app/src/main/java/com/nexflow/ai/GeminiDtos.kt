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
package com.nexflow.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire types for the Gemini REST API (v1beta `models.generateContent` / `models.list`).
 * Field names match the API's camelCase JSON exactly.
 */

@Serializable
data class GenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>? = null,
    val toolConfig: GeminiToolConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    /** True when this part is model reasoning (thought summary), not user-facing output. */
    val thought: Boolean? = null,
    /**
     * Opaque reasoning signature. Gemini 3 REJECTS (HTTP 400) any history whose functionCall
     * parts are missing it, so it must survive the response→history→request round trip.
     */
    val thoughtSignature: String? = null,
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject,
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>,
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null,
)

@Serializable
data class GeminiToolConfig(
    val functionCallingConfig: GeminiFunctionCallingConfig,
)

@Serializable
data class GeminiFunctionCallingConfig(
    val mode: String,
    val allowedFunctionNames: List<String>? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
) {
    /** Parts of the first candidate, or empty when the prompt was blocked. */
    val parts: List<GeminiPart>
        get() = candidates.firstOrNull()?.content?.parts.orEmpty()
}

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null,
)

@Serializable
data class GeminiErrorBody(
    val error: GeminiErrorDetail? = null,
)

@Serializable
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
    /** google.rpc.ErrorInfo entries; `reason` distinguishes e.g. API_KEY_INVALID from other 400s. */
    val details: List<JsonObject> = emptyList(),
)

@Serializable
data class ListModelsResponse(
    val models: List<GeminiModelInfo> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class GeminiModelInfo(
    /** Resource name, e.g. "models/gemini-3.5-flash". */
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
) {
    /** Bare model id usable in the generateContent path, e.g. "gemini-3.5-flash". */
    val modelId: String get() = name.removePrefix("models/")
}
