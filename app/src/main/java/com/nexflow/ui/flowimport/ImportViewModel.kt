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
package com.nexflow.ui.flowimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.Flow as AutomationFlow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSchemaValidator
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.macrodroid.MdrToFlowConverter
import com.nexflow.core.macrodroid.parser.MdrParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class ImportResult(
    val imported: Int = 0,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: FlowRepository,
) : ViewModel() {

    private val _result = MutableStateFlow<ImportResult?>(null)
    val result: StateFlow<ImportResult?> = _result.asStateFlow()

    fun importMdr(content: String) {
        viewModelScope.launch {
            val root = MdrParser.parse(content).getOrElse { e ->
                _result.update { ImportResult(error = "Parse failed: ${e.message}") }
                return@launch
            }

            var imported = 0
            val allWarnings = mutableListOf<String>()

            root.macroList.forEach { macro ->
                val conversion = MdrToFlowConverter.convert(macro)
                allWarnings += conversion.warnings.map { "[${macro.name}] $it" }
                repository.save(conversion.flow.toDomain())
                imported++
            }

            _result.update { ImportResult(imported = imported, warnings = allWarnings) }
        }
    }

    fun importFlowJson(content: String) {
        viewModelScope.launch {
            val flowJson = FlowSerializer.decode(content).getOrElse { e ->
                _result.update { ImportResult(error = "Parse failed: ${e.message}") }
                return@launch
            }

            val errors = FlowSchemaValidator.validate(flowJson)
            val warnings = errors.map { "${it.field}: ${it.message}" }.toMutableList()

            repository.save(flowJson.toDomain())
            _result.update { ImportResult(imported = 1, warnings = warnings) }
        }
    }

    fun importAuto(content: String) {
        if (content.trimStart().startsWith("{")) {
            importFlowJson(content)
        } else {
            importMdr(content)
        }
    }

    fun clearResult() = _result.update { null }
}

// ---------------------------------------------------------------------------
// FlowJson → domain Flow mapper (used only for import)
// ---------------------------------------------------------------------------

private fun FlowJson.toDomain(): AutomationFlow {
    val now = System.currentTimeMillis()
    fun String.toEpochMs() = runCatching {
        java.time.Instant.parse(this).toEpochMilli()
    }.getOrDefault(now)

    return AutomationFlow(
        id = id,
        schemaVersion = schemaVersion,
        name = name,
        description = description,
        author = author,
        tags = tags,
        enabled = enabled,
        createdAt = createdAt.toEpochMs(),
        updatedAt = now,
        triggers = triggers.map { tj ->
            Trigger(
                id = tj.id,
                type = runCatching { TriggerType.valueOf(tj.type) }.getOrDefault(TriggerType.MANUAL),
                config = tj.config.entries.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: v.toString())
                },
            )
        },
        triggerLogic = runCatching { TriggerLogic.valueOf(triggerLogic) }.getOrDefault(TriggerLogic.ANY),
        conditions = conditions.map { cj ->
            Condition(
                id = cj.id,
                type = cj.type,
                config = cj.config.entries.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: v.toString())
                },
                negate = cj.negate,
            )
        },
        actions = actions.map { aj ->
            Action(
                id = aj.id,
                type = runCatching { ActionType.valueOf(aj.type) }.getOrDefault(ActionType.TOAST),
                config = aj.config.entries.associate { (k, v) ->
                    k to (v.jsonPrimitive.contentOrNull ?: v.toString())
                },
                order = aj.order,
                enabled = aj.enabled,
            )
        },
        variables = emptyList(),
    )
}
