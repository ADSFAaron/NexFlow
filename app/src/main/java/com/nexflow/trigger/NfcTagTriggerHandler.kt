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
 * Tags arrive two ways: MainActivity's reader mode while the app is in front, and
 * [com.nexflow.nfc.NfcTagRunActivity] when a tag is tapped with the app closed. Both go through
 * [com.nexflow.nfc.NfcTagDispatcher].
 *
 * Reports the scanned UID as `{{trigger.tag_id}}`, so one flow can branch on several tags.
 */
@Singleton
class NfcTagTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.NFC_TAG

    override fun observe(trigger: Trigger): Flow<TriggerEvent> =
        NfcEventSource.events
            .filter { tagId -> matches(trigger, tagId) }
            .map { tagId -> eventFor(trigger, tagId) }

    companion object {
        /**
         * Whether [tagId] is one [trigger] fires on. Shared with [com.nexflow.service.FlowEngine.runForNfcTag],
         * the path for a tag that arrives while nothing is subscribed, so the two cannot disagree.
         */
        fun matches(trigger: Trigger, tagId: String): Boolean {
            val target = trigger.config["tag_id"]?.trim().orEmpty()
            return target.isBlank() || tagId.equals(target, ignoreCase = true)
        }

        fun eventFor(trigger: Trigger, tagId: String) = TriggerEvent(
            triggerId = trigger.id,
            flowId = "",
            metadata = mapOf(TriggerVariables.TAG_ID to tagId),
        )
    }
}
