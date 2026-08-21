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
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.common.GeminiGradientLoop
import com.nexflow.ui.common.MarkdownText
import com.nexflow.ui.common.geminiGradientTint
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
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
    val scope = rememberCoroutineScope()
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

    // Saving while editing keeps the user in the conversation; the trip to the flow is offered,
    // not taken for them.
    val savedMessage = stringResource(R.string.ai_flow_updated)
    val viewFlowAction = stringResource(R.string.ai_view_flow)
    LaunchedEffect(vm) {
        vm.flowSaved.collect { flowId ->
            val result = snackbarHostState.showSnackbar(
                message = savedMessage,
                actionLabel = viewFlowAction,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) onFlowSaved(flowId)
        }
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

    // Flow-edit mode gets a profile-card header instead of an app bar: the flow's own icon and
    // name are what identify the conversation, and the card carries the same close / new-chat
    // actions the bar would have.
    val editingFlow = uiState.editingFlow

    // The header is pinned, so close / new chat stay reachable no matter how long the
    // transcript gets. It starts as the full profile card and shrinks to a one-line bar as
    // soon as the user engages with the conversation — otherwise it would eat half the screen
    // the moment the keyboard opens.
    var headerExpanded by rememberSaveable { mutableStateOf(true) }
    var suggestionsDismissed by rememberSaveable { mutableStateOf(false) }

    val itemCount = uiState.messages.size + if (uiState.isThinking) 1 else 0

    // Following the conversation means staying at the bottom; reading back through it means
    // being left alone. `canScrollForward` is false exactly when the last item is fully shown.
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    // Latched, because the auto-scroll below moves the list itself: sampling "am I at the
    // bottom" after that move would always say yes and the list would drag the user back down.
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom, uiState.isThinking) {
        if (atBottom) followTail = true
    }
    // One signal, two consequences: a drag is the user taking over, so the card gets out of the
    // way and the list stops chasing the tail. Only a *drag* counts — the auto-scroll below
    // moves the list too, and that must not be mistaken for the user pushing anything away.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                headerExpanded = false
                followTail = false
            }
        }
    }

    /**
     * Puts the newest content on screen.
     *
     * [animated] separates the two reasons this happens. Keeping up with a streaming reply must
     * be instant — an animation there would still be catching up when the next token lands, and
     * the text would visibly lag behind itself. Answering the user's tap on "latest" is the
     * opposite: the glide down is what tells them where they were taken.
     */
    suspend fun scrollToTail(animated: Boolean = false) {
        val lastIndex = itemCount - 1
        if (lastIndex < 0) return
        // Landing on an item shows its *top*, which for a long reply is text the user has
        // already read. Offsetting by however much of it hangs below the fold pins its bottom
        // instead, so the newest words are the ones on screen.
        val info = listState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val overflow = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
            ?.let { it.size - viewport }
            ?.coerceAtLeast(0)
            ?: 0

        if (!animated) {
            // Requests the position for the next measure pass instead of forcing a synchronous
            // one. This runs on every streamed chunk, and scrollToItem's forced remeasure at
            // that rate is exactly the kind of work that eats frames.
            listState.requestScrollToItem(lastIndex, overflow)
            return
        }
        listState.animateScrollToItem(lastIndex)
        if (overflow > 0) listState.animateScrollBy(overflow.toFloat())
    }

    // New message, or the streamed reply growing inside the item that's already last.
    LaunchedEffect(itemCount, uiState.streamingText) {
        if (followTail) scrollToTail()
    }
    // The keyboard shortens the list without moving it, so the newest message slides out of
    // sight behind the input row unless we follow it down.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && followTail) scrollToTail()
    }

    Scaffold(
        topBar = {
            if (editingFlow == null) {
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
            }
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
                if (editingFlow != null) {
                    FlowChatHeader(
                        flow = editingFlow,
                        expanded = headerExpanded,
                        onToggleExpanded = { headerExpanded = !headerExpanded },
                        onClose = onBack,
                        onNewChat = { vm.newChat() },
                        onOpenSettings = onOpenSettings,
                        newChatEnabled = uiState.messages.isNotEmpty() && !uiState.isThinking,
                        tokensUsed = uiState.tokensUsed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        // Tapping the transcript is the user saying "let me read" — get the card
                        // out of the way. Taps that land on a bubble or a button are consumed
                        // there and never reach this detector.
                        .pointerInput(Unit) {
                            detectTapGestures { headerExpanded = false }
                        },
                ) {
                    when {
                        uiState.apiKeyMissing -> ApiKeyMissingContent(
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier.fillMaxSize(),
                        )

                        uiState.messages.isEmpty() -> GreetingContent(modifier = Modifier.fillMaxSize())

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(uiState.messages, key = { _, m -> m.id }) { index, message ->
                                val itemModifier = Modifier.animateItem(
                                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                )
                                // A day rule opens the transcript and marks every date change after it.
                                val previous = uiState.messages.getOrNull(index - 1)
                                if (previous == null || !isSameDay(previous.timestamp, message.timestamp)) {
                                    DaySeparator(message.timestamp)
                                }
                                // One time per burst, on the last bubble of it — a clock on
                                // every line is noise nobody reads.
                                val showTimestamp = !message.groupsWith(
                                    uiState.messages.getOrNull(index + 1),
                                )
                                when (message) {
                                    is ChatMessage.UserText -> MessageBubble(
                                        text = message.text,
                                        timestamp = message.timestamp,
                                        fromUser = true,
                                        // Long-press → Edit reloads the sent text into the input field
                                        onEdit = { vm.updateDraft(message.text) },
                                        showTimestamp = showTimestamp,
                                        modifier = itemModifier,
                                    )

                                    is ChatMessage.AssistantText -> MessageBubble(
                                        text = message.text,
                                        timestamp = message.timestamp,
                                        fromUser = false,
                                        // Gemini answers in markdown; render it instead of showing
                                        // raw ** and - characters.
                                        renderMarkdown = true,
                                        announce = index == uiState.messages.lastIndex,
                                        stopped = message.stopped,
                                        showTimestamp = showTimestamp,
                                        modifier = itemModifier,
                                    )

                                    is ChatMessage.Error -> ErrorBubble(
                                        text = message.text,
                                        onRetry = if (message.retryText.isNotBlank() && !uiState.isThinking) {
                                            { vm.retry(message.id) }
                                        } else {
                                            null
                                        },
                                        modifier = itemModifier,
                                    )

                                    is ChatMessage.FlowPreview -> FlowPreviewCard(
                                        flow = message.flow,
                                        saved = message.saved,
                                        isUpdate = uiState.hasSavedFlow,
                                        baseline = uiState.baselineFlow,
                                        onSave = { vm.saveFlow(message) },
                                        modifier = itemModifier,
                                    )
                                }
                            }
                            // While a turn runs the tail of the list is either the answer
                            // forming in place, or — before the first token — the indicator.
                            if (uiState.isThinking) {
                                item(key = "thinking") {
                                    val streaming = uiState.streamingText
                                    if (streaming.isNullOrBlank()) {
                                        ThinkingBubble(step = uiState.thinkingStep)
                                    } else {
                                        StreamingBubble(text = streaming)
                                    }
                                }
                            }
                        }
                    }

                    // The way back down, for when the list stopped chasing the tail. Only
                    // shown when there is actually something below the fold.
                    JumpToLatest(
                        visible = !followTail && !atBottom && uiState.messages.isNotEmpty(),
                        onClick = {
                            followTail = true
                            scope.launch { scrollToTail(animated = true) }
                        },
                    )
                }

                // Openers, for the blank page. They stop once the user has said something —
                // suggestions help someone who is exploring and annoy someone who already
                // knows what they want — and the dismiss button ends them for good.
                if (!uiState.apiKeyMissing &&
                    !suggestionsDismissed &&
                    !uiState.isThinking &&
                    uiState.messages.none { it is ChatMessage.UserText }
                ) {
                    SuggestionChips(
                        suggestions = stringArrayResource(
                            if (editingFlow != null) {
                                R.array.ai_suggestions_edit
                            } else {
                                R.array.ai_suggestions_new
                            },
                        ).toList(),
                        onPick = { vm.sendMessage(it) },
                        onDismiss = { suggestionsDismissed = true },
                    )
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
                            // The mic lives inside the pill, so the input row reads as one control
                            // with the send button beside it rather than three floating pieces.
                            trailingIcon = {
                                IconButton(
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
                                ) {
                                    Icon(
                                        if (voice.isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                        contentDescription = stringResource(
                                            if (voice.isListening) R.string.ai_mic_stop_content_desc
                                            else R.string.ai_mic_content_desc,
                                        ),
                                        tint = if (voice.isListening) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                // Typing needs the room the expanded card is holding.
                                .onFocusChanged { if (it.isFocused) headerExpanded = false }
                                .then(if (voice.isListening) Modifier.voiceListeningGlow() else Modifier),
                            maxLines = 4,
                            // A chat's keyboard ends in send, not a newline nobody wants here.
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (fieldValue.text.isNotBlank() && !uiState.isThinking) {
                                        vm.sendMessage(fieldValue.text)
                                    }
                                },
                            ),
                        )
                        // While a turn runs, send becomes stop — aborting a bad answer has to
                        // be the most obvious control on screen, not something buried in a menu.
                        FilledIconButton(
                            onClick = {
                                if (uiState.isThinking) vm.stop() else vm.sendMessage(fieldValue.text)
                            },
                            enabled = uiState.isThinking || fieldValue.text.isNotBlank(),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Icon(
                                if (uiState.isThinking) {
                                    Icons.Outlined.Stop
                                } else {
                                    Icons.AutoMirrored.Outlined.Send
                                },
                                contentDescription = stringResource(
                                    if (uiState.isThinking) R.string.ai_stop else R.string.ai_send,
                                ),
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
                style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_key_missing_body),
                style = MaterialTheme.typography.bodyLarge,
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
 * Pinned header for a flow-scoped conversation: the flow's own icon and name stand in for the
 * "who am I talking about" that an avatar gives a person-to-person chat.
 *
 * It replaces the app bar in this mode, so it carries the actions the bar would have had —
 * close, new chat, and the AI settings the user may need to switch models mid-conversation.
 * Because it never scrolls away, those actions stay one tap away however long the transcript
 * grows; [expanded] is what keeps that from costing half the screen, shrinking the profile
 * card to a single bar as soon as the user starts reading or typing.
 */
@Composable
private fun FlowChatHeader(
    flow: EditingFlowInfo,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    newChatEnabled: Boolean,
    tokensUsed: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        // It floats over the moving transcript, so it needs a shadow to separate from it.
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "flow-chat-header",
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedFlowHeader(
                    flow = flow,
                    onCollapse = onToggleExpanded,
                    onClose = onClose,
                    onNewChat = onNewChat,
                    onOpenSettings = onOpenSettings,
                    newChatEnabled = newChatEnabled,
                    tokensUsed = tokensUsed,
                )
            } else {
                CollapsedFlowHeader(
                    flow = flow,
                    onExpand = onToggleExpanded,
                    onClose = onClose,
                    onNewChat = onNewChat,
                    onOpenSettings = onOpenSettings,
                    newChatEnabled = newChatEnabled,
                )
            }
        }
    }
}

/** The full profile card: big identity mark, description, and named actions. */
@Composable
private fun ExpandedFlowHeader(
    flow: EditingFlowInfo,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    newChatEnabled: Boolean,
    tokensUsed: Int,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                // Tapping the card itself is the second way to put it away, for users who
                // never discover that tapping the transcript does it.
                .clickable(
                    onClickLabel = stringResource(R.string.ai_flow_header_collapse),
                    onClick = onCollapse,
                )
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            FlowAvatar(flow = flow, size = 72.dp, ringSize = 96.dp)

            Spacer(Modifier.height(16.dp))
            Text(
                text = flow.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // The flow's own description when it has one, otherwise say what this
                // screen is — the card must never show an empty second line.
                text = flow.description.ifBlank {
                    stringResource(R.string.ai_editing_flow_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // The user is spending their own API quota; don't make them guess how fast.
            if (tokensUsed > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pluralStringResource(R.plurals.ai_tokens_used, tokensUsed, tokensUsed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Named, not just drawn: a bare speech-bubble glyph under a flow's portrait
                // reads as "comment on this", which is not what it does.
                HeaderAction(
                    icon = Icons.Outlined.AddComment,
                    label = stringResource(R.string.ai_new_chat),
                    onClick = onNewChat,
                    enabled = newChatEnabled,
                )
                HeaderAction(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.settings_ai_model),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/** The one-line form: still says which flow this is, still offers every action. */
@Composable
private fun CollapsedFlowHeader(
    flow: EditingFlowInfo,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    newChatEnabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.ai_flow_header_expand),
                onClick = onExpand,
            )
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        FlowAvatar(flow = flow, size = 36.dp, ringSize = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = flow.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNewChat, enabled = newChatEnabled) {
            Icon(
                Icons.Outlined.AddComment,
                contentDescription = stringResource(R.string.ai_new_chat),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.settings_ai_model),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_close),
            )
        }
    }
}

/**
 * The flow's icon on its own colour, ringed in a soft tint of that colour — the same identity
 * mark the flow shows everywhere else in the app, at whatever size the header needs.
 */
@Composable
private fun FlowAvatar(flow: EditingFlowInfo, size: Dp, ringSize: Dp) {
    val accent = FlowIcons.color(flow.iconColor) ?: MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ringSize)
            .background(accent.copy(alpha = 0.18f), CircleShape),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(accent, CircleShape),
        ) {
            Icon(
                FlowIcons.vector(flow.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** One of the header card's round, raised action buttons, with its name underneath. */
@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = if (enabled) 1f else 0.38f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    // The visible label sits outside the button, so the button still needs
                    // its own description for TalkBack.
                    contentDescription = label,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
    }
}

/**
 * How wide a bubble may get. Fixed dp would squeeze enlarged text into the same narrow column,
 * so the cap follows the user's font-size setting; the list's own constraints clamp it to the
 * screen when the scale is large.
 */
@Composable
private fun bubbleMaxWidth(): Dp = 340.dp * LocalDensity.current.fontScale

/**
 * Empty space kept on the side a bubble did NOT come from. Without it both speakers' bubbles
 * run edge to edge and the transcript reads as a stack of paragraphs rather than a
 * conversation — the alignment alone is invisible once a bubble is as wide as the screen.
 *
 * It shrinks as the user's text grows, so a large font setting spends the width on words
 * instead of on the gutter.
 */
@Composable
private fun bubbleGutter(): Dp = (56.dp / LocalDensity.current.fontScale).coerceAtLeast(28.dp)

/**
 * Tappable openers above the input. Horizontally scrollable rather than wrapped, so a long
 * suggestion never pushes the input row off a small screen.
 */
@Composable
private fun SuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onPick(suggestion) },
                    label = { Text(suggestion) },
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.ai_suggestions_dismiss),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The floating "back to the newest message" control, fading in over the bottom of the
 * transcript. Written as a [BoxScope] extension so `align` resolves against the transcript's
 * own Box rather than the column it sits in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.JumpToLatest(visible: Boolean, onClick: () -> Unit) {
    // It rises out of the bottom edge and sinks back into it — the direction of the motion is
    // the direction of the trip it offers, and on the way out it reads as "you're there now"
    // rather than as something quietly blinking off.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = spatial) { it } + fadeIn(animationSpec = effects),
        exit = slideOutVertically(animationSpec = spatial) { it } + fadeOut(animationSpec = effects),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp),
    ) {
        JumpToLatestPill(onClick = onClick)
    }
}

