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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nexflow.ui.flows.FlowsScreen
import com.nexflow.ui.flows.detail.FlowDetailScreen
import com.nexflow.ui.logs.LogsScreen
import com.nexflow.ui.settings.SettingsScreen

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Flows : Screen("flows", "Flows", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    data object Logs : Screen("logs", "Logs", Icons.Filled.History, Icons.Outlined.History)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavScreens = listOf(Screen.Flows, Screen.Logs, Screen.Settings)

@Composable
fun NexFlowNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Flows.route) {
        composable(Screen.Flows.route) {
            FlowsScreen(
                onFlowClick = { flowId -> navController.navigate("flows/$flowId") },
            )
        }
        composable(Screen.Logs.route) { LogsScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
        composable("flows/{flowId}") {
            FlowDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun NexFlowBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevel = bottomNavScreens.any { it.route == currentRoute }

    AnimatedVisibility(
        visible = isTopLevel,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        NavigationBar {
            bottomNavScreens.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationBarItem(
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
                            contentDescription = screen.label,
                        )
                    },
                    label = { Text(screen.label) },
                )
            }
        }
    }
}
