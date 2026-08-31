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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which users are registered for NFC tag dispatch.
 *
 * This is the whole point of the alias: a plain manifest filter registered *every* install as a
 * handler for tags no other app claimed — door badges and transit cards included — regardless of
 * whether the user had ever built an NFC flow. Getting the enable condition wrong in either
 * direction brings that back, so both directions are pinned here.
 *
 * Robolectric because the component's enabled state lives in PackageManager.
 */
@RunWith(AndroidJUnit4::class)
class NfcBackgroundDispatchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatch = NfcBackgroundDispatch(context)
    private val alias = ComponentName(context, "com.nexflow.nfc.NfcTagDispatchAlias")

    private fun state(): Int = context.packageManager.getComponentEnabledSetting(alias)

    private fun flow(enabled: Boolean, type: TriggerType) = Flow(
        id = "f-${type.name}-$enabled",
        schemaVersion = 1,
        name = "test",
        description = "",
        author = null,
        tags = emptyList(),
        enabled = enabled,
        createdAt = 0L,
        updatedAt = 0L,
        triggers = listOf(Trigger(id = "t1", type = type, config = emptyMap())),
        triggerLogic = TriggerLogic.ANY,
        conditions = emptyList(),
        actions = emptyList(),
        variables = emptyList(),
    )

    @Test
    fun `the alias this toggles actually exists in the manifest`() {
        // The alias has no Kotlin class, so it is addressed by a string and a rename in the
        // manifest cannot fail at compile time — it would just mean background NFC silently
        // never works. MATCH_DISABLED_COMPONENTS because the alias ships disabled.
        val info = context.packageManager.getActivityInfo(
            alias,
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )

        assertEquals("com.nexflow.nfc.NfcTagRunActivity", info.targetActivity)
        // Shipping it enabled would put every install back in tag dispatch, which is the bug.
        assertEquals(false, info.enabled)
    }

    @Test
    fun `an enabled NFC flow claims tag dispatch`() {
        dispatch.sync(listOf(flow(enabled = true, type = TriggerType.NFC_TAG)))

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state())
    }

    @Test
    fun `a user with no NFC flow never appears in tag dispatch`() {
        dispatch.sync(listOf(flow(enabled = true, type = TriggerType.TIME)))

        // Not merely "not enabled": the alias ships disabled, and this must leave it that way.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state())
    }

    @Test
    fun `a disabled NFC flow does not claim tag dispatch`() {
        dispatch.sync(listOf(flow(enabled = false, type = TriggerType.NFC_TAG)))

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state())
    }

    @Test
    fun `deleting the last NFC flow gives tag dispatch back`() {
        dispatch.sync(listOf(flow(enabled = true, type = TriggerType.NFC_TAG)))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state())

        // The enabled state is persisted by the platform, so it survives reboots and updates —
        // leaving it on would keep intercepting tags for a feature the user has removed.
        dispatch.sync(emptyList())

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state())
    }

    @Test
    fun `stopping the engine gives tag dispatch back`() {
        dispatch.sync(listOf(flow(enabled = true, type = TriggerType.NFC_TAG)))

        // Automation off: a tag would wake the app, find nothing listening and do nothing —
        // costing the user the tag read and returning nothing for it.
        dispatch.disable()

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state())
    }
}
