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
package com.nexflow.ui.menu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexflow.ui.theme.NexFlowTheme
import kotlinx.coroutines.launch

/**
 * Transparent trampoline activity that hosts a Material 3 ModalBottomSheet for
 * the SHOW_MENU action. Delivers the user's selection (or null on dismiss) to
 * MenuPickerBridge and then finishes immediately.
 */
class MenuPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val options = intent.getStringArrayListExtra(EXTRA_OPTIONS) ?: arrayListOf()

        setContent {
            NexFlowTheme {
                MenuPickerSheet(
                    title = title,
                    options = options,
                    onSelect = { choice ->
                        MenuPickerBridge.deliver(choice)
                        finish()
                    },
                    onDismiss = {
                        MenuPickerBridge.deliver(null)
                        finish()
                    },
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        MenuPickerBridge.deliver(null)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_OPTIONS = "options"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuPickerSheet(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = {
                visible = false
                onDismiss()
            },
            sheetState = sheetState,
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn {
                items(options) { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                sheetState.hide()
                                visible = false
                                onSelect(option)
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
