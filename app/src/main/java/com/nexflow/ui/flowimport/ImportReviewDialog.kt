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
package com.nexflow.ui.flowimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexflow.R

/**
 * What the user sees after an import: the count, then the things that need their attention **one
 * at a time**, in the same shape as the permission wizard.
 *
 * An import of a real MacroDroid backup can raise dozens of warnings. Listed together they were a
 * wall of text the user could only dismiss; taken one at a time each has room to say what happened
 * and what to do, and — when the warning knows which item it is about — to open that item directly.
 */
@Composable
fun ImportReviewDialog(
    result: ImportResult,
    onDismiss: () -> Unit,
    onOpenItem: (flowId: String, itemId: String) -> Unit,
) {
    val context = LocalContext.current
    var index by rememberSaveable(result) { mutableIntStateOf(0) }
    val warnings = remember(result) { result.warnings }
    val current = warnings.getOrNull(index)

    val isLast = index >= warnings.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = when {
                    result.error != null -> Icons.Outlined.ErrorOutline
                    current != null -> Icons.Outlined.ReportProblem
                    else -> Icons.Outlined.CheckCircle
                },
                contentDescription = null,
            )
        },
        title = {
            Text(
                when {
                    result.error != null -> stringResource(R.string.flows_import_failed)
                    current != null -> stringResource(R.string.import_review_title)
                    else -> stringResource(R.string.flows_import_complete)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    result.error != null -> Text(result.error)

                    current == null -> Text(
                        pluralStringResource(R.plurals.flows_imported_count, result.imported, result.imported),
                    )

                    else -> {
                        // Position first: the user needs to know how much is left before reading.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.import_review_step, index + 1, warnings.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (current.flowName.isNotBlank()) {
                                Text(
                                    text = current.flowName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (index + 1f) / warnings.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(current.title(context), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = current.body(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                result.error != null || current == null ->
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }

                // Only offer to open the item when the warning knows which one it is about.
                current.isFixable -> TextButton(
                    onClick = {
                        onDismiss()
                        onOpenItem(current.flowId!!, current.itemId!!)
                    },
                ) { Text(stringResource(R.string.import_review_fix)) }

                else -> TextButton(onClick = { if (isLast) onDismiss() else index++ }) {
                    Text(
                        stringResource(
                            if (isLast) R.string.import_review_done else R.string.import_review_next,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            // Only when there is something else to offer: a lone "next" needs no twin.
            if (result.error == null && current?.isFixable == true) {
                TextButton(onClick = { if (isLast) onDismiss() else index++ }) {
                    Text(
                        stringResource(
                            if (isLast) R.string.import_review_done else R.string.import_review_skip,
                        ),
                    )
                }
            }
        },
    )
}
