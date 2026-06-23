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
 * Per-flavor feature gating. The play flavor hides SMS and phone-call triggers/actions
 * from the pickers because their executors/handlers are not bundled (and Google Play
 * restricts SMS/Call permissions to default handler apps).
 */
object FlavorFeatures {
    val hiddenTriggerTypes: Set<TriggerType> = setOf(
        TriggerType.SMS_RECEIVED,
        TriggerType.INCOMING_CALL,
    )
    val hiddenActionTypes: Set<ActionType> = setOf(
        ActionType.SEND_SMS,
        ActionType.CALL_PHONE,
        // Needs WRITE_SECURE_SETTINGS, which the play flavor does not declare.
        ActionType.AIRPLANE_TOGGLE,
    )
}
