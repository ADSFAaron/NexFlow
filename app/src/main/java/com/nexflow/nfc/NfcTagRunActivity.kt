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
package com.nexflow.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.nexflow.event.NfcEventSource
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.service.FlowExecutionService

/**
 * What a tag opens when NexFlow is closed. It shows nothing and finishes immediately — the user
 * taps their tag and the flow runs, with no app appearing over whatever they were doing.
 *
 * An Activity is the only way in: the tag dispatch system starts activities, there is no
 * broadcast for it. Reaching it at all requires [NfcTagDispatchAlias][com.nexflow.nfc] to be
 * enabled, which only happens while a flow that could use a tag exists — see
 * [NfcBackgroundDispatch].
 *
 * Unlike [com.nexflow.shortcut.ShortcutRunActivity] this does not stay alive for the run. A
 * shortcut has to host a SHOW_MENU sheet over the launcher; a tag hands its id to the engine
 * and is done, and the engine owns everything after that.
 */
class NfcTagRunActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatch(intent)
        finish()
    }

    private fun dispatch(intent: Intent?) {
        intent ?: return
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
            ?: return
        val tagId = tag.id.joinToString("") { "%02X".format(it) }

        // The tag may have woken a dead process, so the engine that listens for it might not be
        // running yet. Start it first; NfcEventSource holds the id until the engine subscribes.
        // Respect the master switch: if the user turned automation off, a tag must not turn it
        // back on, and there is then nothing to hand the id to.
        if (!ServiceEnabledPrefs.get(this)) return
        NfcEventSource.emit(tagId)
        FlowExecutionService.start(this)
    }
}
