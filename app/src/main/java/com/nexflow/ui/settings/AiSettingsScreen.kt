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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexflow.BuildConfig
import com.nexflow.R
import com.nexflow.ai.CustomEndpoint
import com.nexflow.prefs.AiPrefs
import com.nexflow.prefs.AiProvider
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsRadioItem
import com.nexflow.ui.common.SettingsTopAppBar
import com.nexflow.ui.common.inContainerColors
import kotlinx.coroutines.delay

private const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"

/**
 * Everything the AI settings page shows, read in one go from [AiPrefs] — and re-read after
 * every edit, so the page never shows a value other than the one stored.
 */
internal data class AiSettingsState(
    val provider: AiProvider,
    val geminiKeySet: Boolean,
    val geminiModel: String,
    val customUrl: String?,
    val customKeySet: Boolean,
    val customModel: String?,
) {
    /** Whether the assistant can run with the selected service — same rule as AiPrefs.isConfigured. */
    val configured: Boolean
        get() = when (provider) {
            AiProvider.GEMINI -> geminiKeySet
            AiProvider.CUSTOM -> customUrl != null && customModel != null
        }

    companion object {
        fun read(context: Context) = AiSettingsState(
            provider = AiPrefs.getProvider(context),
            geminiKeySet = AiPrefs.getApiKey(context) != null,
            geminiModel = AiPrefs.getModel(context),
            customUrl = AiPrefs.getCustomBaseUrl(context),
            customKeySet = AiPrefs.getCustomApiKey(context) != null,
            customModel = AiPrefs.getCustomModel(context),
        )
    }
}

/** The fields edited one at a time, each in a dialog of its own. */
internal enum class AiField { GEMINI_KEY, CUSTOM_URL, CUSTOM_KEY, CUSTOM_MODEL }

