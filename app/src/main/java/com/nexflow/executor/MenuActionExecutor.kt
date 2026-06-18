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
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import com.nexflow.ui.menu.MenuPickerActivity
import com.nexflow.ui.menu.MenuPickerBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MenuActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionExecutor {

    override val supportedType: ActionType = ActionType.SHOW_MENU

    override suspend fun execute(action: Action, variables: MutableMap<String, String>): ActionResult {
        val title = action.config["title"] ?: ""
        val optionsJson = action.config["options"] ?: "[]"
        val options = runCatching { Json.decodeFromString<List<String>>(optionsJson) }
            .getOrElse { emptyList() }

        if (options.isEmpty()) return ActionResult.Failure("SHOW_MENU has no options")

        val choice = MenuPickerBridge.awaitChoice {
            val intent = Intent(context, MenuPickerActivity::class.java).apply {
                putExtra(MenuPickerActivity.EXTRA_TITLE, title)
                putStringArrayListExtra(MenuPickerActivity.EXTRA_OPTIONS, ArrayList(options))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        return if (choice != null) {
            variables["__menu_choice__"] = choice
            variables["__menu_choice_index__"] = options.indexOf(choice).toString()
            ActionResult.Success
        } else {
            ActionResult.Failure("Menu dismissed by user")
        }
    }
}
