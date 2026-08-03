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
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.widget.NexFlowWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val flowRepository: FlowRepository,
) {
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    /** Last published (id, label, icon, color) set; skips redundant, rate-limited system calls. */
    private var lastDynamicSignature: String? = null
    private var lastPinnedSignature: String? = null

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                flowRepository.observeRecentLogs(limit = 20),
                flowRepository.observeAll(),
            ) { logs, flows ->
                val flowMap = flows.associateBy { it.id }
                val recent = logs.map { it.flowId }
                    .distinct()
                    .take(4)
                    .mapNotNull { flowMap[it] }
                recent to flows
            }.collect { (recentFlows, allFlows) ->
                syncShortcuts(recentFlows)
                refreshPinnedShortcuts(allFlows)
                NexFlowWidget.updateRecentFlows(context, recentFlows)
            }
        }
    }

    private fun syncShortcuts(flows: List<Flow>) {
        val signature = flows.joinToString("|") { it.signature() }
        if (signature == lastDynamicSignature) return
        val shortcuts = flows.mapIndexed { index, flow ->
            buildShortcut(flow).setRank(index).build()
        }
        runCatching { shortcutManager?.dynamicShortcuts = shortcuts }
            .onSuccess { lastDynamicSignature = signature }
    }

    /**
     * A pinned shortcut and the dynamic one share the "flow_<id>" id, so a pinned copy of a flow
     * that has since dropped out of the recent list keeps whatever label/icon it was pinned with.
     * Push renames and icon edits onto those copies — [ShortcutManager.updateShortcuts] is the
     * only call that reaches a shortcut which is pinned but no longer dynamic.
     */
    private fun refreshPinnedShortcuts(allFlows: List<Flow>) {
        val byId = allFlows.associateBy { PinShortcutHelper.shortcutId(it.id) }
        val pinned = runCatching { shortcutManager?.pinnedShortcuts.orEmpty() }.getOrDefault(emptyList())
        val flows = pinned.mapNotNull { byId[it.id] }
        if (flows.isEmpty()) return
        val signature = flows.joinToString("|") { it.signature() }
        if (signature == lastPinnedSignature) return
        runCatching { shortcutManager?.updateShortcuts(flows.map { buildShortcut(it).build() }) }
            .onSuccess { lastPinnedSignature = signature }
    }

    private fun buildShortcut(flow: Flow): ShortcutInfo.Builder =
        ShortcutInfo.Builder(context, PinShortcutHelper.shortcutId(flow.id))
            .setShortLabel(flow.name.take(25))
            .setLongLabel(flow.name)
            .setIcon(Icon.createWithAdaptiveBitmap(FlowShortcutIcon.render(context, flow)))
            .setIntent(PinShortcutHelper.buildIntent(context, flow.id))

    private fun Flow.signature(): String = "$id:$name:$icon:$iconColor"
}