/**
 * Which AI service the assistant uses, and that service's settings.
 *
 * Its own page because the two services need different things: Gemini a key and a model picked
 * from what the key can reach; an OpenAI-compatible service an address, an optional key and a
 * model name typed in. On the Settings page they were a Gemini-only key and model row plus a
 * dialog that crammed the choice and all three custom fields together.
 *
 * Choosing a service takes effect at once, as Android's own settings do. Each service keeps its
 * values when the other is chosen, so switching back costs nothing; only the chosen service's
 * settings are shown, so the page never asks about a service that is not in use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(AiSettingsState.read(context)) }
    var editing by remember { mutableStateOf<AiField?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    editing?.let { field ->
        AiFieldDialog(
            field = field,
            onDismiss = { editing = null },
            onSaved = {
                editing = null
                // Which models are available depends on the key; refetch next time.
                if (field == AiField.GEMINI_KEY) vm.resetModels()
                state = AiSettingsState.read(context)
            },
        )
    }

    if (showModelSheet) {
        GeminiModelSheet(
            vm = vm,
            apiKey = AiPrefs.getApiKey(context),
            selectedModel = state.geminiModel,
            onPicked = { model ->
                AiPrefs.setModel(context, model)
                state = AiSettingsState.read(context)
            },
            onDismiss = { showModelSheet = false },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = { SettingsTopAppBar(stringResource(R.string.settings_section_ai), scrollBehavior, onBack = onBack) },
    ) { innerPadding ->
        AiSettingsContent(
            state = state,
            // Debug only until the custom service has been proven against real servers;
            // release builds stay on Gemini, so there is nothing to choose.
            showProviderChoice = BuildConfig.DEBUG,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            onProviderSelected = { provider ->
                AiPrefs.setProvider(context, provider)
                state = AiSettingsState.read(context)
            },
            onEdit = { editing = it },
            onPickGeminiModel = { showModelSheet = true },
            onGetGeminiKey = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GEMINI_KEY_URL.toUri())) }
            },
        )
    }
}

/** The page's content, stateless so it can be previewed and screenshot-tested. */
@Composable
internal fun AiSettingsContent(
    state: AiSettingsState,
    showProviderChoice: Boolean,
    contentPadding: PaddingValues,
    onProviderSelected: (AiProvider) -> Unit,
    onEdit: (AiField) -> Unit,
    onPickGeminiModel: () -> Unit,
    onGetGeminiKey: () -> Unit,
) {
    val unset = stringResource(R.string.settings_ai_value_unset)
    LazyColumn(contentPadding = contentPadding) {
        if (showProviderChoice) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_ai_section_service),
                    modifier = Modifier.selectableGroup(),
                ) {
                    AiProvider.entries.forEach { provider ->
                        item { shapes ->
                            SettingsRadioItem(
                                shapes = shapes,
                                title = provider.label(),
                                selected = state.provider == provider,
                                onClick = { onProviderSelected(provider) },
                            )
                        }
                    }
                }
            }
        }

        when (state.provider) {
            AiProvider.GEMINI -> item {
                SettingsGroup(
                    title = AiProvider.GEMINI.label(),
                    footer = stringResource(R.string.settings_ai_api_key_desc),
                ) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_api_key),
                            summary = stringResource(
                                if (state.geminiKeySet) R.string.settings_ai_api_key_set
                                else R.string.settings_ai_api_key_unset,
                            ),
                            leading = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            trailing = missingMark(!state.geminiKeySet),
                            onClick = { onEdit(AiField.GEMINI_KEY) },
                        )
                    }
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_model),
                            summary = if (state.geminiKeySet) state.geminiModel
                            else stringResource(R.string.settings_ai_model_need_key),
                            leading = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                            enabled = state.geminiKeySet,
                            onClick = onPickGeminiModel,
                        )
                    }
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.ai_key_get),
                            summary = "aistudio.google.com",
                            leading = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                            onClick = onGetGeminiKey,
                        )
                    }
                }
            }

            AiProvider.CUSTOM -> item {
                SettingsGroup(
                    title = AiProvider.CUSTOM.label(),
                    footer = stringResource(R.string.settings_ai_custom_desc),
                ) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_custom_url),
                            summary = state.customUrl ?: unset,
                            leading = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            trailing = missingMark(state.customUrl == null),
                            onClick = { onEdit(AiField.CUSTOM_URL) },
                        )
                    }
                    item { shapes ->
                        // Optional, so never flagged: a local server takes no key.
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_custom_key),
                            summary = if (state.customKeySet) stringResource(R.string.settings_ai_api_key_set) else unset,
                            leading = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            onClick = { onEdit(AiField.CUSTOM_KEY) },
                        )
                    }
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = stringResource(R.string.settings_ai_custom_model),
                            summary = state.customModel ?: unset,
                            leading = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                            trailing = missingMark(state.customModel == null),
                            onClick = { onEdit(AiField.CUSTOM_MODEL) },
                        )
                    }
                }
            }
        }
    }
}

