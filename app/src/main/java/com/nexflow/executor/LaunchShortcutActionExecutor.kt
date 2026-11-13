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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.nexflow.core.automation.LogText

/**
 * Launches a shortcut of another app that was picked at configuration time and
 * persisted as an intent: URI. The target may stop existing (app uninstalled/updated)
 * or refuse outside callers (non-exported activity), so every failure mode maps to a
 * distinct message for the run history.
 */
class LaunchShortcutActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType = ActionType.LAUNCH_SHORTCUT

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val uri = action.config["intent_uri"]?.takeIf { it.isNotBlank() }
            ?: return ActionResult.Failure(LogText.of("shortcut_none"))
        val label = action.config["label"] ?: uri

        val intent = when (val parsed = SafeIntents.parse(context, uri)) {
            is SafeIntents.Parsed.Ok -> parsed.intent
            is SafeIntents.Parsed.Invalid -> return ActionResult.Failure(LogText.of("shortcut_invalid", parsed.reason))
            SafeIntents.Parsed.Blocked -> return ActionResult.Failure(LogText.of("shortcut_blocked", label))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            ActionResult.Success
        } catch (e: ActivityNotFoundException) {
            ActionResult.Failure(LogText.of("shortcut_missing", label), e)
        } catch (e: SecurityException) {
            ActionResult.Failure(LogText.of("shortcut_blocked", label), e)
        } catch (e: Exception) {
            ActionResult.Failure(LogText.of("shortcut_failed", label, e.message), e)
        }
    }
}
