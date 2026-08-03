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
package com.nexflow.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One posted notification. [title] and [text] are kept apart so a trigger can filter on either,
 * and [combined] is what a "match anywhere" filter reads.
 */
data class NotificationEvent(
    val packageName: String,
    val title: String,
    val text: String,
) {
    /** Title and body as one line, e.g. `Alice: see you at 7`. */
    val combined: String
        get() = when {
            title.isBlank() -> text
            text.isBlank() -> title
            else -> "$title: $text"
        }
}

/** Bridges NexFlowNotificationListenerService → NotificationTriggerHandler. */
object NotificationEventSource {
    private val _events = MutableSharedFlow<NotificationEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun emit(event: NotificationEvent) {
        _events.tryEmit(event)
    }
}