/** The warning at the end of a row the selected service cannot run without. */
private fun missingMark(missing: Boolean): (@Composable () -> Unit)? = if (missing) {
    {
        // The summary already says so; this is for the eye, not TalkBack.
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
} else {
    null
}

/** Edits one field and stores it; [onSaved] is called only once something valid was stored. */
@Composable
internal fun AiFieldDialog(field: AiField, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    when (field) {
        AiField.GEMINI_KEY -> SettingsTextFieldDialog(
            title = stringResource(R.string.settings_ai_api_key),
            initialValue = AiPrefs.getApiKey(context).orEmpty(),
            description = stringResource(R.string.settings_ai_api_key_desc),
            placeholder = stringResource(R.string.settings_ai_api_key_hint),
            secret = true,
            onDismiss = onDismiss,
            // Blank clears the key — the way to remove one.
            onSave = {
                AiPrefs.setApiKey(context, it)
                onSaved()
            },
        )

        AiField.CUSTOM_URL -> SettingsTextFieldDialog(
            title = stringResource(R.string.settings_ai_custom_url),
            initialValue = AiPrefs.getCustomBaseUrl(context).orEmpty(),
            placeholder = stringResource(R.string.settings_ai_custom_url_hint),
            // The rule is shown from the start rather than only after a wrong guess.
            rule = stringResource(R.string.settings_ai_custom_url_error),
            keyboardType = KeyboardType.Uri,
            isValid = { CustomEndpoint.normalizeBaseUrl(it) != null },
            onDismiss = onDismiss,
            onSave = {
                AiPrefs.setCustomBaseUrl(context, CustomEndpoint.normalizeBaseUrl(it)!!)
                onSaved()
            },
        )

        AiField.CUSTOM_KEY -> SettingsTextFieldDialog(
            title = stringResource(R.string.settings_ai_custom_key),
            initialValue = AiPrefs.getCustomApiKey(context).orEmpty(),
            secret = true,
            onDismiss = onDismiss,
            onSave = {
                AiPrefs.setCustomApiKey(context, it)
                onSaved()
            },
        )

        AiField.CUSTOM_MODEL -> SettingsTextFieldDialog(
            title = stringResource(R.string.settings_ai_custom_model),
            initialValue = AiPrefs.getCustomModel(context).orEmpty(),
            error = stringResource(R.string.settings_ai_custom_model_error),
            isValid = { it.isNotBlank() },
            onDismiss = onDismiss,
            onSave = {
                AiPrefs.setCustomModel(context, it)
                onSaved()
            },
        )
    }
}

/**
 * A dialog for one text setting: the title names the field, so the field itself needs no label.
 *
 * [rule] sits under the field from the start and turns red when [isValid] refuses what was
 * typed; [error] is for a field whose only rule is "not empty", which needs no advance notice.
 * [secret] hides the value behind a show/hide toggle.
 */
@Composable
internal fun SettingsTextFieldDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    description: String? = null,
    placeholder: String? = null,
    rule: String? = null,
    error: String? = rule,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isValid: (String) -> Boolean = { true },
) {
    // Opened with what is stored already selected: replacing a key or an address is usually one
    // paste, and a cursor parked in front of thirty masked dots helps nobody.
    var value by remember {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(0, initialValue.length)))
    }
    var invalid by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    // The dialog is opened to type in, so it should already be waiting for typing. The delay is
    // what makes it stick — the dialog's window has to be attached before it can take focus.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val supporting = if (invalid) error else rule
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        invalid = false
                    },
                    singleLine = true,
                    placeholder = placeholder?.let { { Text(it) } },
                    isError = invalid,
                    supportingText = supporting?.let { { Text(it) } },
                    visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = if (secret) {
                        {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = stringResource(
                                        if (visible) R.string.settings_ai_api_key_hide else R.string.settings_ai_api_key_show,
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret) KeyboardType.Password else keyboardType,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isValid(value.text)) onSave(value.text.trim()) else invalid = true
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The models the Gemini key can reach, fetched when the sheet opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiModelSheet(
    vm: AiSettingsViewModel,
    apiKey: String?,
    selectedModel: String,
    onPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val modelsState by vm.modelsState.collectAsState()
    LaunchedEffect(apiKey) { apiKey?.let { vm.loadModels(it) } }
    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            vm.resetModels()
        },
        scrimColor = Color.Transparent,
    ) {
        Text(
            text = stringResource(R.string.settings_ai_model),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        when (val state = modelsState) {
            AiSettingsViewModel.ModelsState.Idle,
            AiSettingsViewModel.ModelsState.Loading,
            -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is AiSettingsViewModel.ModelsState.Error -> Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_ai_model_load_failed) +
                        (state.message?.let { "\n$it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { apiKey?.let { vm.loadModels(it) } }) {
                    Text(stringResource(R.string.action_retry))
                }
            }

            is AiSettingsViewModel.ModelsState.Loaded -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .selectableGroup(),
            ) {
                items(state.models.size) { index ->
                    val model = state.models[index]
                    ListItem(
                        headlineContent = { Text(model.displayName ?: model.modelId) },
                        supportingContent = { Text(model.modelId) },
                        leadingContent = { RadioButton(selected = selectedModel == model.modelId, onClick = null) },
                        colors = ListItemDefaults.inContainerColors(),
                        modifier = Modifier.selectable(
                            selected = selectedModel == model.modelId,
                            role = Role.RadioButton,
                            onClick = {
                                onPicked(model.modelId)
                                onDismiss()
                                vm.resetModels()
                            },
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun AiProvider.label(): String = stringResource(
    when (this) {
        AiProvider.GEMINI -> R.string.settings_ai_provider_gemini
        AiProvider.CUSTOM -> R.string.settings_ai_provider_custom
    },
)
