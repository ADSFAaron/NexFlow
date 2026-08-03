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
package com.nexflow.core.automation.condition

import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType

/**
 * Reads one piece of device state and answers whether a flow's constraint holds right now.
 *
 * Implementations live in the app module (they touch Android APIs) and are wired into the graph
 * with `@Binds @IntoSet` in `ExecutionModule`, exactly like [ActionExecutor][com.nexflow.core.automation.executor.ActionExecutor].
 */
interface ConditionEvaluator {
    val supportedType: ConditionType

    /**
     * @param variables the run's variable map (flow defaults + `g:` globals + `trigger.` values),
     *   for condition types that compare against them.
     * @return whether the condition holds, ignoring [Condition.negate] — [ConditionGate] applies
     *   the negation so no implementation can forget to.
     */
    suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean
}
