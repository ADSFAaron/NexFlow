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
import com.nexflow.event.NfcEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when an NFC tag is scanned by the user.
 * Android only routes NFC to the foreground activity — this trigger works when the NexFlow app
 * is open. MainActivity owns reader mode and feeds NfcEventSource; it only claims the NFC
 * controller while a flow like this one is enabled, because reader mode blocks every other app
 * on the device from seeing a tag at all.
 *
 * Reports the scanned UID as `{{trigger.tag_id}}`, so one flow can branch on several tags.
 */
@Singleton
class NfcTagTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.NFC_TAG

    override fun observe(trigger: Trigger): Flow<TriggerEvent> {
        val targetTagId = trigger.config["tag_id"]?.trim()?.uppercase() ?: ""
        return NfcEventSource.events
            .filter { tagId ->
                targetTagId.isBlank() || tagId.equals(targetTagId, ignoreCase = true)
            }
            .map { tagId ->
                TriggerEvent(
                    triggerId = trigger.id,
                    flowId = "",
                    metadata = mapOf(TriggerVariables.TAG_ID to tagId),
                )
            }
    }
}
