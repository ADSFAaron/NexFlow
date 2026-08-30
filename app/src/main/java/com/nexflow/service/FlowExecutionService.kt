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

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexflow.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nexflow.R
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.shortcut.ShortcutSyncManager
import com.nexflow.widget.NexFlowWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification(emptyList()))
        observeRunningFlows()
    }

    /**
     * Keep the ongoing notification naming the flows that are running. Background triggers were
     * previously indistinguishable from nothing happening: the only clue a flow had fired was a
     * Toast the user may well have missed.
     *
     * Started from [onCreate] rather than alongside the engine, because a manual run (widget,
     * tile, shortcut) is served even while the master switch is off.
     */
    private fun observeRunningFlows() {
        serviceScope.launch {
            flowEngine.runningFlows.collectLatest { running ->
                // The platform drops notification updates posted more than about once a second,
                // and a short flow can start and finish inside that window. collectLatest +
                // a settling delay coalesces those bursts and always ends on the latest state;
                // it also spares the shade a flicker for flows that finish in milliseconds.
                delay(NOTIFICATION_SETTLE_MS)
                postServiceNotification(running)
            }
        }
    }

    /**
     * Re-post the ongoing notification. [Service.startForeground] is only for entering the
     * foreground state; updates to the notification it posted go through the notification
     * manager under the same id.
     */
    private fun postServiceNotification(running: List<RunningFlow>) {
        // Without POST_NOTIFICATIONS (Android 13+) there is nothing in the shade to update —
        // the service itself keeps running, so this is a skip, not a failure.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this)
            .notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(running))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Notification Stop = the user turned automation off; persist that intent so
            // reopening the app (or a reboot) doesn't silently restart the service.
            ServiceEnabledPrefs.set(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        val serviceEnabled = ServiceEnabledPrefs.get(this)
        if (intent?.action == ACTION_RUN_FLOW) {
            val flowId = intent.getStringExtra(EXTRA_FLOW_ID)
            if (flowId != null) {
                val triggerVariables = intent.getBundleExtra(EXTRA_TRIGGER_VARS)
                    ?.let { bundle -> bundle.keySet().associateWith { bundle.getString(it).orEmpty() } }
                    ?: emptyMap()
                val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID)
                val runToken = intent.getStringExtra(EXTRA_RUN_TOKEN)
                serviceScope.launch {
                    try {
                        flowEngine.runNow(flowId, triggerVariables, triggerId)
                    } finally {
                        // Always report the end of the run, however it ended: the shortcut host
                        // window waits on this to dismiss itself.
                        runToken?.let { _runFinished.tryEmit(it) }
                    }
                    // Manual runs are allowed while the master switch is off, but the
                    // service must not linger afterwards — run one flow, then leave.
                    if (!ServiceEnabledPrefs.get(this@FlowExecutionService)) stopSelf()
                }
            }
            if (!serviceEnabled) return START_NOT_STICKY
            // Master switch on: fall through so a run request also (re)starts the engine —
            // e.g. after the OS killed the sticky service, the next widget run revives it.
        }
        if (serviceEnabled && !engineStarted) {
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
        return if (serviceEnabled) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        flowEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildServiceNotification(running: List<RunningFlow>): Notification {
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
        val builder = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NexFlow")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            // Every state change re-posts this same id; without this the shade would re-animate
            // the notification on each one even though the channel is silent.
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stopIntent)

        when (running.size) {
            0 -> builder.setContentText(getString(R.string.service_notification_text))
            1 -> builder.setContentText(
                getString(R.string.service_notification_running_one, running.first().name),
            )
            // More than one at a time: the collapsed line counts them, and expanding lists which
            // ones — a single truncated "A, B, C…" would hide exactly the flow being looked for.
            else -> {
                val summary = resources.getQuantityString(
                    R.plurals.service_notification_running_many,
                    running.size,
                    running.size,
                )
                builder.setContentText(summary)
                builder.setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        running.take(MAX_NOTIFICATION_LINES).forEach { style.addLine(it.name) }
                        style.setSummaryText(summary)
                    },
                )
            }
        }
        return builder.build()
    }

    companion object {
        /** How long the running set must hold still before the notification is re-posted. */
        private const val NOTIFICATION_SETTLE_MS = 250L

        /** Cap on the expanded notification's flow lines; the shade truncates beyond this anyway. */
        private const val MAX_NOTIFICATION_LINES = 6

        const val CHANNEL_SERVICE = "nexflow_service"
        const val SERVICE_NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.nexflow.action.STOP_SERVICE"
        const val ACTION_RUN_FLOW = "com.nexflow.action.RUN_FLOW"
        const val EXTRA_FLOW_ID = "flow_id"

        /** Bundle of `{{trigger.x}}` values for this run; see [FlowEngine.runNow]. */
        const val EXTRA_TRIGGER_VARS = "trigger_vars"

        /**
         * Id of the trigger behind this run, when it has one. Absent for widget/tile/shortcut
         * taps — those are manual runs and must not be treated as part of an ALL combination.
         */
        const val EXTRA_TRIGGER_ID = "trigger_id"

        /**
         * Caller-generated id for a single [runFlow] request, echoed on [runFinished] when that
         * run ends. Used by ShortcutRunActivity, which must stay alive (invisibly) for exactly
         * as long as its flow runs so that a SHOW_MENU can open a sheet over the home screen.
         */
        const val EXTRA_RUN_TOKEN = "run_token"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        // Replays recent tokens: a caller that starts a run and only then begins collecting
        // must not miss the completion of a flow that finished in the meantime.
        private val _runFinished = MutableSharedFlow<String>(replay = 8)

        /** Emits the [EXTRA_RUN_TOKEN] of each finished run. */
        val runFinished: SharedFlow<String> = _runFinished.asSharedFlow()

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

        /**
         * Run a single flow by ID via the foreground service. If the service is already
         * running this just delivers the intent; if it was killed, startForegroundService
         * restarts it (callers fired from an exact alarm hold the temporary FGS-start
         * exemption). Swallows ForegroundServiceStartNotAllowedException so a missed start
         * never crashes — the flow is simply skipped that cycle.
         *
         * @return true when the service accepted the start; false when the system refused it,
         *   in which case the flow never runs and no [runFinished] token is ever emitted.
         */
        fun runFlow(
            context: Context,
            flowId: String,
            triggerVariables: Map<String, String> = emptyMap(),
            triggerId: String? = null,
            runToken: String? = null,
        ): Boolean {
            try {
                context.startForegroundService(
                    Intent(context, FlowExecutionService::class.java).apply {
                        action = ACTION_RUN_FLOW
                        putExtra(EXTRA_FLOW_ID, flowId)
                        triggerId?.let { putExtra(EXTRA_TRIGGER_ID, it) }
                        runToken?.let { putExtra(EXTRA_RUN_TOKEN, it) }
                        if (triggerVariables.isNotEmpty()) {
                            putExtra(
                                EXTRA_TRIGGER_VARS,
                                Bundle().apply {
                                    triggerVariables.forEach { (k, v) -> putString(k, v) }
                                },
                            )
                        }
                    },
                )
                return true
            } catch (e: Exception) {
                android.util.Log.w("FlowExecutionService", "Could not start service to run flow", e)
                return false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlowExecutionService::class.java))
        }
    }
}
