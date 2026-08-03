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
package com.nexflow.trigger

import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.trigger.TriggerEvent
import com.nexflow.core.automation.trigger.TriggerHandler
import com.nexflow.core.automation.trigger.TriggerVariables
import com.nexflow.core.automation.util.TextMatcher
import com.nexflow.event.NotificationEvent
import com.nexflow.event.NotificationEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when a notification arrives, optionally filtered by app and by a keyword in the
 * notification's title and/or body (config: `keyword`, `match_field`, `match_mode`).
 * Requires Notification Listener permission — user must enable NexFlow in
 * Settings → Notifications → Notification access.
 *
 * Reports the app, title and body as `{{trigger.package}}` / `{{trigger.title}}` /
 * `{{trigger.text}}`.
 */
@Singleton
class NotificationTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.NOTIFICATION_RECEIVED

    override fun observe(trigger: Trigger): Flow<TriggerEvent> {
        val targetPackage = trigger.config["package_name"]?.trim() ?: ""
        val keyword = trigger.config["keyword"]?.trim() ?: ""
        val field = trigger.config["match_field"]?.trim()?.uppercase() ?: FIELD_ANY
        val mode = trigger.config["match_mode"]

        return NotificationEventSource.events
            .filter { event ->
                (targetPackage.isBlank() || event.packageName == targetPackage) &&
                    TextMatcher.matches(event.haystack(field), keyword, mode)
            }
            .map { event ->
                TriggerEvent(
                    triggerId = trigger.id,
                    flowId = "",
                    metadata = mapOf(
                        TriggerVariables.PACKAGE to event.packageName,
                        TriggerVariables.TITLE to event.title,
                        TriggerVariables.TEXT to event.text,
                    ),
                )
            }
    }

    private fun NotificationEvent.haystack(field: String): String = when (field) {
        FIELD_TITLE -> title
        FIELD_TEXT -> text
        else -> combined
    }

    companion object {
        /** `match_field` values: search title + body, only the title, or only the body. */
        const val FIELD_ANY = "ANY"
        const val FIELD_TITLE = "TITLE"
        const val FIELD_TEXT = "TEXT"

        val FIELDS = listOf(FIELD_ANY, FIELD_TITLE, FIELD_TEXT)
    }
}
