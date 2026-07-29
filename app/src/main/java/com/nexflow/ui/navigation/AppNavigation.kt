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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.annotation.StringRes
import com.nexflow.R
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nexflow.ui.about.AboutScreen
import com.nexflow.ui.ai.AiChatScreen
import com.nexflow.ui.flows.FlowsScreen
import com.nexflow.ui.flows.detail.FlowDetailScreen
import com.nexflow.ui.globalvars.GlobalVariablesScreen
import com.nexflow.ui.logs.LogsScreen
import com.nexflow.ui.settings.SettingsScreen

sealed class Screen(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Flows : Screen("flows", R.string.nav_flows, Icons.Filled.Bolt, Icons.Outlined.Bolt)
    data object Logs : Screen("logs", R.string.nav_logs, Icons.Filled.History, Icons.Outlined.History)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavScreens = listOf(Screen.Flows, Screen.Logs, Screen.Settings)

private const val AI_CHAT_ROUTE = "ai_chat"

private data class NavItem(val screen: Screen, val selected: Boolean, val label: String)

@Composable
fun NexFlowNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    // Read the theme's motion tokens here (the transition lambdas are not composable):
    // slides are spatial motion, fades are effects — same physics as component motion.
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    NavHost(
        navController = navController,
        startDestination = Screen.Flows.route,
        modifier = modifier,
        enterTransition = {
            val fromIdx = bottomNavScreens.indexOfFirst { it.route == initialState.destination.route }
            val toIdx = bottomNavScreens.indexOfFirst { it.route == targetState.destination.route }
            val isLeftward = fromIdx >= 0 && toIdx >= 0 && toIdx < fromIdx
            slideInHorizontally(
                initialOffsetX = { if (isLeftward) -it / 4 else it / 4 },
                animationSpec = slideSpec,
            ) + fadeIn(animationSpec = fadeSpec)
        },
        exitTransition = {
            // Going to the AI chat: hold the underlying screen still and just fade it a touch,
            // so it doesn't slide sideways while the chat rises up over it.
            if (targetState.destination.route == AI_CHAT_ROUTE) {
                fadeOut(animationSpec = fadeSpec)
            } else {
                val fromIdx = bottomNavScreens.indexOfFirst { it.route == initialState.destination.route }
                val toIdx = bottomNavScreens.indexOfFirst { it.route == targetState.destination.route }
                val isLeftward = fromIdx >= 0 && toIdx >= 0 && toIdx < fromIdx
                slideOutHorizontally(
                    targetOffsetX = { if (isLeftward) it / 4 else -it / 4 },
                    animationSpec = slideSpec,
                ) + fadeOut(animationSpec = fadeSpec)
            }
        },
        popEnterTransition = {
            // Returning from the AI chat: the chat is a sheet sliding off the bottom, so the
            // screen underneath should just be revealed — hold it still (no fade/slide of its own)
            // so nothing crossfades against the sliding chat and reads as a "cut".
            if (initialState.destination.route == AI_CHAT_ROUTE) {
                EnterTransition.None
            } else {
                val fromIdx = bottomNavScreens.indexOfFirst { it.route == initialState.destination.route }
                val toIdx = bottomNavScreens.indexOfFirst { it.route == targetState.destination.route }
                val isBottomNav = fromIdx >= 0 && toIdx >= 0
                val isLeftward = isBottomNav && toIdx < fromIdx
                slideInHorizontally(
                    initialOffsetX = { if (isBottomNav && !isLeftward) it / 4 else -it / 4 },
                    animationSpec = slideSpec,
                ) + fadeIn(animationSpec = fadeSpec)
            }
        },
        popExitTransition = {
            val fromIdx = bottomNavScreens.indexOfFirst { it.route == initialState.destination.route }
            val toIdx = bottomNavScreens.indexOfFirst { it.route == targetState.destination.route }
            val isBottomNav = fromIdx >= 0 && toIdx >= 0
            val isLeftward = isBottomNav && toIdx < fromIdx
            slideOutHorizontally(
                targetOffsetX = { if (isBottomNav && !isLeftward) -it / 4 else it / 4 },
                animationSpec = slideSpec,
            ) + fadeOut(animationSpec = fadeSpec)
        },
    ) {
        composable(Screen.Flows.route) {
            FlowsScreen(
                onFlowClick = { flowId -> navController.navigate("flows/$flowId") },
                onAiClick = { navController.navigate("ai_chat") },
            )
        }
        composable(Screen.Logs.route) { LogsScreen() }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onAboutClick = { navController.navigate("about") },
                onGlobalVariablesClick = { navController.navigate("global_variables") },
            )
        }
        composable("flows/{flowId}") {
            FlowDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable("global_variables") {
            GlobalVariablesScreen(onBack = { navController.popBackStack() })
        }
        composable(
            AI_CHAT_ROUTE,
            // AI reveal, not a lateral page slide: the chat rises up from the bottom while fading
            // in — a single, smooth spatial+effects pairing that reads as "a new surface appears".
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = slideSpec,
                ) + fadeIn(animationSpec = fadeSpec)
            },
            // Leaving is a sheet dismiss: the chat slides ALL the way off the bottom in one
            // motion. No fadeOut — a fade that finishes before the slide is what made the chat
            // vanish mid-screen and read as a "cut". The screen underneath is held static
            // (popEnterTransition = None), so this reads as one continuous downward glide.
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = slideSpec,
                )
            },
        ) {
            AiChatScreen(
                onBack = { navController.popBackStack() },
                onFlowSaved = { flowId ->
                    navController.navigate("flows/$flowId") {
                        // The saved flow replaces the chat in the back stack: back from the
                        // flow detail returns to the Flows list, not the finished chat.
                        popUpTo(Screen.Flows.route)
                    }
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/**
 * Adaptive navigation container. On compact width (phones) it renders a bottom
 * [androidx.compose.material3.NavigationBar]; on medium/expanded width (foldables,
 * tablets, landscape) it automatically switches to a NavigationRail — see
 * developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation.
 *
 * The navigation container is hidden ([NavigationSuiteType.None]) on non-top-level
 * routes (flow detail, about) so those screens get the full width.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NexFlowNavigationScaffold(
    navController: NavController,
    content: @Composable () -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevel = bottomNavScreens.any { it.route == currentRoute }

    val layoutType = if (isTopLevel) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    // Resolve labels here (composable context); navigationSuiteItems is a plain builder scope.
    val items = bottomNavScreens.map { screen ->
        NavItem(screen, selected = currentRoute == screen.route, label = stringResource(screen.labelRes))
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            items.forEach { (screen, selected, label) ->
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = label,
                        )
                    },
                    label = { Text(label) },
                )
            }
        },
        content = content,
    )
}
