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
import com.nexflow.executor.GestureActionSupport.DEFAULT_TAP_DURATION_MS
import com.nexflow.executor.GestureActionSupport.coordinate
import com.nexflow.executor.GestureActionSupport.durationMs
import com.nexflow.service.GestureCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Taps the screen at the configured pixel coordinates via the accessibility service's
 * dispatchGesture. A longer `duration` turns the tap into a long press. Coordinates are
 * device-specific — flows using this are not portable across screen sizes.
 */
class SimulateTapActionExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coordinator: GestureCoordinator,
) : ActionExecutor {

    override val supportedType = ActionType.SIMULATE_TAP

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val x = action.coordinate("x")
        val y = action.coordinate("y")
        if (x == null || y == null) {
            return ActionResult.Failure("Simulate tap needs numeric x and y screen coordinates")
        }
        if (!GestureActionSupport.accessibilityEnabled(context)) {
            return GestureActionSupport.accessibilityFailure("Simulate tap")
        }
        val dispatched = GestureActionSupport.dispatch(
            coordinator,
            listOf(PointF(x, y)),
            action.durationMs("duration", DEFAULT_TAP_DURATION_MS),
        )
        return if (dispatched) ActionResult.Success
        else ActionResult.Failure("Tap gesture was not dispatched")
    }
}
