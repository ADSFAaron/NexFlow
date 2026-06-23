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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TIME triggers are scheduled through [TimeTriggerScheduler]/[AlarmManager][android.app.AlarmManager],
 * not the in-process [observe] stream. The previous implementation polled with a
 * `while (true) { delay(...) }` loop, which only ran while the foreground service was alive and
 * whose `delay` was suspended (not fired) during Doze, silently skipping scheduled flows.
 *
 * This handler is kept only so [TriggerType.TIME] stays a registered handler type; FlowEngine
 * explicitly excludes TIME from the stream it collects, so [observe] is never subscribed.
 */
@Singleton
class TimeTriggerHandler @Inject constructor() : TriggerHandler {

    override val supportedType = TriggerType.TIME

    override fun observe(trigger: Trigger): Flow<TriggerEvent> = emptyFlow()
}
