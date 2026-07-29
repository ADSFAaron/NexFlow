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
package com.nexflow.ui.globalvars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.R
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.GlobalVariable
import com.nexflow.core.automation.model.VariableType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalVariablesScreen(
    onBack: () -> Unit,
    vm: GlobalVariablesViewModel = hiltViewModel(),
) {
    val variables by vm.variables.collectAsState()
    // null = no dialog; a GlobalVariable with a blank name = "add new".
    var editing by remember { mutableStateOf<GlobalVariable?>(null) }
    val existingNames = remember(variables) { variables.map { it.name } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.gv_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = GlobalVariable("", VariableType.STRING, "", "") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.gv_add)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { IntroCard() }

            if (variables.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.gv_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }

            items(variables, key = { it.name }) { variable ->
                ListItem(
                    headlineContent = { Text("{{${FlowInterpreter.GLOBAL_PREFIX}${variable.name}}}", fontFamily = FontFamily.Monospace) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.gv_row_values,
                                variable.currentValue.ifBlank { "—" },
                                variable.defaultValue.ifBlank { "—" },
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { vm.delete(variable.name) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                    modifier = Modifier.clickable { editing = variable },
                )
            }
        }
    }

    editing?.let { variable ->
        GlobalVariableDialog(
            variable = variable,
            existingNames = existingNames,
            onConfirm = { updated ->
                vm.save(originalName = variable.name.ifBlank { null }, variable = updated)
                editing = null
            },
            onReset = { vm.resetToDefault(variable) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun IntroCard() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(
            stringResource(R.string.gv_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalVariableDialog(
    variable: GlobalVariable,
    existingNames: List<String>,
    onConfirm: (GlobalVariable) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = variable.name.isBlank()
    var name by remember { mutableStateOf(variable.name) }
    var type by remember { mutableStateOf(variable.type) }
    var defaultValue by remember { mutableStateOf(variable.defaultValue) }

    val trimmedName = name.trim()
    val nameTaken = trimmedName != variable.name && trimmedName in existingNames
    // Global names live in a {{g:name}} token, so keep them token-safe (no braces / colons / spaces).
    val nameValid = trimmedName.isNotEmpty() && trimmedName.matches(Regex("[A-Za-z0-9_]+"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.gv_new) else stringResource(R.string.gv_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    isError = trimmedName.isNotEmpty() && (!nameValid || nameTaken),
                    supportingText = {
                        Text(
                            when {
                                nameTaken -> stringResource(R.string.fd_var_name_taken)
                                trimmedName.isNotEmpty() && !nameValid -> stringResource(R.string.gv_name_invalid)
                                else -> stringResource(
                                    R.string.gv_name_hint,
                                    "${FlowInterpreter.GLOBAL_PREFIX}${trimmedName.ifBlank { "name" }}",
                                )
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TypeDropdown(selected = type, onSelected = { type = it })
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text(stringResource(R.string.fd_default_value)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = onReset,
                            label = { Text(stringResource(R.string.gv_reset_to_default)) },
                        )
                        Text(
                            stringResource(R.string.gv_current_value, variable.currentValue.ifBlank { "—" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        GlobalVariable(
                            name = trimmedName,
                            type = type,
                            defaultValue = defaultValue,
                            // New vars start at their default; edits keep the running value.
                            currentValue = if (isNew) defaultValue else variable.currentValue,
                        ),
                    )
                },
                enabled = nameValid && !nameTaken,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(
    selected: VariableType,
    onSelected: (VariableType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.gv_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            VariableType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
