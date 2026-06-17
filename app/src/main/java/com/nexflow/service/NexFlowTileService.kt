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
package com.nexflow.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.core.automation.repository.FlowRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NexFlowTileService : TileService() {

    @Inject lateinit var flowRepository: FlowRepository

    private var tileScope: CoroutineScope? = null
    private var lastFlowId: String? = null

    override fun onStartListening() {
        tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        tileScope?.launch {
            combine(
                flowRepository.observeRecentLogs(limit = 1),
                flowRepository.observeAll(),
            ) { logs, flows ->
                val id = logs.firstOrNull()?.flowId ?: return@combine null
                flows.firstOrNull { it.id == id }
            }.collect { lastFlow ->
                lastFlowId = lastFlow?.id
                qsTile?.apply {
                    state = Tile.STATE_INACTIVE
                    label = lastFlow?.name?.take(20) ?: "NexFlow"
                    contentDescription = if (lastFlow != null) "執行 ${lastFlow.name}" else "開啟 NexFlow"
                    icon = Icon.createWithResource(this@NexFlowTileService, R.drawable.ic_launcher_foreground)
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        tileScope?.cancel()
        tileScope = null
    }

    override fun onClick() {
        val flowId = lastFlowId
        if (flowId != null) {
            startForegroundService(
                Intent(this, FlowExecutionService::class.java).apply {
                    action = FlowExecutionService.ACTION_RUN_FLOW
                    putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
                }
            )
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
