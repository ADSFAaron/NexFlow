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
package com.nexflow.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexflow.R

/** Lets the user pick a flow icon and background color from the built-in catalog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowIconPickerDialog(
    initialIcon: String?,
    initialColor: String?,
    onConfirm: (icon: String, color: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIcon by rememberSaveable {
        mutableStateOf(initialIcon ?: FlowIcons.DEFAULT_KEY)
    }
    var selectedColor by rememberSaveable {
        mutableStateOf(initialColor ?: FlowIcons.colorPalette.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fd_icon)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Live preview of the chosen combination
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(64.dp)
                        .background(
                            FlowIcons.color(selectedColor) ?: MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        FlowIcons.vector(selectedIcon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    FlowIcons.colorPalette.forEach { hex ->
                        val selected = hex == selectedColor
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .background(FlowIcons.color(hex) ?: Color.Gray, CircleShape)
                                .clickable { selectedColor = hex },
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.icon_selected_color),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    items(FlowIcons.catalog, key = { it.first }) { (key, vector) ->
                        val selected = key == selectedIcon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape,
                                            )
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape,
                                            )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { selectedIcon = key },
                        ) {
                            Icon(
                                vector,
                                contentDescription = key,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIcon, selectedColor) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
