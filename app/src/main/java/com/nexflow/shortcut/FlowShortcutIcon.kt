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
package com.nexflow.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.nexflow.core.automation.model.Flow
import com.nexflow.ui.common.FlowIcons
import kotlin.math.roundToInt

/**
 * Rasterises a flow's icon + background color into the single flattened bitmap that both the
 * dynamic (long-press) and pinned launcher shortcuts need.
 *
 * Deliberately Compose-free: [ShortcutSyncManager] renders from a background coroutine where
 * there is no composition, so the catalog's [ImageVector]s are walked and drawn onto an
 * `android.graphics.Canvas` instead of going through `rememberVectorPainter`.
 *
 * The catalog is Material "Outlined" icons — filled paths, no strokes, no clip paths, no
 * gradients — so only path fills are honored, and every path is drawn white on the flow color.
 */
object FlowShortcutIcon {

    /** Adaptive icon canvas; launchers mask this to the middle ~72dp and may scale it up. */
    private const val CANVAS_DP = 108f

    /** Glyph size inside the 66dp adaptive safe zone. */
    private const val GLYPH_DP = 44f

    /**
     * Used when the flow has no color: the in-app default is `colorScheme.primary`, which is a
     * composition-only value, and a launcher icon must not change with the app's theme anyway.
     */
    private val DEFAULT_BACKGROUND = Color(0xFF3E63DD)

    fun render(context: Context, flow: Flow): Bitmap = render(context, flow.icon, flow.iconColor)

    /**
     * The same glyph on the same flow color, but as a circle on a transparent square, for the
     * badge on a widget card.
     *
     * Circular in the bitmap rather than a square bitmap rounded by the layout:
     * `GlanceModifier.cornerRadius(Dp)` needs API 31 and minSdk here is 30, so on an API 30
     * launcher a squared-off badge would be the one thing on the card that looks broken.
     *
     * @param sizeDp the badge's diameter as laid out; the bitmap is rendered at the display
     *   density so it is not upscaled.
     */
    fun renderBadge(context: Context, iconKey: String?, iconColor: String?, sizeDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1)
        // The glyph occupies a little over half the badge, the proportion a Material icon button
        // uses; filling more makes the circle read as a solid blob at widget sizes.
        val glyphPx = sizePx * 0.55f

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        canvas.drawCircle(
            radius,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = (FlowIcons.color(iconColor) ?: DEFAULT_BACKGROUND).toArgb()
            },
        )

        val vector = FlowIcons.vector(iconKey)
        val scale = glyphPx / maxOf(vector.viewportWidth, vector.viewportHeight)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                (sizePx - vector.viewportWidth * scale) / 2f,
                (sizePx - vector.viewportHeight * scale) / 2f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        drawGroup(canvas, vector.root, matrix, paint)
        return bitmap
    }

    fun render(context: Context, iconKey: String?, iconColor: String?): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (CANVAS_DP * density).roundToInt().coerceAtLeast(1)
        val glyphPx = GLYPH_DP * density

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Full-bleed background: adaptive icons are masked by the launcher, so painting only a
        // circle would leave transparent corners on square/squircle mask launchers.
        canvas.drawColor((FlowIcons.color(iconColor) ?: DEFAULT_BACKGROUND).toArgb())

        val vector = FlowIcons.vector(iconKey)
        val scale = glyphPx / maxOf(vector.viewportWidth, vector.viewportHeight)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                (sizePx - vector.viewportWidth * scale) / 2f,
                (sizePx - vector.viewportHeight * scale) / 2f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        drawGroup(canvas, vector.root, matrix, paint)
        return bitmap
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, parent: Matrix, paint: Paint) {
        // Same composition order as VectorDrawable's group transform.
        val local = Matrix().apply {
            postTranslate(-group.pivotX, -group.pivotY)
            postScale(group.scaleX, group.scaleY)
            postRotate(group.rotation)
            postTranslate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        }
        val matrix = Matrix(parent).apply { preConcat(local) }
        group.forEach { node ->
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, matrix, paint)
                is VectorPath -> {
                    val path = PathParser().addPathNodes(node.pathData).toPath()
                        .apply { fillType = node.pathFillType }
                        .asAndroidPath()
                    path.transform(matrix)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}
