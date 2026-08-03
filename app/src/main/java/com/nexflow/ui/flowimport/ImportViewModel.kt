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
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.ConditionType
import com.nexflow.core.automation.model.GlobalVariable
import com.nexflow.core.automation.model.VariableType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.core.automation.repository.GlobalVariableRepository
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.core.flowschema.FlowSchemaValidator
import com.nexflow.core.flowschema.FlowSerializer
import com.nexflow.core.macrodroid.MdrToFlowConverter
import com.nexflow.core.macrodroid.parser.MdrParser
import com.nexflow.data.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportResult(
    val imported: Int = 0,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val globalVariableRepository: GlobalVariableRepository,
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
            warnings += reconcileGlobals(flowJson)
            // Conditions are enforced now, so an unrecognised one is not a harmless leftover:
            // the engine refuses to run a flow whose constraint it cannot check.
            val unknownConditions = flowJson.conditions
                .map { it.type }
                .filter { ConditionType.fromId(it) == null }
                .distinct()
            if (unknownConditions.isNotEmpty()) {
                warnings += "Conditions: unsupported type(s) ${unknownConditions.joinToString()} — " +
                    "this flow will not run until you remove them in the editor"
            }

            repository.save(flowJson.toDomain())
            _result.update { ImportResult(imported = 1, warnings = warnings) }
        }
    }

    /**
     * Creates the globals an imported flow declares but this device doesn't have yet, and reports
     * any `g:` name the flow uses without declaring — those would otherwise fail at run time.
     * An existing global is never overwritten: another flow may already be using its value.
     */
    private suspend fun reconcileGlobals(flowJson: FlowJson): List<String> {
        val warnings = mutableListOf<String>()
        val existing = globalVariableRepository.currentValues().keys.toMutableSet()

        flowJson.globalVariables.forEach { g ->
            if (g.name in existing) return@forEach
            globalVariableRepository.save(
                GlobalVariable(
                    name = g.name,
                    type = runCatching { VariableType.valueOf(g.type) }.getOrDefault(VariableType.STRING),
                    defaultValue = g.defaultValue,
                    // Live values are device state and don't travel: start at the default.
                    currentValue = g.defaultValue,
                ),
            )
            existing += g.name
            warnings += "Global variable '${FlowInterpreter.GLOBAL_PREFIX}${g.name}' created from the imported flow"
        }

        val undeclared = referencedGlobalNames(flowJson) - existing
        undeclared.sorted().forEach {
            warnings += "Global variable '${FlowInterpreter.GLOBAL_PREFIX}$it' is used but not declared — " +
                "create it in Settings → Global Variables, or the flow will fail when it writes to it"
        }
        return warnings
    }

    /** Every `{{g:x}}` reference and `g:x` SET_VARIABLE target in the file, un-prefixed. */
    private fun referencedGlobalNames(flowJson: FlowJson): Set<String> {
        val text = (flowJson.actions.map { it.config.toString() } + flowJson.triggers.map { it.config.toString() })
            .joinToString("\n")
        val names = GLOBAL_REF_REGEX.findAll(text).map { it.groupValues[1].trim() }.toMutableSet()
        flowJson.actions.filter { it.type == "SET_VARIABLE" }.forEach { a ->
            val target = (a.config["variable_name"] ?: a.config["name"])
                ?.toString()?.trim('"')?.trim().orEmpty()
            if (target.startsWith(FlowInterpreter.GLOBAL_PREFIX)) {
                names += target.removePrefix(FlowInterpreter.GLOBAL_PREFIX)
            }
        }
        return names
    }

    fun importAuto(content: String) {
        if (content.trimStart().startsWith("{")) {
            importFlowJson(content)
        } else {
            importMdr(content)
        }
    }

    fun clearResult() = _result.update { null }

    private companion object {
        // Both braces escaped explicitly — an unescaped `}}` trips some Android regex engines.
        val GLOBAL_REF_REGEX = Regex("""\{\{${FlowInterpreter.GLOBAL_PREFIX}([^}]+)\}\}""")
    }
}
