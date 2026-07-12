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
package com.nexflow.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The blue→purple→coral gradient from Gemini's visual identity
 * (design.google/library/gemini-ai-visual-design), used to mark AI entry points.
 */
val GeminiGradientColors: List<Color> = listOf(
    Color(0xFF4285F4),
    Color(0xFF9B72CB),
    Color(0xFFD96570),
)

val GeminiGradient: Brush = Brush.linearGradient(colors = GeminiGradientColors)

/** [GeminiGradientColors] closed back to the first color — for seamlessly looping sweeps. */
val GeminiGradientLoop: List<Color> =
    GeminiGradientColors + GeminiGradientColors.dropLast(1).reversed() + GeminiGradientColors.first()

/** Recolors the composable's own pixels (e.g. an [androidx.compose.material3.Icon]) with [GeminiGradient]. */
fun Modifier.geminiGradientTint(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(GeminiGradient, blendMode = BlendMode.SrcAtop)
        }
    }
