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
package com.nexflow.ui.ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexflow.R
import com.nexflow.ai.AiChatOrchestrator
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.ui.common.GeminiGradientLoop
import com.nexflow.ui.common.geminiGradientTint
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit = {},
    onFlowSaved: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    vm: AiChatViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    // Draft lives in the app-scoped session so it survives leaving and re-entering the screen.
    // The screen wraps it in a TextFieldValue so programmatic updates (voice dictation, the
    // long-press Edit action) place the cursor at the end instead of leaving it stranded.
    var fieldValue by remember { mutableStateOf(TextFieldValue(vm.draft.value)) }
    LaunchedEffect(vm) {
        vm.draft.collect { text ->
            if (text != fieldValue.text) {
                fieldValue = TextFieldValue(text, selection = TextRange(text.length))
            }
        }
    }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val voice = remember { VoiceInputController(context) }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    // Text typed before the mic was tapped; dictation appends to it.
    var voiceBaseline by remember { mutableStateOf("") }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceBaseline = fieldValue.text
            voice.start()
        }
    }

    val voiceErrorText = stringResource(R.string.ai_voice_error)
    val voiceUnavailableText = stringResource(R.string.ai_voice_unavailable)
    LaunchedEffect(voice.state) {
        when (val state = voice.state) {
            is VoiceInputController.VoiceState.Listening -> {
                if (state.partialText.isNotBlank()) {
                    vm.updateDraft((voiceBaseline + " " + state.partialText).trim())
                }
            }
            is VoiceInputController.VoiceState.Result -> {
                vm.updateDraft((voiceBaseline + " " + state.text).trim())
                voice.consumeState()
            }
            VoiceInputController.VoiceState.Error -> {
                snackbarHostState.showSnackbar(voiceErrorText)
                voice.consumeState()
            }
            VoiceInputController.VoiceState.Unavailable -> {
                snackbarHostState.showSnackbar(voiceUnavailableText)
                voice.consumeState()
            }
            VoiceInputController.VoiceState.Idle -> Unit
        }
    }

    LaunchedEffect(vm) {
        vm.navigateToFlow.collect { flowId -> onFlowSaved(flowId) }
    }

    // The user may hop to Settings to paste the API key and come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshApiKeyState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val itemCount = uiState.messages.size + if (uiState.isThinking) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.newChat() },
                        enabled = uiState.messages.isNotEmpty() && !uiState.isThinking,
                    ) {
                        Icon(
                            Icons.Outlined.AddComment,
                            contentDescription = stringResource(R.string.ai_new_chat),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Soft ambient wash at the top — the Gemini design language's gradient "glow"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
            when {
                uiState.apiKeyMissing -> ApiKeyMissingContent(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )

                uiState.messages.isEmpty() -> GreetingContent(modifier = Modifier.weight(1f))

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        val itemModifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        )
                        when (message) {
                            is ChatMessage.UserText -> MessageBubble(
                                text = message.text,
                                timestamp = message.timestamp,
                                fromUser = true,
                                // Long-press → Edit reloads the sent text into the input field
                                onEdit = { vm.updateDraft(message.text) },
                                modifier = itemModifier,
                            )

                            is ChatMessage.AssistantText -> MessageBubble(
                                text = message.text,
                                timestamp = message.timestamp,
                                fromUser = false,
                                modifier = itemModifier,
                            )

                            is ChatMessage.Error -> ErrorBubble(
                                text = message.text,
                                modifier = itemModifier,
                            )

                            is ChatMessage.FlowPreview -> FlowPreviewCard(
                                flow = message.flow,
                                saved = message.saved,
                                isUpdate = uiState.hasSavedFlow,
                                onSave = { vm.saveFlow(message) },
                                modifier = itemModifier,
                            )
                        }
                    }
                    if (uiState.isThinking) {
                        item(key = "thinking") { ThinkingBubble(step = uiState.thinkingStep) }
                    }
                }
            }

            if (!uiState.apiKeyMissing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Expressive pill-shaped input, matching the M3 conversation pattern.
                    // While dictating, a sweeping Gemini-gradient border + shimmering
                    // placeholder signal "listening" (per the Gemini visual design language).
                    TextField(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            vm.updateDraft(it.text)
                        },
                        placeholder = {
                            if (voice.isListening) {
                                Text(stringResource(R.string.ai_listening), style = voiceShimmerTextStyle())
                            } else {
                                Text(stringResource(R.string.ai_input_hint))
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (voice.isListening) Modifier.voiceListeningGlow() else Modifier),
                        maxLines = 4,
                    )
                    FilledTonalIconButton(
                        onClick = {
                            if (voice.isListening) {
                                voice.stop()
                            } else if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                voiceBaseline = fieldValue.text
                                voice.start()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !uiState.isThinking,
                        // TextField is 56dp with centered text; the 48dp button box needs 4dp
                        // to line its visual center up with the single-line text baseline row
                        modifier = Modifier.padding(bottom = 4.dp),
                        colors = if (voice.isListening) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        },
                    ) {
                        Icon(
                            if (voice.isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = stringResource(
                                if (voice.isListening) R.string.ai_mic_stop_content_desc
                                else R.string.ai_mic_content_desc,
                            ),
                        )
                    }
                    FilledIconButton(
                        onClick = { vm.sendMessage(fieldValue.text) },
                        enabled = fieldValue.text.isNotBlank() && !uiState.isThinking,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.ai_send),
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Sweeping Gemini-gradient border around the input pill while dictation is active —
 * the design language's directional "energy in motion" listening signal.
 */
@Composable
private fun Modifier.voiceListeningGlow(cornerRadius: Dp = 28.dp): Modifier {
    val transition = rememberInfiniteTransition(label = "voice-glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
        ),
        label = "voice-glow-phase",
    )
    return drawWithContent {
        drawContent()
        val strokeWidth = 2.5.dp.toPx()
        val shift = size.width * phase
        val brush = Brush.linearGradient(
            colors = GeminiGradientLoop,
            start = Offset(shift, 0f),
            end = Offset(shift + size.width, 0f),
            tileMode = TileMode.Repeated,
        )
        val inset = strokeWidth / 2
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
            style = Stroke(strokeWidth),
        )
    }
}

