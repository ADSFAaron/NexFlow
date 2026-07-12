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
package com.nexflow.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [com.nexflow.executor.SimulateTapActionExecutor] (runs in FlowExecutionService)
 * with [NexFlowAccessibilityService] via a buffered Channel — same pattern as
 * [ScreenshotCoordinator].
 */
@Singleton
class GestureCoordinator @Inject constructor() {
    data class TapRequest(
        val x: Float,
        val y: Float,
        val result: CompletableDeferred<Boolean>,
    )

    val channel = Channel<TapRequest>(Channel.BUFFERED)
}
