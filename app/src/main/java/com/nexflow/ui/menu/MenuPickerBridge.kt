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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Carries a SHOW_MENU request from the suspended MenuActionExecutor to whichever window can
 * render it, and the user's answer back. One pending menu at a time — flows execute
 * sequentially so this is safe.
 *
 * Two kinds of renderer exist, because a menu must also work when no app UI is on screen:
 *  - a *host*: an activity that is already visible and simply renders [request] inline
 *    (ShortcutRunActivity, so a home-screen shortcut shows only the bottom sheet, never the app);
 *  - the fallback: [MenuPickerActivity], started on demand when no host is attached.
 *
 * The fallback is a background activity start, which the system only permits while the app has a
 * visible window — that is exactly why the shortcut path keeps a host alive instead of relying
 * on it.
 */
object MenuPickerBridge {

    data class Request(val title: String, val options: List<String>)

    private val _request = MutableStateFlow<Request?>(null)

    /** The menu waiting to be answered, or null when nothing is pending. */
    val request: StateFlow<Request?> = _request.asStateFlow()

    private val hostCount = AtomicInteger(0)

    @Volatile private var pending: CompletableDeferred<String?>? = null

    /** True while an already-visible activity is rendering [request] itself. */
    val hasHost: Boolean get() = hostCount.get() > 0

    fun attachHost() {
        hostCount.incrementAndGet()
    }

    fun detachHost() {
        hostCount.decrementAndGet()
    }

    /**
     * Publishes [request] and suspends until someone answers it. [launchFallback] is invoked
     * only when no host is attached, and must bring up [MenuPickerActivity].
     */
    suspend fun awaitChoice(request: Request, launchFallback: () -> Unit): String? {
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        _request.value = request
        if (!hasHost) launchFallback()
        return try {
            deferred.await()
        } finally {
            _request.value = null
        }
    }

    /** Answers the pending menu — the chosen option, or null when the user dismissed it. */
    fun deliver(choice: String?) {
        _request.value = null
        pending?.complete(choice)
        pending = null
    }
}
