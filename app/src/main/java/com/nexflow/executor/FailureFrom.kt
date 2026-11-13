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

import com.nexflow.core.automation.LogText
import com.nexflow.core.automation.executor.ActionResult

/**
 * A failure for an exception caught around an action's work. One this app threw on purpose
 * already carries a coded message and is passed through as it is; anything else — the
 * platform's own exceptions — becomes [code] with the exception's message as its argument.
 * Wrapping a coded message in another would leave a log line that cannot be split apart.
 */
internal fun failureFrom(code: String, error: Throwable): ActionResult.Failure =
    ActionResult.Failure(
        error.message?.takeIf(LogText::isEncoded) ?: LogText.of(code, error.message),
        error,
    )
