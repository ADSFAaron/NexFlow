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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The SHOW_MENU bottom sheet. Shared by every window that can render a menu — the transparent
 * shortcut host and [MenuPickerActivity] — so a menu looks the same wherever it pops up.
 *
 * Laid out as a grouped card list, the same shape the system Settings app uses: one rounded,
 * raised card per option on a dimmer sheet background. It gives every choice a large, obvious
 * tap target and keeps long option texts readable.
 *
 * [onSelect] / [onDismiss] fire exactly once; the caller answers [MenuPickerBridge] with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuPickerSheet(
    request: MenuPickerBridge.Request,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visible by remember(request) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = {
            visible = false
            onDismiss()
        },
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        // A shade below the option cards, so the cards read as raised the way Settings rows do.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (request.title.isNotBlank()) {
                item {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
            }
            itemsIndexed(request.options) { index, option ->
                MenuOptionCard(
                    index = index,
                    option = option,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            visible = false
                            onSelect(option)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuOptionCard(index: Int, option: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        ListItem(
            headlineContent = { Text(option, style = MaterialTheme.typography.titleMedium) },
            leadingContent = { OptionNumber(index) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** The Settings-style leading circle. Sized off the font scale so large type keeps it round. */
@Composable
private fun OptionNumber(index: Int) {
    Box(
        modifier = Modifier
            .size(36.dp * LocalDensity.current.fontScale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
