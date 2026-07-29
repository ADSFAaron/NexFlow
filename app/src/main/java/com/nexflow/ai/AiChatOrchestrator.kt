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

import android.content.Context
import android.util.Log
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.prefs.AiPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Multi-turn function-calling loop for one chat session. Unscoped — each ViewModel owns its
 * own conversation history.
 *
 * Per user turn: call generateContent; answer `search_installed_apps` locally and continue;
 * validate `create_flow` via [FlowDraftMapper], feeding errors back as a functionResponse so
 * Gemini can repair (bounded), and surface the validated flow with the model's summary text.
 */
class AiChatOrchestrator @Inject constructor(
    private val client: GeminiClient,
    private val installedApps: InstalledAppsSource,
    private val globalVariableRepository: GlobalVariableRepository,
    @param:ApplicationContext private val context: Context,
) {
    sealed interface TurnResult {
        /** Plain assistant reply (question, confirmation, etc.). */
        data class Assistant(val text: String) : TurnResult

        /** A flow passed validation; [summaryText] is the model's follow-up message. */
        data class FlowReady(val flow: FlowJson, val summaryText: String) : TurnResult

        data class Failure(val error: GeminiException) : TurnResult
    }

    /** What the loop is doing right now — surfaced under the thinking indicator. */
    sealed interface Progress {
        data object Contacting : Progress
        data object SearchingApps : Progress
        data object BuildingFlow : Progress
        data class Repairing(val attempt: Int) : Progress
    }

    private val history = mutableListOf<GeminiContent>()

    fun reset() = history.clear()

    suspend fun sendUserMessage(
        text: String,
        onProgress: (Progress) -> Unit = {},
    ): TurnResult {
        val apiKey = AiPrefs.getApiKey(context)
            ?: return TurnResult.Failure(GeminiException.InvalidKey("API key not set"))
        val model = AiPrefs.getModel(context)

        history += GeminiContent(role = "user", parts = listOf(GeminiPart(text = text)))

        var repairAttempts = 0
        var pendingFlow: FlowJson? = null

        repeat(MAX_STEPS) { step ->
            if (step == 0) onProgress(Progress.Contacting)
            val request = GenerateContentRequest(
                contents = history.toList(),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = AiCatalog.systemInstruction(context))),
                ),
                tools = AiTools.tools(),
            )
            val response = client.generateContent(apiKey, model, request).getOrElse { e ->
                Log.e(TAG, "turn failed: ${e.javaClass.simpleName}: ${e.message}")
                return TurnResult.Failure(
                    e as? GeminiException ?: GeminiException.Unknown(e.message ?: "unexpected error"),
                )
            }

            val parts = response.parts
            if (parts.isEmpty()) {
                Log.e(TAG, "empty response, blockReason=${response.promptFeedback?.blockReason}")
                return TurnResult.Failure(
                    GeminiException.Unknown(
                        response.promptFeedback?.blockReason?.let { "blocked: $it" } ?: "empty response",
                    ),
                )
            }
            history += GeminiContent(role = "model", parts = parts)

            val functionCalls = parts.mapNotNull { it.functionCall }
            Log.d(
                TAG,
                "model turn: ${functionCalls.size} function call(s) ${functionCalls.map { it.name }}, " +
                    "text parts=${parts.count { it.text != null }}",
            )
            if (functionCalls.isEmpty()) {
                // thought=true parts are reasoning summaries, not user-facing output
                val reply = parts.filter { it.thought != true }
                    .mapNotNull { it.text }.joinToString("\n").trim()
                val flow = pendingFlow
                return if (flow != null) TurnResult.FlowReady(flow, reply) else TurnResult.Assistant(reply)
            }

            val responseParts = functionCalls.map { call ->
                when (call.name) {
                    AiTools.SEARCH_INSTALLED_APPS -> {
                        onProgress(Progress.SearchingApps)
                        searchAppsResponse(call)
                    }

                    AiTools.CREATE_FLOW -> {
                        onProgress(Progress.BuildingFlow)
                        val draft = FlowDraftMapper.fromArgs(
                            call.args,
                            context,
                            knownGlobals = globalVariableRepository.currentValues().keys,
                        )
                        if (draft.isValid) {
                            Log.d(TAG, "create_flow validated: \"${draft.flow?.name}\"")
                            pendingFlow = draft.flow
                            functionResponse(call.name, buildJsonObject { put("status", "ok") })
                        } else {
                            repairAttempts++
                            onProgress(Progress.Repairing(repairAttempts))
                            Log.w(
                                TAG,
                                "create_flow rejected (attempt $repairAttempts/$MAX_REPAIR_ATTEMPTS): ${draft.errors}",
                            )
                            if (repairAttempts > MAX_REPAIR_ATTEMPTS) {
                                return TurnResult.Failure(
                                    GeminiException.Unknown(
                                        "flow validation failed after $MAX_REPAIR_ATTEMPTS attempts: " +
                                            draft.errors.joinToString("; "),
                                    ),
                                )
                            }
                            functionResponse(
                                call.name,
                                buildJsonObject {
                                    put("status", "error")
                                    putJsonArray("errors") { draft.errors.forEach { add(it) } }
                                },
                            )
                        }
                    }

                    else -> functionResponse(
                        call.name,
                        buildJsonObject {
                            put("status", "error")
                            put("message", "unknown function ${call.name}")
                        },
                    )
                }
            }
            history += GeminiContent(role = "user", parts = responseParts)
        }

        return TurnResult.Failure(GeminiException.Unknown("conversation step limit reached"))
    }

    private fun searchAppsResponse(call: GeminiFunctionCall): GeminiPart {
        val query = (call.args["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val results = installedApps.search(query)
        Log.d(TAG, "search_installed_apps(\"$query\") → ${results.size} result(s)")
        return functionResponse(
            call.name,
            buildJsonObject {
                putJsonArray("apps") {
                    results.forEach { app ->
                        add(
                            buildJsonObject {
                                put("label", app.label)
                                put("package_name", app.packageName)
                            },
                        )
                    }
                }
                if (results.isEmpty()) put("note", "no installed app matched \"$query\"")
            },
        )
    }

    private fun functionResponse(name: String, response: kotlinx.serialization.json.JsonObject) =
        GeminiPart(functionResponse = GeminiFunctionResponse(name = name, response = response))

    private companion object {
        const val TAG = "AiChatOrchestrator"

        /** Upper bound on model round-trips per user turn (tool answers + repairs). */
        const val MAX_STEPS = 8
        const val MAX_REPAIR_ATTEMPTS = 3
    }
}