/** Shimmering "Listening…" placeholder: the gradient flows through the text while dictating. */
@Composable
private fun voiceShimmerTextStyle(): TextStyle {
    val transition = rememberInfiniteTransition(label = "voice-shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_WIDTH_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
        ),
        label = "voice-shimmer-shift",
    )
    return LocalTextStyle.current.copy(
        brush = Brush.linearGradient(
            colors = GeminiGradientLoop,
            start = Offset(shift, 0f),
            end = Offset(shift + SHIMMER_WIDTH_PX, 0f),
            tileMode = TileMode.Repeated,
        ),
    )
}

private const val SHIMMER_WIDTH_PX = 600f

@Composable
private fun GreetingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .geminiGradientTint(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ai_greeting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyMissingContent(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .geminiGradientTint(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ai_key_missing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_key_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.ai_key_missing_button))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://aistudio.google.com/apikey"),
                            ),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.ai_key_get))
            }
        }
    }
}

/**
 * Chat bubble with a long-press menu (copy for all bubbles, edit for the user's own) and a
 * subtle timestamp underneath.
 */
@Composable
private fun MessageBubble(
    text: String,
    timestamp: Long,
    fromUser: Boolean,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Box {
            Surface(
                color = if (fromUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (fromUser) 20.dp else 4.dp,
                    bottomEnd = if (fromUser) 4.dp else 20.dp,
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(text) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            },
                        )
                    },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            MessageMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onCopy = { copyToClipboard(context, text) },
                onEdit = onEdit,
            )
        }
        Timestamp(timestamp)
    }
}

@Composable
private fun ErrorBubble(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                // Error text is what users paste into bug reports — make it copyable too
                .pointerInput(text) {
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                    )
                },
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        MessageMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onCopy = { copyToClipboard(context, text) },
        )
    }
}

@Composable
private fun MessageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_copy)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = {
                onCopy()
                onDismiss()
            },
        )
        if (onEdit != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    onEdit()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun Timestamp(timestamp: Long) {
    val context = LocalContext.current
    val timeText = remember(timestamp) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timestamp))
    }
    Text(
        text = timeText,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NexFlow", text))
    // No confirmation needed: Android 13+ shows the system copy overlay itself
}

/**
 * Thinking indicator with the loop's current activity underneath in a lighter tone —
 * the "transparent signaling" of processing from the Gemini design language.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThinkingBubble(step: AiChatOrchestrator.Progress?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.ai_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stepText = when (step) {
                    AiChatOrchestrator.Progress.Contacting -> stringResource(R.string.ai_step_contacting)
                    AiChatOrchestrator.Progress.SearchingApps -> stringResource(R.string.ai_step_searching_apps)
                    AiChatOrchestrator.Progress.BuildingFlow -> stringResource(R.string.ai_step_building_flow)
                    is AiChatOrchestrator.Progress.Repairing ->
                        stringResource(R.string.ai_step_repairing, step.attempt)
                    null -> null
                }
                if (stepText != null) {
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowPreviewCard(
    flow: FlowJson,
    saved: Boolean,
    isUpdate: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = flow.name, style = MaterialTheme.typography.titleMedium)
            if (flow.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = flow.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            flow.triggers.forEach { trigger ->
                val type = runCatching { TriggerType.valueOf(trigger.type) }.getOrNull() ?: return@forEach
                val info = type.info(context)
                PreviewRow(
                    icon = { Icon(info.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                    label = info.label,
                    summary = type.configSummary(context, trigger.config.toStringMap()),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            flow.actions.sortedBy { it.order }.forEach { action ->
                val type = runCatching { ActionType.valueOf(action.type) }.getOrNull() ?: return@forEach
                val info = type.info(context)
                PreviewRow(
                    icon = { Icon(info.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary) },
                    label = info.label,
                    summary = type.configSummary(context, action.config.toStringMap()),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ai_flow_disabled_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = !saved,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(
                        when {
                            saved -> R.string.ai_flow_saved
                            isUpdate -> R.string.ai_update_flow
                            else -> R.string.ai_save_flow
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(icon: @Composable () -> Unit, label: String, summary: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
