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
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.service.FlowExecutionService
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

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            combine(
                flowRepository.observeRecentLogs(limit = 20),
                flowRepository.observeAll(),
            ) { logs, flows ->
                val flowMap = flows.associateBy { it.id }
                logs.map { it.flowId }
                    .distinct()
                    .take(4)
                    .mapNotNull { flowMap[it] }
            }.collect { recentFlows ->
                syncShortcuts(recentFlows)
                NexFlowWidget.updateRecentFlows(context, recentFlows)
            }
        }
    }

    private fun syncShortcuts(flows: List<Flow>) {
        val shortcuts = flows.mapIndexed { index, flow ->
            ShortcutInfo.Builder(context, "flow_${flow.id}")
                .setShortLabel(flow.name.take(25))
                .setLongLabel(flow.name)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = FlowExecutionService.ACTION_RUN_FLOW
                        putExtra(FlowExecutionService.EXTRA_FLOW_ID, flow.id)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                .setRank(index)
                .build()
        }
        runCatching { shortcutManager?.dynamicShortcuts = shortcuts }
    }
}