@Composable
private fun JumpToLatestPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.ai_jump_to_latest),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * The reply as it arrives. Deliberately the same shape and colour as a finished assistant
 * bubble, so when the turn completes nothing jumps — the caret just stops blinking.
 *
 * No `liveRegion` here on purpose: announcing every token is exactly the "content that updates
 * frequently" case the accessibility guidance warns about. The finished message announces once.
 */
@Composable
private fun StreamingBubble(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = bubbleGutter()),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = 6.dp,
                bottomEnd = 22.dp,
            ),
            modifier = Modifier.widthIn(max = bubbleMaxWidth()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                MarkdownText(markdown = text, style = MaterialTheme.typography.bodyLarge)
                StreamingCaret()
            }
        }
    }
}

/** A pulsing bar under the streamed text: a pause in the stream must not read as a freeze. */
@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "stream-caret")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stream-caret-alpha",
    )
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            // Sized from the text so it stays a caret at every font scale.
            .size(width = 10.dp, height = 2.dp * LocalDensity.current.fontScale)
            .background(LocalContentColor.current.copy(alpha = alpha), CircleShape),
    )
}

/**
 * Chat bubble with a long-press menu (copy for all bubbles, edit for the user's own) and the
 * send time tucked into the bubble's bottom-right corner.
 */
