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
package com.nexflow.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexflow.R
import com.nexflow.ai.CustomEndpoint
import com.nexflow.prefs.AiPrefs
import com.nexflow.prefs.AiProvider

/** Chooses which AI service the assistant talks to, and sets up the custom one. */
@Composable
internal fun AiProviderDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { AiPrefs.getCustomEndpoint(context) }
    var provider by remember { mutableStateOf(AiPrefs.getProvider(context)) }
    var baseUrl by remember { mutableStateOf(saved?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(saved?.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(saved?.model.orEmpty()) }
    var keyVisible by remember { mutableStateOf(false) }
    var urlInvalid by remember { mutableStateOf(false) }
    var modelMissing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_provider)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AiProvider.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label()) },
                        leadingContent = { RadioButton(selected = provider == option, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = provider == option,
                            role = Role.RadioButton,
                            onClick = { provider = option },
                        ),
                    )
                }
                if (provider == AiProvider.CUSTOM) {
                    Text(
                        text = stringResource(R.string.settings_ai_custom_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            urlInvalid = false
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_custom_url)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_custom_url_hint)) },
                        isError = urlInvalid,
                        supportingText = if (urlInvalid) {
                            { Text(stringResource(R.string.settings_ai_custom_url_error)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_custom_key)) },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (keyVisible) R.string.settings_ai_api_key_hide else R.string.settings_ai_api_key_show,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            modelMissing = false
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_ai_custom_model)) },
                        isError = modelMissing,
                        supportingText = if (modelMissing) {
                            { Text(stringResource(R.string.settings_ai_custom_model_error)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (provider == AiProvider.CUSTOM) {
                    val url = CustomEndpoint.normalizeBaseUrl(baseUrl)
                    urlInvalid = url == null
                    modelMissing = model.isBlank()
                    if (url == null || model.isBlank()) return@TextButton
                    AiPrefs.setCustomEndpoint(
                        context,
                        CustomEndpoint(url, apiKey.trim().ifEmpty { null }, model.trim()),
                    )
                }
                AiPrefs.setProvider(context, provider)
                onSaved()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun AiProvider.label(): String = stringResource(
    when (this) {
        AiProvider.GEMINI -> R.string.settings_ai_provider_gemini
        AiProvider.CUSTOM -> R.string.settings_ai_provider_custom
    },
)
