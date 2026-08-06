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
package com.nexflow.shortcut

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nexflow.service.FlowExecutionService
import com.nexflow.ui.menu.MenuPickerBridge
import com.nexflow.ui.menu.MenuPickerSheet
import com.nexflow.ui.theme.NexFlowTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * What a home-screen (or long-press) shortcut opens: it runs the flow and shows nothing —
 * the user stays on their launcher. The app UI is never brought up.
 *
 * It does stay alive, invisible, for as long as the run lasts. That is the point: a SHOW_MENU
 * needs a window, and the system only lets an app open one from the background while it already
 * has a visible one. Holding this transparent window keeps the menu legal, so the sheet slides
 * up over the home screen instead of over a freshly opened app.
 *
 * While no menu is pending the window is flagged not-touchable and not-focusable, so the
 * launcher underneath stays fully usable; the flags are cleared only for the sheet itself.
 */
class ShortcutRunActivity : ComponentActivity() {

    private var resumed by mutableStateOf(false)
    private var runToken: String? = null
    private var attachedAsHost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val flowId = intent?.getStringExtra(FlowExecutionService.EXTRA_FLOW_ID)
        if (flowId == null) {
            finish()
            return
        }

        setPassThrough(true)
        MenuPickerBridge.attachHost()
        attachedAsHost = true

        setContent {
            NexFlowTheme {
                val request by MenuPickerBridge.request.collectAsState()
                // Only the visible instance renders: two shortcut taps in a row stack two hosts,
                // and the one underneath must not put a sheet in a window nobody can see.
                val menu = request?.takeIf { resumed }
                LaunchedEffect(menu) { setPassThrough(menu == null) }
                if (menu != null) {
                    MenuPickerSheet(
                        request = menu,
                        onSelect = { MenuPickerBridge.deliver(it) },
                        onDismiss = { MenuPickerBridge.deliver(null) },
                    )
                }
            }
        }

        // A recreated instance (process death) keeps the original token so it still learns when
        // the run it already started ends — and must not start that flow a second time.
        val token = savedInstanceState?.getString(KEY_RUN_TOKEN) ?: UUID.randomUUID().toString()
        runToken = token
        lifecycleScope.launch {
            FlowExecutionService.runFinished.filter { it == token }.first()
            finish()
        }
        if (savedInstanceState == null && !FlowExecutionService.runFlow(this, flowId, runToken = token)) {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        runToken?.let { outState.putString(KEY_RUN_TOKEN, it) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }

    override fun onStop() {
        super.onStop()
        // The user moved on to something else. An invisible host that is no longer on screen
        // cannot legally open a sheet any more, and lingering would strand a later menu — and
        // the flow suspended on it — forever. Let go; the run itself carries on in the service.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (attachedAsHost) MenuPickerBridge.detachHost()
        // Going away for good with a menu still open (task swiped away, activity killed) would
        // leave the flow suspended forever — treat it as a dismissal.
        if (isFinishing && MenuPickerBridge.request.value != null) MenuPickerBridge.deliver(null)
    }

    private fun setPassThrough(enabled: Boolean) {
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (enabled) window.addFlags(flags) else window.clearFlags(flags)
    }

    private companion object {
        const val KEY_RUN_TOKEN = "run_token"
    }
}
