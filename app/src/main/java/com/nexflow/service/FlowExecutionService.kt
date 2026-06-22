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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexflow.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nexflow.R
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.shortcut.ShortcutSyncManager
import com.nexflow.widget.NexFlowWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FlowExecutionService : Service() {

    @Inject lateinit var flowEngine: FlowEngine
    @Inject lateinit var repository: FlowRepository
    @Inject lateinit var shortcutSyncManager: ShortcutSyncManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engineStarted = false

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RUN_FLOW) {
            val flowId = intent.getStringExtra(EXTRA_FLOW_ID) ?: return START_STICKY
            serviceScope.launch { flowEngine.runNow(flowId) }
            return START_STICKY
        }
        if (!engineStarted) {
            engineStarted = true
            flowEngine.start(serviceScope)
            shortcutSyncManager.startSync(serviceScope)
            serviceScope.launch {
                repository.observeAll().collect { flows ->
                    NexFlowWidget.updateCounts(
                        applicationContext,
                        flows.count { it.enabled },
                        flows.size,
                    )
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        flowEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildServiceNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FlowExecutionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NexFlow")
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_SERVICE = "nexflow_service"
        const val SERVICE_NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.nexflow.action.STOP_SERVICE"
        const val ACTION_RUN_FLOW = "com.nexflow.action.RUN_FLOW"
        const val EXTRA_FLOW_ID = "flow_id"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            // On Android 12+ a foreground service cannot always be started from the background
            // (e.g. from a BOOT_COMPLETED receiver). When that happens the system throws
            // ForegroundServiceStartNotAllowedException; swallow it so we never crash. The
            // service will start instead the next time the user opens the app.
            try {
                context.startForegroundService(Intent(context, FlowExecutionService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("FlowExecutionService", "Could not start foreground service from background", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlowExecutionService::class.java))
        }
    }
}
