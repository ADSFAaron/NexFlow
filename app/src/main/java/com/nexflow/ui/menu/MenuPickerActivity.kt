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
package com.nexflow.ui.menu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexflow.ui.theme.NexFlowTheme

/**
 * Transparent trampoline that hosts the SHOW_MENU bottom sheet when nothing else can — i.e.
 * when the flow runs with no NexFlow window on screen. Reads the pending menu straight from
 * [MenuPickerBridge], delivers the user's selection (or null on dismiss) and finishes.
 *
 * Runs from a home-screen shortcut are served by ShortcutRunActivity instead: it is already
 * visible, so it renders the sheet inline and this activity is never started.
 */
class MenuPickerActivity : ComponentActivity() {

    /** The menu this activity put on screen; the only one it may answer. */
    private var displayed: MenuPickerBridge.Request? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NexFlowTheme {
                val request by MenuPickerBridge.request.collectAsState()
                val menu = request
                if (menu == null) {
                    // Answered elsewhere (or already gone) — nothing left to show.
                    LaunchedEffect(Unit) { finish() }
                } else {
                    LaunchedEffect(menu) {
                        displayed = menu
                        MenuPickerBridge.markShown(menu)
                    }
                    MenuPickerSheet(
                        request = menu,
                        onSelect = { choice ->
                            MenuPickerBridge.deliver(menu, choice)
                            finish()
                        },
                        onDismiss = {
                            MenuPickerBridge.deliver(menu, null)
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Back (gesture or button) dismisses the sheet, which already answers the bridge; this
        // only covers the activity being torn down some other way with a menu still pending.
        // deliver() ignores it unless that menu is still the pending one.
        if (isFinishing) displayed?.let { MenuPickerBridge.deliver(it, null) }
    }
}