@Composable
private fun MessageBubble(
    text: String,
    timestamp: Long,
    fromUser: Boolean,
    onEdit: (() -> Unit)? = null,
    renderMarkdown: Boolean = false,
    /** Set on the newest assistant reply so TalkBack reads it once when it lands. */
    announce: Boolean = false,
    /** The user stopped this reply part-way — say so instead of pretending it's complete. */
    stopped: Boolean = false,
    showTimestamp: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (fromUser) bubbleGutter() else 0.dp,
                end = if (fromUser) 0.dp else bubbleGutter(),
            ),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Box {
            Surface(
                color = if (fromUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                // A single small corner points the bubble at its sender; the other three stay
                // generously round.
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = if (fromUser) 22.dp else 6.dp,
                    bottomEnd = if (fromUser) 6.dp else 22.dp,
                ),
                modifier = Modifier
                    .widthIn(max = bubbleMaxWidth())
                    .pointerInput(text) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuOpen = true
                            },
                        )
                    }
                    // Polite, and only on the newest reply: a live region on every bubble
                    // would re-announce the whole transcript on each recomposition.
                    .then(
                        if (announce) {
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (renderMarkdown) {
                        MarkdownText(markdown = text, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(text = text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (stopped) {
                            Text(
                                text = stringResource(R.string.ai_stopped_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.6f),
                            )
                        }
                        if (showTimestamp) BubbleTimestamp(timestamp)
                    }
                }
            }
            MessageMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onCopy = { copyToClipboard(context, text) },
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun ErrorBubble(
    text: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    // Errors come from the assistant's side, so they keep the incoming gutter.
    Box(modifier = modifier.padding(end = bubbleGutter())) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .widthIn(max = bubbleMaxWidth())
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                // Without this the only way out of a failed turn is retyping the whole message.
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_retry))
                    }
                }
            }
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
private fun BubbleTimestamp(timestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timeText = remember(timestamp) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(timestamp))
    }
    Text(
        text = timeText,
        style = MaterialTheme.typography.labelSmall,
        // Reads as a footnote on whatever the bubble\'s content colour is, so it works on both
        // the tinted outgoing bubble and the neutral incoming one.
        color = LocalContentColor.current.copy(alpha = 0.6f),
        modifier = modifier.padding(top = 2.dp),
    )
}

