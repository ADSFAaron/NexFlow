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
package com.nexflow.receiver

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    /**
     * The code path is worthless if the manifest never delivers the broadcast — which is exactly
     * how the update case went missing: the receiver filtered BOOT_COMPLETED only.
     */
    @Test
    fun manifestDeliversBootAndAppUpdateToTheReceiver() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (action in BootReceiver.HANDLED_ACTIONS) {
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName),
                0,
            ).map { it.activityInfo.name }
            // Glance registers its own MY_PACKAGE_REPLACED receiver to refresh widgets; only ours
            // matters here.
            assertTrue("receivers for $action: $receivers", BootReceiver::class.java.name in receivers)
        }
    }

    @Test
    fun appUpdateRestartsTheServiceWheneverTheMasterSwitchIsOn() {
        // Auto-start is about boot; an update only killed a service that should be running.
        assertTrue(BootReceiver.shouldResumeService(Intent.ACTION_MY_PACKAGE_REPLACED, autoStart = false, serviceEnabled = true))
        assertTrue(BootReceiver.shouldResumeService(Intent.ACTION_MY_PACKAGE_REPLACED, autoStart = true, serviceEnabled = true))
    }

    @Test
    fun nothingOverridesTheMasterSwitch() {
        assertFalse(BootReceiver.shouldResumeService(Intent.ACTION_MY_PACKAGE_REPLACED, autoStart = true, serviceEnabled = false))
        assertFalse(BootReceiver.shouldResumeService(Intent.ACTION_BOOT_COMPLETED, autoStart = true, serviceEnabled = false))
    }

    @Test
    fun bootStillWaitsForAutoStart() {
        assertFalse(BootReceiver.shouldResumeService(Intent.ACTION_BOOT_COMPLETED, autoStart = false, serviceEnabled = true))
        assertTrue(BootReceiver.shouldResumeService(Intent.ACTION_BOOT_COMPLETED, autoStart = true, serviceEnabled = true))
    }

    @Test
    fun otherActionsDoNothing() {
        assertFalse(BootReceiver.shouldResumeService(Intent.ACTION_PACKAGE_REPLACED, autoStart = true, serviceEnabled = true))
        assertFalse(BootReceiver.shouldResumeService(null, autoStart = true, serviceEnabled = true))
    }
}
