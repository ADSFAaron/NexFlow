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
import android.content.Intent
import com.nexflow.core.automation.executor.ActionExecutor
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.interpreter.FlowInterpreter
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.ui.menu.MenuPickerActivity
import com.nexflow.ui.menu.MenuPickerBridge
import com.nexflow.ui.menu.MenuWaitingNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val waitingNotifier: MenuWaitingNotifier,
) : ActionExecutor {

    override val supportedType: ActionType = ActionType.SHOW_MENU

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val title = action.config["title"] ?: ""
        val optionsJson = action.config["options"] ?: "[]"
        val options = runCatching { Json.decodeFromString<List<String>>(optionsJson) }
            .getOrElse { emptyList() }

        if (options.isEmpty()) return ActionResult.Failure("SHOW_MENU has no options")

        // When a NexFlow window is already up (a shortcut run's transparent host, or the app
        // itself) it renders the sheet inline; only otherwise do we start the trampoline. If the
        // system refuses that start — silently, as it does for a background activity start —
        // a notification takes over: tapping it is always allowed to open the sheet.
        val answer = MenuPickerBridge.awaitChoice(
            request = MenuPickerBridge.Request(title, options),
            launchFallback = {
                context.startActivity(
                    Intent(context, MenuPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onNotShown = { waitingNotifier.show(title) },
            onSettled = { waitingNotifier.clear() },
        )

        return when (answer) {
            is MenuPickerBridge.Answer.Chosen -> {
                variables["__menu_choice__"] = answer.option
                variables["__menu_choice_index__"] = options.indexOf(answer.option).toString()
                ActionResult.Success
            }
            // Dismissing a menu is the user declining, not an error. Reporting it as a failure
            // put a red FAIL and an English error string in the log for the ordinary act of
            // changing your mind; the interpreter stops the run either way.
            MenuPickerBridge.Answer.Dismissed -> ActionResult.Skipped()
            // Nobody answered. Same outcome for the run, but the log says which it was.
            MenuPickerBridge.Answer.TimedOut -> ActionResult.Skipped(FlowInterpreter.NOTE_MENU_TIMED_OUT)
        }
    }
}
