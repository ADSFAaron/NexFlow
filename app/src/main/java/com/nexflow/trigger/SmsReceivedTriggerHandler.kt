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
import com.nexflow.event.SmsEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when an SMS is received.
 * Requires RECEIVE_SMS permission. Events arrive via SmsReceiver → SmsEventSource.
 */
@Singleton
class SmsReceivedTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.SMS_RECEIVED

    override fun observe(trigger: Trigger): Flow<TriggerEvent> {
        val targetSender = trigger.config["sender"]?.trim() ?: ""
        return SmsEventSource.events
            .filter { event ->
                targetSender.isBlank() ||
                    event.sender.contains(targetSender, ignoreCase = true) ||
                    targetSender.contains(event.sender, ignoreCase = true)
            }
            .map { TriggerEvent(trigger.id, "") }
    }
}
