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
package com.nexflow.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The widget configure screens are exported — the launcher has to reach them — so the widget id
 * in the intent is whatever the caller sent. An id that is not one of NexFlow's widgets made
 * Glance's getGlanceIdBy throw "Invalid AppWidget ID" and took the whole app down with it; it
 * was found by hand on a phone, not by any test. They must close quietly instead.
 *
 * Instrumented, not Robolectric: both are Hilt activities, and the real graph is what this layer
 * provides.
 */
@HiltAndroidTest
class WidgetConfigureForeignIdTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    /** Never allocated to any provider on a fresh device. */
    private val foreignId = 987_654

    private fun stateAfterLaunch(activity: Class<out Activity>): Lifecycle.State {
        val intent = Intent(ApplicationProvider.getApplicationContext(), activity)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, foreignId)
        return ActivityScenario.launch<Activity>(intent).use { it.state }
    }

    @Test
    fun overviewWidgetConfigure_withAnotherAppsWidgetId_closesInsteadOfCrashing() {
        assertEquals(Lifecycle.State.DESTROYED, stateAfterLaunch(OverviewWidgetConfigureActivity::class.java))
    }

    @Test
    fun singleWidgetConfigure_withAnotherAppsWidgetId_closesInsteadOfCrashing() {
        assertEquals(Lifecycle.State.DESTROYED, stateAfterLaunch(WidgetConfigureActivity::class.java))
    }
}
