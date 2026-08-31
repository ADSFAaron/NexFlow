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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.event.NfcEventSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers NexFlow for NFC tag dispatch only while a flow could actually use a tag.
 *
 * A manifest intent filter is normally all-or-nothing, and that is what made the previous
 * `TAG_DISCOVERED` filter a problem: every user was permanently registered as the handler for
 * every tag no other app claimed, whether or not they had ever built an NFC flow. An
 * `<activity-alias>` shipped disabled and toggled here is the way to make the filter
 * conditional — a disabled component is excluded from intent resolution entirely, so NexFlow
 * does not appear in tag dispatch at all until [sync] turns it on.
 *
 * Enabled state survives reboots and updates, so both directions have to be written: leaving it
 * on after the last NFC flow is deleted would keep intercepting tags for a feature the user no
 * longer has.
 */
@Singleton
class NfcBackgroundDispatch @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alias = ComponentName(context, ALIAS_CLASS)

    /** Enable dispatch when any enabled flow has an NFC trigger, disable it otherwise. */
    fun sync(flows: List<Flow>) {
        val wanted = flows.any { flow ->
            flow.enabled && flow.triggers.any { it.type == TriggerType.NFC_TAG }
        }
        setEnabled(wanted)
    }

    /**
     * Give up tag dispatch. Called when the engine stops: with automation off a tag would wake
     * the app, find nothing listening and do nothing visible — an intercepted tap that costs the
     * user their tag read and returns nothing.
     */
    fun disable() {
        setEnabled(false)
        // Nothing will collect it now, and a stale id delivered whenever the engine next starts
        // would fire a flow the user triggered minutes or days ago.
        NfcEventSource.clearPending()
    }

    private fun setEnabled(enabled: Boolean) {
        val target = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        // Read first: this is a cross-process call that writes to package state, and sync() runs
        // on every change to the flow list.
        if (runCatching { context.packageManager.getComponentEnabledSetting(alias) }
                .getOrNull() == target
        ) {
            return
        }
        try {
            context.packageManager.setComponentEnabledSetting(
                alias,
                target,
                // Without DONT_KILL_APP the platform restarts the process to apply this — which,
                // called from the running engine, would kill the service mid-flow.
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: IllegalArgumentException) {
            // The alias is addressed by a string, so a rename in the manifest lands here rather
            // than at compile time. Swallowing it silently would mean background NFC simply
            // never works, with nothing to show why. NfcBackgroundDispatchTest asserts the name
            // resolves against the merged manifest so this should be unreachable.
            Log.e(TAG, "NFC dispatch alias $ALIAS_CLASS is not declared in the manifest", e)
        }
    }

    private companion object {
        const val TAG = "NfcBackgroundDispatch"

        /**
         * The alias is manifest-only (no Kotlin class of its own), so it is addressed by name.
         * Must match the `android:name` of the activity-alias in AndroidManifest.xml.
         */
        const val ALIAS_CLASS = "com.nexflow.nfc.NfcTagDispatchAlias"
    }
}
