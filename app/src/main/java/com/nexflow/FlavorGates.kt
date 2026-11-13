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
package com.nexflow

import com.nexflow.core.automation.model.ActionType
import com.nexflow.core.automation.model.TriggerType

/**
 * What each flavor hides, kept where every flavor can see it: the flow catalog has to say which
 * flavors can run a step, from whichever flavor generates it. [FlavorFeatures] picks its set.
 */
object FlavorGates {
    /** The play flavor does not bundle the SMS and phone-call handlers. */
    val PLAY_HIDDEN_TRIGGERS: Set<TriggerType> = setOf(
        TriggerType.SMS_RECEIVED,
        TriggerType.INCOMING_CALL,
    )

    val PLAY_HIDDEN_ACTIONS: Set<ActionType> = setOf(
        // Google Play restricts SMS/Call permissions to default handler apps.
        ActionType.SEND_SMS,
        ActionType.CALL_PHONE,
        // Needs WRITE_SECURE_SETTINGS, which the play flavor does not declare.
        ActionType.AIRPLANE_TOGGLE,
        // dispatchGesture ("performs actions on the user's behalf") would widen the Play
        // accessibility-usage declaration beyond screenshots/app-detection — github only.
        ActionType.SIMULATE_TAP,
        ActionType.SIMULATE_SWIPE,
    )
}
