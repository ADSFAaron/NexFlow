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
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nexflow.MainActivity
import com.nexflow.core.automation.model.Flow
import com.nexflow.service.FlowExecutionService

/**
 * Pins a home-screen shortcut for a single flow, independent of [ShortcutSyncManager]'s
 * dynamic (long-press menu) shortcuts. Uses the same "flow_<id>" shortcut id, so the
 * system treats both as the same shortcut — pinning survives even if the dynamic list
 * later evicts this flow.
 */
object PinShortcutHelper {

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, flow: Flow, iconBitmap: Bitmap) {
        val shortcut = ShortcutInfoCompat.Builder(context, "flow_${flow.id}")
            .setShortLabel(flow.name.take(25))
            .setLongLabel(flow.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = FlowExecutionService.ACTION_RUN_FLOW
                    putExtra(FlowExecutionService.EXTRA_FLOW_ID, flow.id)
                },
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
