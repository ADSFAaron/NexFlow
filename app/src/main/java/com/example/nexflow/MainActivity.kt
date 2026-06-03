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
package com.nexflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexflow.service.FlowExecutionService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.nexflow.ui.navigation.NexFlowBottomBar
import com.nexflow.ui.navigation.NexFlowNavHost
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FlowExecutionService.start(this)
        enableEdgeToEdge()
        setContent {
            NexFlowTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { NexFlowBottomBar(navController) },
                ) { innerPadding ->
                    // Apply only bottom padding so content doesn't hide behind the nav bar.
                    // Each screen's own TopAppBar handles the status-bar insets independently.
                    Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        NexFlowNavHost(navController)
                    }
                }
            }
        }
    }
}
