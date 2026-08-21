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
package com.nexflow.ui.navigation

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [NexFlowNavigationScaffold] — the adaptive nav container that decides whether the
 * bottom bar / rail is on screen at all, and what tapping it does to the back stack.
 *
 * The destinations here are empty composables on purpose. Every real screen is Hilt-injected,
 * so rendering them would drag the whole DI graph (Room, WorkManager, location services) into
 * a test about navigation. What is under test is the scaffold's own logic: the top-level route
 * check that hides the bar on detail screens, and the popUpTo/launchSingleTop/restoreState
 * options on each item — the part that decides whether tab-hopping quietly grows the back
 * stack until Back takes several presses to leave the app.
 */
@RunWith(AndroidJUnit4::class)
class NexFlowNavigationScaffoldTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private fun setUpScaffold() {
        rule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                graph = createGraph(startDestination = Screen.Flows.route) {
                    bottomNavScreens.forEach { composable(it.route) {} }
                    composable(DETAIL_ROUTE) {}
                }
            }
            NexFlowNavigationScaffold(navController) {}
        }
    }

    private fun navigateTo(route: String) {
        rule.runOnUiThread { navController.navigate(route) }
        rule.waitForIdle()
    }

    @Test
    fun everyTopLevelDestinationIsReachableFromTheNavItems() {
        setUpScaffold()

        // Walking the whole bar rather than one item: an entry whose route does not exist in
        // the graph throws on click, which is exactly the regression a renamed route causes.
        bottomNavScreens.forEach { screen ->
            rule.onNodeWithText(labelOf(screen)).performClick()
            rule.waitForIdle()
            assertEquals(screen.route, navController.currentDestination?.route)
        }
    }

    @Test
    fun navigationBarIsHiddenOnDetailRoutes() {
        setUpScaffold()
        rule.onNodeWithText(labelOf(Screen.Flows)).assertIsDisplayed()

        navigateTo("flows/abc")

        // NavigationSuiteType.None removes the items entirely so a detail screen gets the full
        // width — the labels must be gone from the tree, not merely off to one side.
        bottomNavScreens.forEach { screen ->
            rule.onNodeWithText(labelOf(screen)).assertDoesNotExist()
        }
    }

    @Test
    fun navigationBarComesBackWhenLeavingTheDetailRoute() {
        setUpScaffold()
        navigateTo("flows/abc")

        rule.runOnUiThread { navController.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithText(labelOf(Screen.Flows)).assertIsDisplayed()
    }

    @Test
    fun hoppingBetweenTabsDoesNotGrowTheBackStack() {
        setUpScaffold()

        rule.onNodeWithText(labelOf(Screen.Logs)).performClick()
        rule.onNodeWithText(labelOf(Screen.Settings)).performClick()
        rule.onNodeWithText(labelOf(Screen.Flows)).performClick()
        rule.waitForIdle()

        // popUpTo(startDestination) + launchSingleTop means the three hops must collapse back
        // onto the start destination, so Back from here leaves the app in one press. Without
        // those options this entry would be "settings".
        assertEquals(Screen.Flows.route, navController.currentDestination?.route)
        assertNull(
            "back from the start destination must exit, not walk back through visited tabs",
            navController.previousBackStackEntry,
        )
    }

    private fun labelOf(screen: Screen): String =
        ApplicationProvider.getApplicationContext<Context>().getString(screen.labelRes)

    private companion object {
        const val DETAIL_ROUTE = "flows/{flowId}"
    }
}
