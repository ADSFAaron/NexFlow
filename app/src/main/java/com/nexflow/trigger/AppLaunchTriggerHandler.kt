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
import com.nexflow.event.AppLaunchEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires when a specific app comes to the foreground.
 * Requires the NexFlow Accessibility Service to be enabled — events are emitted by
 * NexFlowAccessibilityService when it detects TYPE_WINDOW_STATE_CHANGED.
 *
 * Reports the app that was opened as `{{trigger.package}}` — useful with a blank filter,
 * where the flow fires for every app.
 */
@Singleton
class AppLaunchTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.APP_LAUNCH

    override fun observe(trigger: Trigger): Flow<TriggerEvent> {
        val targetPackage = trigger.config["package_name"]?.trim() ?: ""
        return AppLaunchEventSource.events
            .filter { pkg -> targetPackage.isBlank() || pkg == targetPackage }
            .map { pkg ->
                TriggerEvent(
                    triggerId = trigger.id,
                    flowId = "",
                    metadata = mapOf(TriggerVariables.PACKAGE to pkg),
                )
            }
    }
}