/**
 * "Today" / "Yesterday" / a date, drawn between two rules — the separator that tells a chat
 * transcript where one day ends. [DateUtils] gives the localized wording for free.
 */
@Composable
private fun DaySeparator(timestamp: Long) {
    val label = remember(timestamp) {
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Whether [next] belongs to the same burst as this message: same side of the conversation and
 * close enough in time that one timestamp covers both.
 */
private fun ChatMessage.groupsWith(next: ChatMessage?): Boolean {
    if (next == null) return false
    val sameSide = (this is ChatMessage.UserText) == (next is ChatMessage.UserText)
    return sameSide && next.timestamp - timestamp < BURST_WINDOW_MS
}

private const val BURST_WINDOW_MS = 2 * 60 * 1000L

/** Same calendar day in the device's zone — what a chat's day separator actually means. */
private fun isSameDay(first: Long, second: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(first).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(second).atZone(zone).toLocalDate()
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stepText = when (step) {
                    AiChatOrchestrator.Progress.Contacting -> stringResource(R.string.ai_step_contacting)
                    AiChatOrchestrator.Progress.SearchingApps -> stringResource(R.string.ai_step_searching_apps)
                    AiChatOrchestrator.Progress.SearchingShortcuts ->
                        stringResource(R.string.ai_step_searching_shortcuts)
                    AiChatOrchestrator.Progress.BuildingFlow -> stringResource(R.string.ai_step_building_flow)
                    is AiChatOrchestrator.Progress.Repairing ->
                        stringResource(R.string.ai_step_repairing, step.attempt)
                    null -> null
                }
                if (stepText != null) {
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.labelMedium,
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
    /** The saved flow this proposal would replace, when there is one to compare against. */
    baseline: FlowJson?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The model is told to resend the COMPLETE flow, so a one-line change comes back as a full
    // reprint. Without a diff the user has to spot the difference by eye, every single time.
    val diff = remember(flow, baseline) { baseline?.let { diffFlows(it, flow) } }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = flow.name, style = MaterialTheme.typography.titleLarge)
            if (flow.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = flow.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (diff != null) {
                FlowDiffContent(diff = diff, context = context)
            } else {
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
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ai_flow_disabled_note),
                style = MaterialTheme.typography.bodyMedium,
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

/**
 * The proposal as a change list. Unchanged steps are dimmed rather than hidden — the user still
 * needs to see the shape of the flow they're approving, just not to hunt through it.
 */
@Composable
private fun FlowDiffContent(diff: FlowDiff, context: Context) {
    if (diff.isEmpty) {
        Text(
            text = stringResource(R.string.ai_diff_no_changes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
    diff.triggers.forEach { row -> DiffRow(row = row, context = context, isTrigger = true) }
    if (diff.triggers.isNotEmpty() && diff.actions.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
    diff.actions.forEach { row -> DiffRow(row = row, context = context, isTrigger = false) }
}

@Composable
private fun DiffRow(row: FlowDiffRow, context: Context, isTrigger: Boolean) {
    // A type the app doesn't know can't be summarised; the raw name is still better than a gap.
    val label: String
    val summary: String
    if (isTrigger) {
        val type = runCatching { TriggerType.valueOf(row.type) }.getOrNull()
        label = type?.info(context)?.label ?: row.type
        summary = type?.configSummary(context, row.config).orEmpty()
    } else {
        val type = runCatching { ActionType.valueOf(row.type) }.getOrNull()
        label = type?.info(context)?.label ?: row.type
        summary = type?.configSummary(context, row.config).orEmpty()
    }

    // Colour alone can't carry the meaning — every row also has a marker and a word.
    val accent = when (row.kind) {
        DiffKind.ADDED -> MaterialTheme.colorScheme.primary
        DiffKind.REMOVED -> MaterialTheme.colorScheme.error
        DiffKind.CHANGED -> MaterialTheme.colorScheme.tertiary
        DiffKind.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val marker = when (row.kind) {
        DiffKind.ADDED -> "+"
        DiffKind.REMOVED -> "−"
        DiffKind.CHANGED -> "~"
        DiffKind.UNCHANGED -> " "
    }
    val kindLabel = when (row.kind) {
        DiffKind.ADDED -> stringResource(R.string.ai_diff_added)
        DiffKind.REMOVED -> stringResource(R.string.ai_diff_removed)
        DiffKind.CHANGED -> stringResource(R.string.ai_diff_changed)
        DiffKind.UNCHANGED -> null
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.kind == DiffKind.UNCHANGED) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (kindLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (row.kind == DiffKind.REMOVED) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
            }
            // "Volume 8 → 15" is the whole point of the diff for an edited step.
            if (row.kind == DiffKind.CHANGED) {
                row.changedKeys.forEach { key ->
                    Text(
                        text = stringResource(
                            R.string.ai_diff_field_change,
                            key,
                            row.previousConfig[key].orEmpty(),
                            row.config[key].orEmpty(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
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
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
