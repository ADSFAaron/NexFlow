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

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.outlined.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.key
import androidx.core.net.toUri
import com.nexflow.ui.common.inContainerColors
import androidx.compose.material.icons.outlined.RestartAlt
import com.nexflow.ui.common.SettingsGroupTitle
import com.nexflow.ui.common.settingsItemShapes
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexflow.ui.common.SettingsBackButton
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsItem
import androidx.compose.material3.TopAppBarDefaults
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexflow.R
import coil3.compose.AsyncImage
import com.nexflow.ai.AiChatOrchestrator
import com.nexflow.ai.AiImageStore
import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.flowschema.FlowJson
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.common.GeminiGradientLoop
import com.nexflow.ui.common.MarkdownText
import com.nexflow.ui.common.geminiGradientTint
import com.nexflow.ui.flows.detail.config.configSummary
import com.nexflow.ui.flows.detail.config.info
import java.io.File
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
    // A different conversation gets a fresh list, scrolled from the top, rather than the old
    // one's position and item animations.
    val conversationId by vm.conversationId.collectAsState()
    val listState = remember(conversationId) { LazyListState() }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingImages by vm.pendingImages.collectAsState()
    val imageLimitText = pluralStringResource(
        R.plurals.ai_image_limit,
        AiImageStore.MAX_PER_MESSAGE,
        AiImageStore.MAX_PER_MESSAGE,
    )
    // The system photo picker: no storage permission, and the user hands over only what they
    // chose. Picking more than there is room for keeps the first ones and says why.
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AiImageStore.MAX_PER_MESSAGE),
    ) { uris ->
        if (uris.size > AiImageStore.MAX_PER_MESSAGE - vm.pendingImages.value.size) {
            scope.launch { snackbarHostState.showSnackbar(imageLimitText) }
        }
        vm.attachImages(uris)
    }

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

    // In flow-edit mode the bar carries the flow's own icon and name — they are what identify
    // the conversation — laid out like the flow editor's bar the user just came from.
    val editingFlow = uiState.editingFlow

    // Openers, for the blank page. They stop once the user has said something — suggestions
    // help someone who is exploring and annoy someone who already knows what they want — so
    // they need no dismiss button: the first message is what ends them. A new chat shows them
    // under the greeting; a flow-edit chat opens with the assistant's intro, so there they
    // follow it as replies to it.
    val suggestions = stringArrayResource(
        if (editingFlow != null) R.array.ai_suggestions_edit else R.array.ai_suggestions_new,
    ).toList()
    val showSuggestions = !uiState.apiKeyMissing &&
        !uiState.isThinking &&
        uiState.messages.none { it is ChatMessage.UserText }
    val showQuickReplies = showSuggestions && uiState.messages.isNotEmpty()

    val itemCount = uiState.messages.size +
        (if (uiState.isThinking) 1 else 0) +
        (if (showQuickReplies) 1 else 0)

    // Following the conversation means staying at the bottom; reading back through it means
    // being left alone. `canScrollForward` is false exactly when the last item is fully shown.
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    // Latched, because the auto-scroll below moves the list itself: sampling "am I at the
    // bottom" after that move would always say yes and the list would drag the user back down.
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom, uiState.isThinking) {
        if (atBottom) followTail = true
    }
    // A drag is the user taking over, so the list stops chasing the tail. Only a *drag* counts —
    // the auto-scroll below moves the list too, and that must not be mistaken for the user.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followTail = false
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

    if (showMenu) {
        ChatMenuSheet(
            editingFlow = editingFlow != null,
            canStartOver = uiState.messages.isNotEmpty() && !uiState.isThinking,
            onAttach = {
                showMenu = false
                if (pendingImages.size >= AiImageStore.MAX_PER_MESSAGE) {
                    scope.launch { snackbarHostState.showSnackbar(imageLimitText) }
                } else {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            onStartOver = {
                showMenu = false
                vm.newChat()
            },
            onOpenSettings = {
                showMenu = false
                onOpenSettings()
            },
            onDismiss = { showMenu = false },
        )
    }

    // The settings pages' page colour, with the soft Gemini glow drawn over it from the very top
    // of the screen. It used to start under an opaque bar, which left a hard edge across the
    // page where the bar ended; the bar is transparent now and the glow runs behind it.
    val pageColor = SettingsDefaults.pageColor
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val glowHeightPx = with(LocalDensity.current) { AmbientGlowHeight.toPx() }
    Scaffold(
        modifier = Modifier
            .background(pageColor)
            .background(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = glowHeightPx)),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                navigationIcon = { SettingsBackButton(onBack) },
                title = {
                    if (editingFlow == null) {
                        Text(stringResource(R.string.ai_chat_title))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlowAvatar(flow = editingFlow, size = 34.dp, ringSize = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(editingFlow.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // The user is spending their own API quota; don't make them guess
                                // how fast. Under the name, where the old header card kept it.
                                if (uiState.tokensUsed > 0) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.ai_tokens_used,
                                            uiState.tokensUsed,
                                            uiState.tokensUsed,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    // Keyed on the conversation, so swapping one for another (another flow, the free
                    // chat, a fresh start) replaces the list outright. Unkeyed, the old messages
                    // left through the list's item-removal animation and were drawn over the new
                    // conversation — and, the bar being transparent, over the bar too.
                    key(conversationId) {
                        when {
                            uiState.apiKeyMissing -> ApiKeyMissingContent(
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.fillMaxSize(),
                            )

                            uiState.messages.isEmpty() -> GreetingContent(
                                suggestions = if (showSuggestions) suggestions else emptyList(),
                                onPick = { vm.sendMessage(it) },
                                modifier = Modifier.fillMaxSize(),
                            )

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
                                    // Same, minus the fade out. Retry deletes the error bubble and
                                    // starts the turn in the same breath, so the thinking indicator
                                    // is placed in the very slot the error is still fading out of —
                                    // two opaque bubbles drawn on top of each other. Nothing else in
                                    // this list is ever replaced in place, so only this one bubble
                                    // has to leave immediately.
                                    val replacedItemModifier = Modifier.animateItem(
                                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                        fadeOutSpec = null,
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
                                            images = message.images.map(vm::imageFile),
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
                                            onRetry = if (
                                                (message.retryText.isNotBlank() || message.retryImages.isNotEmpty()) &&
                                                !uiState.isThinking
                                            ) {
                                                { vm.retry(message.id) }
                                            } else {
                                                null
                                            },
                                            modifier = replacedItemModifier,
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
                                if (showQuickReplies) {
                                    item(key = "quick-replies") {
                                        QuickReplies(
                                            suggestions = suggestions,
                                            onPick = { vm.sendMessage(it) },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                                // Leaves at once: the user's message takes its place.
                                                fadeOutSpec = null,
                                            ),
                                        )
                                    }
                                }
                                // While a turn runs the tail of the list is either the answer
                                // forming in place, or — before the first token — the indicator.
                                if (uiState.isThinking) {
                                    item(key = "thinking") {
                                        // Fades in with the list's own item animation like every
                                        // other bubble; leaves at once, because the answer it turns
                                        // into is placed in this same slot.
                                        val indicatorModifier = Modifier.animateItem(
                                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                            fadeOutSpec = null,
                                        )
                                        val streaming = uiState.streamingText
                                        if (streaming.isNullOrBlank()) {
                                            ThinkingBubble(step = uiState.thinkingStep, modifier = indicatorModifier)
                                        } else {
                                            StreamingBubble(text = streaming, modifier = indicatorModifier)
                                        }
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

                    // At the foot of the transcript, right above the composer — not in the
                    // Scaffold's slot, which is the bottom of the screen and so lay over the
                    // composer itself. Here it also rides up with the keyboard.
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (!uiState.apiKeyMissing) {
                    ChatInputBar(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            vm.updateDraft(it.text)
                        },
                        pendingImages = pendingImages.map { it to vm.imageFile(it) },
                        onRemoveImage = vm::removePendingImage,
                        isThinking = uiState.isThinking,
                        isListening = voice.isListening,
                        canSend = fieldValue.text.isNotBlank() || pendingImages.isNotEmpty(),
                        onOpenMenu = { showMenu = true },
                        onMic = {
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
                        onSendOrStop = {
                            if (uiState.isThinking) vm.stop() else vm.sendMessage(fieldValue.text)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The composer: one rounded surface that holds everything about the next message — what's
 * attached, what's typed, and the controls to add to it or send it.
 *
 * Earlier this was a filled text field with icons tucked into its slots and the send button
 * floating beside it, with attachments in a strip of their own above. Each piece was fine, but
 * together they read as parts laid next to each other rather than one control. Here the surface
 * *is* the control: attachments sit inside it above the text, and send lives in its corner.
 *
 * Send only appears once there is something to send. An always-present, greyed-out button is
 * a control that does nothing most of the time; letting it arrive with the first character is
 * the cue that the message is ready. While a turn runs the same slot is stop — aborting a bad
 * answer has to be the most obvious control on screen, not something buried in a menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pendingImages: List<Pair<String, File>>,
    onRemoveImage: (String) -> Unit,
    isThinking: Boolean,
    isListening: Boolean,
    canSend: Boolean,
    /** The "+": attach an image, start over, pick the model — see [ChatMenuSheet]. */
    onOpenMenu: () -> Unit,
    onMic: () -> Unit,
    onSendOrStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    // Text field look without the text field's container: the surface below is the container.
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
    // Through the button's colours rather than the icon's tint, so the disabled state (while a
    // turn runs) still dims them — an explicit tint would override that and leave them looking live.
    val quietIconColors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.onSurfaceVariant)
    Surface(
        shape = RoundedCornerShape(InputBarCorner),
        color = colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // While dictating, a sweeping Gemini-gradient border + shimmering placeholder signal
            // "listening" (per the Gemini visual design language).
            .then(if (isListening) Modifier.voiceListeningGlow(InputBarCorner) else Modifier),
    ) {
        Column {
            if (pendingImages.isNotEmpty()) {
                PendingImages(
                    files = pendingImages,
                    onRemove = onRemoveImage,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
            }
            val textStyle = MaterialTheme.typography.bodyLarge
            ComposerLayout(lineHeight = textStyle.lineHeight, modifier = Modifier.padding(4.dp)) {
                // A screenshot often says what a sentence can't — which button, which field.
                IconButton(onClick = onOpenMenu, enabled = !isThinking, colors = quietIconColors) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                // Official pattern for a custom-container field: BasicTextField + the M3
                // decoration box, with text colour and cursor passed to the BasicTextField.
                // It stays the same child of the same layout in both arrangements — moving it
                // between a Row and a Column would recreate it and drop focus and the keyboard.
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    interactionSource = interactionSource,
                    textStyle = textStyle.copy(color = colorScheme.onSurface),
                    cursorBrush = SolidColor(colorScheme.primary),
                    maxLines = 6,
                    // Enter is a newline. What gets typed here is often several lines — a URL,
                    // then what to fill in where — and a keyboard that sends on Enter fires off
                    // half a request. Sending is the button's job.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = value.text,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            placeholder = {
                                if (isListening) {
                                    Text(stringResource(R.string.ai_listening), style = voiceShimmerTextStyle())
                                } else {
                                    Text(stringResource(R.string.ai_input_hint))
                                }
                            },
                            colors = fieldColors,
                            // 12dp above and below one 24sp line = the 48dp of the buttons
                            // beside it, so a one-line draft sits level with them.
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = FieldVerticalPadding),
                            container = {},
                        )
                    },
                )
                IconButton(
                    onClick = onMic,
                    enabled = !isThinking,
                    colors = if (isListening) {
                        IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary)
                    } else {
                        quietIconColors
                    },
                ) {
                    Icon(
                        if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = stringResource(
                            if (isListening) R.string.ai_mic_stop_content_desc else R.string.ai_mic_content_desc,
                        ),
                    )
                }
                // Always one child, even while the button is hidden, so the layout below can
                // count on four. The call is qualified because the enclosing Column's scope would
                // otherwise pick ColumnScope.AnimatedVisibility, which can't be used from here.
                Box {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isThinking || canSend,
                        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        FilledIconButton(onClick = onSendOrStop) {
                            Icon(
                                if (isThinking) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(
                                    if (isThinking) R.string.ai_stop else R.string.ai_send,
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
 * Places the composer's four children — leading button, text field, mic, send — in one of two
 * arrangements without ever changing which child is which. (Moving the field between a Row and
 * a Column would recreate it and drop focus and the keyboard.)
 *
 * Compact is one row, everything centred on the row's height. A draft that would wrap in that
 * row gets the full width instead, with the buttons in a row beneath it: squeezed between + on one side and mic and send on the other, a long request wraps every
 * few words into a narrow column that is hard to read back before sending.
 *
 * Which arrangement is decided here, from the field's height *at the compact width*, rather
 * than from a line count reported back after layout. Both directions use that same measure, so
 * the switch happens at the same point whether the draft is growing or shrinking — judging by
 * the line count in the current arrangement would oscillate, since text that wraps at the
 * compact width fits on one line at the full width. And it costs no extra frame: the layout
 * that shows the draft is already the right one.
 */
@Composable
private fun ComposerLayout(
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        check(measurables.size == 4) { "ComposerLayout expects exactly four children" }
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(loose)
        val mic = measurables[2].measure(loose)
        val send = measurables[3].measure(loose)
        val width = constraints.maxWidth
        val buttonsHeight = maxOf(leading.height, mic.height, send.height)

        val compactFieldWidth = (width - leading.width - mic.width - send.width).coerceAtLeast(0)
        // Half a line of slack, so rounding never mistakes one line for two.
        val oneLine = lineHeight.toPx() + FieldVerticalPadding.toPx() * 2
        val expanded = measurables[1].minIntrinsicHeight(compactFieldWidth) > oneLine + lineHeight.toPx() / 2

        // Expanded, the field is inset so its text starts where the + glyph does.
        val inset = ExpandedFieldInset.roundToPx()
        val fieldWidth = if (expanded) width - inset * 2 else compactFieldWidth
        val field = measurables[1].measure(
            Constraints(minWidth = fieldWidth, maxWidth = fieldWidth, maxHeight = constraints.maxHeight),
        )
        if (expanded) {
            // The button row tucks into the field's bottom padding rather than stacking below it.
            val rowTop = field.height - FieldVerticalPadding.roundToPx() / 2
            val height = rowTop + buttonsHeight
            layout(width, height) {
                field.placeRelative(inset, 0)
                fun yOf(childHeight: Int) = rowTop + (buttonsHeight - childHeight) / 2
                leading.placeRelative(0, yOf(leading.height))
                mic.placeRelative(width - send.width - mic.width, yOf(mic.height))
                send.placeRelative(width - send.width, yOf(send.height))
            }
        } else {
            // Compact only ever holds one line (more would have expanded), so centring is right
            // — and it matters, because one line isn't always the 48dp of the buttons: on some
            // phones the CJK system font sets a line a few dp taller than Latin text does.
            // Bottom-aligned, that difference all lands above the buttons and they sit visibly
            // low beside a Chinese placeholder.
            val height = maxOf(buttonsHeight, field.height)
            layout(width, height) {
                fun yOf(childHeight: Int) = (height - childHeight) / 2
                leading.placeRelative(0, yOf(leading.height))
                field.placeRelative(leading.width, yOf(field.height))
                mic.placeRelative(width - send.width - mic.width, yOf(mic.height))
                send.placeRelative(width - send.width, yOf(send.height))
            }
        }
    }
}

private val FieldVerticalPadding = 12.dp

/**
 * Field's own 4dp start padding + this = 16dp: where the + glyph's stroke starts (its 24dp
 * icon sits 12dp into the 48dp button, and the glyph is drawn 4–5dp inside the icon).
 */
private val ExpandedFieldInset = 12.dp

/** Corner of the composer: half its one-line height, so an empty composer is a true pill. */
private val InputBarCorner = 28.dp

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

/**
 * The blank page of a new chat: a short, large greeting that hands the turn to the user, and
 * under it a few things they could ask — each one sends as-is when tapped.
 *
 * The greeting is deliberately a headline rather than a paragraph of instructions; the
 * suggestions below it are the examples, and they can be acted on instead of only read.
 * Scrolls when the keyboard leaves too little room, centred otherwise.
 */
@Composable
internal fun GreetingContent(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                // 8 + the group's own 16 keeps the tiles 24dp from the edges, as the text was.
                .padding(horizontal = 8.dp, vertical = 32.dp),
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
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                // One group of tiles, as on the settings pages: each suggestion is a row to tap.
                SettingsGroup(title = null, modifier = Modifier.widthIn(max = 480.dp)) {
                    suggestions.forEach { suggestion ->
                        item { shapes ->
                            SettingsItem(shapes = shapes, title = suggestion, onClick = { onPick(suggestion) })
                        }
                    }
                }
            }
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
                                "https://aistudio.google.com/apikey".toUri(),
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

/**
 * What the composer's "+" opens, after the Gemini app's own: the things a conversation can be
 * given or have done to it, each named, in a sheet rather than as glyphs in the bar. Starting
 * over used to be a bare icon up there that nobody read as "throw this conversation away".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMenuSheet(
    editingFlow: Boolean,
    canStartOver: Boolean,
    onAttach: () -> Unit,
    onStartOver: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ChatMenuItem(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.ai_attach_image),
                onClick = onAttach,
            )
            ChatMenuItem(
                icon = Icons.Outlined.RestartAlt,
                title = stringResource(R.string.ai_start_over),
                summary = stringResource(
                    if (editingFlow) R.string.ai_start_over_desc_flow else R.string.ai_start_over_desc,
                ),
                enabled = canStartOver,
                onClick = onStartOver,
            )
            ChatMenuItem(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.settings_ai_model),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ChatMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        colors = ListItemDefaults.inContainerColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
    )
}

/** How far down the page the ambient glow fades out, measured from the top of the screen. */
private val AmbientGlowHeight = 260.dp

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
 * Suggested replies to the assistant's opening message in a flow-edit chat, wrapped under it
 * the way quick replies sit under a message — part of the conversation, not a strip bolted
 * onto the input. Tapping one sends it.
 */
@Composable
internal fun QuickReplies(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onPick(suggestion) },
                label = { Text(suggestion) },
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
private fun StreamingBubble(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = bubbleGutter()),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            color = SettingsDefaults.tileColor,
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
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    renderMarkdown: Boolean = false,
    /** Set on the newest assistant reply so TalkBack reads it once when it lands. */
    announce: Boolean = false,
    /** The user stopped this reply part-way — say so instead of pretending it's complete. */
    stopped: Boolean = false,
    showTimestamp: Boolean = true,
    /** Images the user attached, shown above the text. */
    images: List<File> = emptyList(),
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
                // The assistant speaks in the settings tiles' colour, a step off the page.
                color = if (fromUser) MaterialTheme.colorScheme.primaryContainer
                else SettingsDefaults.tileColor,
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
                    if (images.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = if (text.isNotEmpty()) 8.dp else 0.dp),
                        ) {
                            images.forEach { file ->
                                AsyncImage(
                                    model = file,
                                    contentDescription = stringResource(R.string.ai_attached_image),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(if (images.size == 1) 200.dp else 96.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                )
                            }
                        }
                    }
                    if (renderMarkdown) {
                        MarkdownText(markdown = text, style = MaterialTheme.typography.bodyLarge)
                    } else if (text.isNotEmpty()) {
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

/** Images picked for the next message, each with a way to take it back out. */
@Composable
private fun PendingImages(
    files: List<Pair<String, File>>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        files.forEach { (name, file) ->
            Box {
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.ai_attached_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                FilledTonalIconButton(
                    onClick = { onRemove(name) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.ai_remove_image),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
private fun ThinkingBubble(step: AiChatOrchestrator.Progress?, modifier: Modifier = Modifier) {
    Surface(
        color = SettingsDefaults.tileColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
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
                    is AiChatOrchestrator.Progress.Retrying ->
                        stringResource(R.string.ai_step_retrying, step.attempt, step.maxAttempts)
                    is AiChatOrchestrator.Progress.WaitingForQuota ->
                        stringResource(R.string.ai_step_waiting_quota, step.secondsLeft)
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

// internal: app/src/screenshotTest renders a proposal with a diff.
@Composable
internal fun FlowPreviewCard(
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
    // Laid out like the flow editor — the same sections, the same tiles with the same icons —
    // so what the user approves here looks like what they will find when they open the flow.
    // It used to be a card of indented text lines split by a rule, a third look for the same
    // triggers and actions.
    val triggers: List<PreviewStep>
    val actions: List<PreviewStep>
    if (diff != null) {
        triggers = diff.triggers.map { previewStep(it, context, isTrigger = true) }
        actions = diff.actions.map { previewStep(it, context, isTrigger = false) }
    } else {
        triggers = flow.triggers.mapNotNull { trigger ->
            val type = runCatching { TriggerType.valueOf(trigger.type) }.getOrNull() ?: return@mapNotNull null
            val info = type.info(context)
            PreviewStep(info.icon, info.label, type.configSummary(context, trigger.config.toStringMap()))
        }
        actions = flow.actions.sortedBy { it.order }.mapNotNull { action ->
            val type = runCatching { ActionType.valueOf(action.type) }.getOrNull() ?: return@mapNotNull null
            val info = type.info(context)
            PreviewStep(info.icon, info.label, type.configSummary(context, action.config.toStringMap()))
        }
    }

    // A panel of its own, a step off the page, so the proposal reads as one thing the assistant
    // handed over rather than tiles loose among the bubbles; the tiles inside keep the tile
    // colour, a step off the panel.
    Surface(
        color = SettingsDefaults.panelColor,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = flow.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (flow.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = flow.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (diff?.isEmpty == true) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_diff_no_changes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        PreviewGroup(title = stringResource(R.string.fd_section_when), steps = triggers)
        PreviewGroup(title = stringResource(R.string.fd_section_then), steps = actions)

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ai_flow_disabled_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
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

/** One trigger or action of a proposal, ready to draw. [kind] is null outside a diff. */
private data class PreviewStep(
    val icon: ImageVector?,
    val label: String,
    val summary: String,
    val kind: DiffKind? = null,
    /** "key: before → after" for an edited step — the whole point of the diff for it. */
    val changes: List<String> = emptyList(),
)

private fun previewStep(row: FlowDiffRow, context: Context, isTrigger: Boolean): PreviewStep {
    // A type the app doesn't know can't be summarised; the raw name is still better than a gap.
    val icon: ImageVector?
    val label: String
    val summary: String
    if (isTrigger) {
        val type = runCatching { TriggerType.valueOf(row.type) }.getOrNull()
        icon = type?.info(context)?.icon
        label = type?.info(context)?.label ?: row.type
        summary = type?.configSummary(context, row.config).orEmpty()
    } else {
        val type = runCatching { ActionType.valueOf(row.type) }.getOrNull()
        icon = type?.info(context)?.icon
        label = type?.info(context)?.label ?: row.type
        summary = type?.configSummary(context, row.config).orEmpty()
    }
    val changes = if (row.kind == DiffKind.CHANGED) {
        row.changedKeys.map { key ->
            context.getString(
                R.string.ai_diff_field_change,
                key,
                row.previousConfig[key].orEmpty(),
                row.config[key].orEmpty(),
            )
        }
    } else {
        emptyList()
    }
    return PreviewStep(icon, label, summary, row.kind, changes)
}

/** A section of the proposal: the editor's section title over one group of tiles. */
@Composable
private fun PreviewGroup(title: String, steps: List<PreviewStep>) {
    if (steps.isEmpty()) return
    SettingsGroupTitle(title)
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        steps.forEachIndexed { index, step ->
            PreviewStepTile(step = step, shapes = settingsItemShapes(index, steps.size))
        }
    }
}

@Composable
private fun PreviewStepTile(step: PreviewStep, shapes: ListItemShapes) {
    // Colour alone can't carry the meaning — a changed row also says in words what happened.
    val accent = when (step.kind) {
        DiffKind.ADDED -> MaterialTheme.colorScheme.primary
        DiffKind.REMOVED -> MaterialTheme.colorScheme.error
        DiffKind.CHANGED -> MaterialTheme.colorScheme.tertiary
        DiffKind.UNCHANGED, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val kindLabel = when (step.kind) {
        DiffKind.ADDED -> stringResource(R.string.ai_diff_added)
        DiffKind.REMOVED -> stringResource(R.string.ai_diff_removed)
        DiffKind.CHANGED -> stringResource(R.string.ai_diff_changed)
        DiffKind.UNCHANGED, null -> null
    }
    // Unchanged steps are dimmed rather than hidden: the user still needs to see the shape of
    // the flow they're approving, just not to hunt through it.
    val dimmed = step.kind == DiffKind.UNCHANGED
    Surface(
        color = SettingsDefaults.tileColor,
        shape = shapes.shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (dimmed) 0.6f else 1f),
        ) {
            Icon(
                step.icon ?: Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (step.kind == null || dimmed) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (kindLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(text = kindLabel, style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                }
                if (step.summary.isNotBlank()) {
                    Text(
                        text = step.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (step.kind == DiffKind.REMOVED) TextDecoration.LineThrough else null,
                    )
                }
                step.changes.forEach { change ->
                    Text(text = change, style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
        }
    }
}

private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
