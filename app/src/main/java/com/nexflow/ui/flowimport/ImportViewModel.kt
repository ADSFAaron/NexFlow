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
import com.nexflow.core.flowschema.ImportItemKind
import com.nexflow.core.flowschema.ImportWarning
import com.nexflow.core.flowschema.ImportWarnings
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
    val warnings: List<ImportWarning> = emptyList(),
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

            if (root.macros.isEmpty()) {
                _result.update { ImportResult(error = "No macros found in this MacroDroid file") }
                return@launch
            }

            var imported = 0
            val allWarnings = mutableListOf<ImportWarning>()

            root.macros.forEach { macro ->
                val conversion = MdrToFlowConverter.convert(macro)
                // The converter already tagged each warning with the flow and item it belongs to,
                // and toDomain() keeps those ids, so the review UI can open the exact row.
                allWarnings += conversion.warnings
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

            fun warning(code: String, vararg args: String, itemKind: ImportItemKind? = null, itemId: String? = null) =
                ImportWarning(code, args.toList(), flowJson.id, flowJson.name, itemKind, itemId)

            val warnings = FlowSchemaValidator.validate(flowJson)
                .map { warning(ImportWarnings.SCHEMA_ERROR, it.field, it.message) }
                .toMutableList()
            warnings += reconcileGlobals(flowJson).map { (code, name) -> warning(code, name) }
            // Conditions are enforced now, so an unrecognised one is not a harmless leftover:
            // the engine refuses to run a flow whose constraint it cannot check.
            flowJson.conditions
                .filter { ConditionType.fromId(it.type) == null }
                .forEach {
                    warnings += warning(
                        ImportWarnings.UNKNOWN_CONDITION_TYPES, it.type,
                        itemKind = ImportItemKind.CONDITION, itemId = it.id,
                    )
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
    private suspend fun reconcileGlobals(flowJson: FlowJson): List<Pair<String, String>> {
        val warnings = mutableListOf<Pair<String, String>>()
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
            warnings += ImportWarnings.GLOBAL_CREATED to "${FlowInterpreter.GLOBAL_PREFIX}${g.name}"
        }

        val undeclared = referencedGlobalNames(flowJson) - existing
        undeclared.sorted().forEach {
            warnings += ImportWarnings.GLOBAL_UNDECLARED to "${FlowInterpreter.GLOBAL_PREFIX}$it"
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

    /**
     * Picks the reader by what the file actually is. A MacroDroid export is JSON too, so the
     * shape of the content decides — testing for a leading brace would send every .mdr to the
     * .flow reader, where it can only fail.
     */
    fun importAuto(content: String) {
        if (MdrParser.looksLikeMacroDroid(content)) importMdr(content) else importFlowJson(content)
    }

    fun clearResult() = _result.update { null }

    private companion object {
        // Both braces escaped explicitly — an unescaped `}}` trips some Android regex engines.
        val GLOBAL_REF_REGEX = Regex("""\{\{${FlowInterpreter.GLOBAL_PREFIX}([^}]+)\}\}""")
    }
}
