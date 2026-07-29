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
package com.nexflow.ui.flows.detail.config

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexflow.R
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen "tap the screenshot" coordinate picker backing [ConfigField.ScreenCoordinatePicker].
 *
 * There is no Android API that lets a third-party app observe the user's touches in other apps
 * (`ACTION_OUTSIDE` coordinates have been zeroed since Android 12), so a real recorder is out.
 * Instead the user brings their own system screenshot: taps are read as a *fraction* of the
 * image and scaled to the display, which stays correct even when the screenshot was saved at a
 * different resolution than the panel.
 *
 * [initialStart]/[initialEnd] and the [onConfirm] values are all in display pixels.
 */
@Composable
fun CoordinatePickerDialog(
    swipeMode: Boolean,
    initialStart: Offset?,
    initialEnd: Offset?,
    onDismiss: () -> Unit,
    onConfirm: (start: Offset, end: Offset?) -> Unit,
) {
    val context = LocalContext.current
    val screen = remember { displaySize(context) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) imageUri = uri }

    fun launchPicker() =
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        loadFailed = false
        val loaded = withContext(Dispatchers.IO) { decodeScaled(context, uri) }
        bitmap = loaded
        loadFailed = loaded == null
    }

    // Screenshots that were cropped no longer map onto the display, so the scaling would be
    // silently wrong — worth calling out rather than handing back plausible-looking coordinates.
    val aspectMismatch = bitmap?.let {
        abs(it.width.toFloat() / it.height - screen.width.toFloat() / screen.height) > 0.02f
    } ?: false

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (swipeMode) R.string.cp_title_swipe else R.string.cp_title_tap,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when {
                            bitmap == null -> R.string.cp_instructions
                            !swipeMode -> R.string.cp_hint_tap
                            start == null -> R.string.cp_hint_swipe_start
                            else -> R.string.cp_hint_swipe_end
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (aspectMismatch) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cp_aspect_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (loadFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.cp_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val shot = bitmap
                    if (shot == null) {
                        OutlinedButton(onClick = { launchPicker() }) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cp_pick_image))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(
                                    shot.width.toFloat() / shot.height,
                                    matchHeightConstraintsFirst = true,
                                )
                                .onSizeChanged { boxSize = it }
                                .pointerInput(swipeMode, screen) {
                                    detectTapGestures { offset ->
                                        val picked = toScreen(offset, boxSize, screen)
                                        when {
                                            !swipeMode -> start = picked
                                            start == null || end != null -> {
                                                start = picked
                                                end = null
                                            }
                                            else -> end = picked
                                        }
                                    }
                                },
                        ) {
                            Image(
                                bitmap = shot,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            val markerColor = MaterialTheme.colorScheme.primary
                            val endColor = MaterialTheme.colorScheme.tertiary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val a = start?.let { toBox(it, boxSize, screen) }
                                val b = end?.let { toBox(it, boxSize, screen) }
                                if (a != null && b != null) {
                                    drawLine(markerColor, a, b, strokeWidth = 6f)
                                }
                                a?.let {
                                    drawCircle(markerColor, radius = 22f, center = it)
                                    drawCircle(markerColor.copy(alpha = 0.25f), radius = 48f, center = it)
                                }
                                b?.let {
                                    drawCircle(endColor, radius = 22f, center = it)
                                    drawCircle(endColor.copy(alpha = 0.25f), radius = 48f, center = it)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                start?.let { s ->
                    Text(
                        buildString {
                            append(stringResource(R.string.cp_point_start, s.x.toInt(), s.y.toInt()))
                            end?.let { e ->
                                append("   ")
                                append(stringResource(R.string.cp_point_end, e.x.toInt(), e.y.toInt()))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bitmap != null) {
                        TextButton(onClick = { launchPicker() }) {
                            Text(stringResource(R.string.cp_change_image))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { start?.let { onConfirm(it, end) } },
                        enabled = start != null && (!swipeMode || end != null),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

/** Parses a stored coordinate pair back into an [Offset], or null when either side is unset. */
fun pointFrom(x: String?, y: String?): Offset? {
    val px = x?.trim()?.toFloatOrNull() ?: return null
    val py = y?.trim()?.toFloatOrNull() ?: return null
    return Offset(px, py)
}

/** Full display size in pixels — gestures are dispatched in these coordinates. */
private fun displaySize(context: Context): IntSize {
    val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    return IntSize(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1))
}

private fun toScreen(offset: Offset, box: IntSize, screen: IntSize): Offset =
    if (box.width == 0 || box.height == 0) Offset.Zero
    else Offset(
        (offset.x / box.width * screen.width).coerceIn(0f, screen.width - 1f),
        (offset.y / box.height * screen.height).coerceIn(0f, screen.height - 1f),
    )

private fun toBox(screenPoint: Offset, box: IntSize, screen: IntSize): Offset =
    Offset(
        screenPoint.x / screen.width * box.width,
        screenPoint.y / screen.height * box.height,
    )

/**
 * Decodes at most [MAX_DECODE_PX] on the long edge. Full-resolution screenshots are large and
 * the picker only needs enough detail to aim — the tap is converted by fraction, so
 * downsampling costs no accuracy.
 */
private fun decodeScaled(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return@runCatching null

    var sample = 1
    while (longEdge / sample > MAX_DECODE_PX) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?.asImageBitmap()
}.getOrNull()

private const val MAX_DECODE_PX = 1440
