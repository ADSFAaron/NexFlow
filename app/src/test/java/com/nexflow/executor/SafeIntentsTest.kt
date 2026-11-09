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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.executor.ActionResult
import com.nexflow.core.automation.model.Action
import com.nexflow.core.automation.model.ActionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A flow's intent URI is launched with NexFlow's identity, and a flow can come from a shared
 * file. These pin what such a URI is no longer allowed to do.
 */
@RunWith(AndroidJUnit4::class)
class SafeIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun uriFor(intent: Intent) = intent.toUri(Intent.URI_INTENT_SCHEME)

    @Test
    fun `an own non-exported activity is blocked`() {
        val intent = Intent().setClassName(context.packageName, "com.nexflow.ui.menu.MenuPickerActivity")
        assertEquals(SafeIntents.Parsed.Blocked, SafeIntents.parse(context, uriFor(intent)))
    }

    @Test
    fun `an own exported activity is still allowed, so flows can chain through shortcuts`() {
        val intent = Intent().setClassName(context.packageName, "com.nexflow.shortcut.ShortcutRunActivity")
        assertTrue(SafeIntents.parse(context, uriFor(intent)) is SafeIntents.Parsed.Ok)
    }

    @Test
    fun `uri grant flags, clip data and the selector are stripped`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .apply {
                selector = Intent().setClassName(context.packageName, "com.nexflow.ui.menu.MenuPickerActivity")
            }

        val parsed = SafeIntents.parse(context, uriFor(intent)) as SafeIntents.Parsed.Ok

        assertEquals(0, parsed.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, parsed.intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertNull(parsed.intent.selector)
        assertNull(parsed.intent.clipData)
    }

    @Test
    fun `garbage is reported as invalid rather than thrown`() {
        assertTrue(SafeIntents.parse(context, "intent:#Intent;%%%;end") is SafeIntents.Parsed.Invalid)
    }

    @Test
    fun `the shortcut action refuses an own private activity`() = runTest {
        val intent = Intent().setClassName(context.packageName, "com.nexflow.nfc.NfcTagRunActivity")
        val action = Action(
            id = "a", type = ActionType.LAUNCH_SHORTCUT, order = 0, enabled = true,
            config = mapOf("intent_uri" to uriFor(intent), "label" to "x"),
        )
        val result = LaunchShortcutActionExecutor(context).execute(action, mutableMapOf())
        assertTrue("expected failure, got $result", result is ActionResult.Failure)
    }

    @Test
    fun `write file refuses the app's own storage but not shared storage`() = runTest {
        val executor = WriteFileActionExecutor(context)
        assertTrue(executor.isAppPrivate(context.dataDir.path))
        assertTrue(executor.isAppPrivate("${context.filesDir.path}/x.txt"))
        assertTrue(executor.isAppPrivate("${context.dataDir.path}/shared_prefs/nexflow_ai.xml"))
        assertEquals(false, executor.isAppPrivate("/sdcard/Documents/notes.txt"))

        val action = Action(
            id = "a", type = ActionType.WRITE_FILE, order = 0, enabled = true,
            config = mapOf("path" to "${context.dataDir.path}/shared_prefs/nexflow_ai.xml", "content" to "x"),
        )
        assertTrue(executor.execute(action, mutableMapOf()) is ActionResult.Failure)
    }
}
