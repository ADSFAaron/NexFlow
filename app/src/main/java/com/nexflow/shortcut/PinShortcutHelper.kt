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
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nexflow.MainActivity
import com.nexflow.core.automation.model.Flow
import com.nexflow.service.FlowExecutionService

/**
 * Pins a home-screen shortcut for a single flow. Shares the "flow_<id>" shortcut id with
 * [ShortcutSyncManager]'s dynamic (long-press menu) shortcuts, so the system treats both as
 * the same shortcut — which also means the two MUST publish the same label/icon/intent, or
 * whichever writes last silently overwrites the pinned copy on the home screen.
 * [shortcutId] and [buildIntent] are the shared definition; keep [ShortcutSyncManager] on them.
 */
object PinShortcutHelper {

    fun shortcutId(flowId: String): String = "flow_$flowId"

    fun buildIntent(context: Context, flowId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = FlowExecutionService.ACTION_RUN_FLOW
            putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, flow: Flow) {
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(flow.id))
            .setShortLabel(flow.name.take(25))
            .setLongLabel(flow.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(FlowShortcutIcon.render(context, flow)))
            .setIntent(buildIntent(context, flow.id))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
