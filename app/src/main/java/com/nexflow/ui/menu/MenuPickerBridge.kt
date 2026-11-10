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
package com.nexflow.ui.menu

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Carries a SHOW_MENU request from the suspended MenuActionExecutor to whichever window can
 * render it, and the user's answer back.
 *
 * Two kinds of renderer exist, because a menu must also work when no app UI is on screen:
 *  - a *host*: an activity that is already visible and simply renders [request] inline
 *    (ShortcutRunActivity, so a home-screen shortcut shows only the bottom sheet, never the app);
 *  - the fallback: [MenuPickerActivity], started on demand when no host is attached.
 *
 * The fallback is a background activity start, which the system only permits while the app has a
 * visible window — that is exactly why the shortcut path keeps a host alive instead of relying
 * on it. When it is refused the system says nothing (no exception, no return value), so
 * [awaitChoice] watches for the sheet to actually appear and, if it does not, asks the caller to
 * post a notification — tapping one is always allowed to open an activity.
 *
 * **One menu at a time, in turn.** Different flows run concurrently, so two of them can reach a
 * SHOW_MENU together. There is a single sheet, so the second waits for the first to be answered
 * instead of taking its place: replacing it left the first flow suspended forever, holding its
 * run slot so that it could not even be started again.
 *
 * **Never forever.** An unanswered menu gives up after [TIMEOUT_MS]. A run waiting on a sheet
 * nobody can see would otherwise hold that flow — and every menu queued behind it — until the
 * process died.
 */
object MenuPickerBridge {

    /**
     * One menu. [id] makes every request distinct even when a flow shows the same menu twice in a
     * row: StateFlow and Compose both drop a value equal to the previous one, so without it the
     * second menu would never reach the screen — or reach it wired to the first one's answer.
     */
    data class Request(
        val title: String,
        val options: List<String>,
        val id: Long = nextId.incrementAndGet(),
    )

    private val nextId = AtomicLong(0)

    /** How a menu ended. */
    sealed interface Answer {
        data class Chosen(val option: String) : Answer
        data object Dismissed : Answer
        data object TimedOut : Answer
    }

    /** How long a menu waits for an answer before the run moves on without one. */
    const val TIMEOUT_MS = 5 * 60_000L

    /**
     * How long the sheet has to appear before the menu falls back to a notification. A started
     * activity draws well within this; one refused as a background start never draws at all.
     */
    const val SHOW_GRACE_MS = 2_000L

    private val _request = MutableStateFlow<Request?>(null)

    /** The menu waiting to be answered, or null when nothing is pending. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    /** The request a renderer last reported on screen; compared by identity. */
    private val shown = MutableStateFlow<Request?>(null)

    private val hostCount = AtomicInteger(0)

    private val turn = Mutex()

    @Volatile private var pending: CompletableDeferred<Answer>? = null

    /** True while an already-visible activity is rendering [request] itself. */
    val hasHost: Boolean get() = hostCount.get() > 0

    fun attachHost() {
        hostCount.incrementAndGet()
    }

    fun detachHost() {
        hostCount.decrementAndGet()
    }

    /** Called by a renderer once [request] is actually on screen. */
    fun markShown(request: Request) {
        shown.value = request
    }

    /**
     * Waits for this menu's turn, publishes [request] and suspends until it is answered, dismissed
     * or times out.
     *
     * @param launchFallback invoked only when no host is attached; must bring up
     *   [MenuPickerActivity].
     * @param onNotShown invoked when the sheet has not appeared within [showGraceMs] — the
     *   background start was refused. Should post a notification that opens [MenuPickerActivity].
     * @param onSettled invoked once the menu is over, however it ended; removes that notification.
     */
    suspend fun awaitChoice(
        request: Request,
        launchFallback: () -> Unit,
        onNotShown: () -> Unit = {},
        onSettled: () -> Unit = {},
        timeoutMs: Long = TIMEOUT_MS,
        showGraceMs: Long = SHOW_GRACE_MS,
    ): Answer = turn.withLock {
        val deferred = CompletableDeferred<Answer>()
        pending = deferred
        _request.value = request
        try {
            coroutineScope {
                val watcher = launch {
                    val appeared = withTimeoutOrNull(showGraceMs) { shown.first { it === request } }
                    if (appeared == null && !deferred.isCompleted) onNotShown()
                }
                if (!hasHost) launchFallback()
                val answer = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: Answer.TimedOut
                watcher.cancel()
                answer
            }
        } finally {
            // Only this menu's own state: the next one in line must not be wiped by a run that
            // was cancelled while waiting.
            if (pending === deferred) pending = null
            if (_request.value === request) _request.value = null
            onSettled()
        }
    }

    /**
     * Answers [request] — the chosen option, or null when the user dismissed it. Ignored unless
     * [request] is the menu pending right now: a renderer tearing down after its answer must not
     * dismiss the next menu in line, which may already have taken its place.
     */
    fun deliver(request: Request, choice: String?) {
        if (_request.value !== request) return
        val deferred = pending ?: return
        pending = null
        _request.value = null
        deferred.complete(if (choice != null) Answer.Chosen(choice) else Answer.Dismissed)
    }
}
