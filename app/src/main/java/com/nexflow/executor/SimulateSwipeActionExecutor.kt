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
package com.nexflow.executor

import android.content.Context
import android.graphics.PointF
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.executor.GestureActionSupport.DEFAULT_SWIPE_DURATION_MS
import com.nexflow.executor.GestureActionSupport.coordinate
import com.nexflow.executor.GestureActionSupport.durationMs
import com.nexflow.service.GestureCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Drags one finger from (x1, y1) to (x2, y2) over `duration` ms. The duration is what the
 * receiving app reads as velocity: a fling needs a short one, a slow drag (e.g. a seek bar)
 * a long one. Like SIMULATE_TAP, coordinates are raw screen pixels and device-specific.
 */
class SimulateSwipeActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coordinator: GestureCoordinator,
) : ActionExecutor {

    override val supportedType = ActionType.SIMULATE_SWIPE

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val x1 = action.coordinate("x1")
        val y1 = action.coordinate("y1")
        val x2 = action.coordinate("x2")
        val y2 = action.coordinate("y2")
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return ActionResult.Failure("Simulate swipe needs numeric x1, y1, x2 and y2 screen coordinates")
        }
        if (x1 == x2 && y1 == y2) {
            return ActionResult.Failure("Simulate swipe needs a start and end point that differ")
        }
        if (!GestureActionSupport.accessibilityEnabled(context)) {
            return GestureActionSupport.accessibilityFailure("Simulate swipe")
        }
        val dispatched = GestureActionSupport.dispatch(
            coordinator,
            listOf(PointF(x1, y1), PointF(x2, y2)),
            action.durationMs("duration", DEFAULT_SWIPE_DURATION_MS),
        )
        return if (dispatched) ActionResult.Success
        else ActionResult.Failure("Swipe gesture was not dispatched")
    }
}
