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
package com.nexflow.ui.ai

import com.nexflow.core.flowschema.FlowJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What happened to one trigger or action between the flow on disk and the one the model just
 * proposed.
 */
enum class DiffKind { ADDED, REMOVED, CHANGED, UNCHANGED }

/** One row of a flow diff: a trigger or action, and how it differs from the stored flow. */
data class FlowDiffRow(
    val kind: DiffKind,
    /** Trigger/action type name, e.g. `SET_VOLUME`. Uppercase, as the schema stores it. */
    val type: String,
    val config: Map<String, String>,
    /** For [DiffKind.CHANGED]: the config as it is today, so the UI can show before → after. */
    val previousConfig: Map<String, String> = emptyMap(),
) {
    /** Config keys whose value differs. Empty unless this row is [DiffKind.CHANGED]. */
    val changedKeys: List<String>
        get() = (config.keys + previousConfig.keys)
            .filter { config[it] != previousConfig[it] }
            .sorted()
}

/** Triggers and actions of a proposed flow, each marked against the flow it would replace. */
data class FlowDiff(
    val triggers: List<FlowDiffRow>,
    val actions: List<FlowDiffRow>,
) {
    /** True when the proposal is identical to what's already saved. */
    val isEmpty: Boolean
        get() = (triggers + actions).all { it.kind == DiffKind.UNCHANGED }
}

/**
 * Diffs a proposed flow against the stored one.
 *
 * Identity can't come from ids: the model never sees them (see `FlowContextFormatter`, which
 * hands it only types and configs), so every proposal comes back with freshly generated ones.
 * Rows are matched structurally instead — same type and same config — via a longest common
 * subsequence, which is what makes "inserted a step in the middle" show as one addition rather
 * than as everything below it having moved.
 *
 * Same-type rows that only differ in config are then paired up as CHANGED, because "you edited
 * the volume" is the answer the user is looking for, not "you deleted a step and added another".
 */
fun diffFlows(current: FlowJson, proposed: FlowJson): FlowDiff = FlowDiff(
    triggers = diffRows(
        current.triggers.map { Row(it.type, it.config.toStringMap()) },
        proposed.triggers.map { Row(it.type, it.config.toStringMap()) },
    ),
    actions = diffRows(
        current.actions.sortedBy { it.order }.map { Row(it.type, it.config.toStringMap()) },
        proposed.actions.sortedBy { it.order }.map { Row(it.type, it.config.toStringMap()) },
    ),
)

private data class Row(val type: String, val config: Map<String, String>)

private fun diffRows(current: List<Row>, proposed: List<Row>): List<FlowDiffRow> =
    pairChanges(lcsDiff(current, proposed))

/**
 * Standard LCS walk: rows present in both lists (same type *and* config) are the anchors, and
 * everything between anchors is a removal or an addition.
 */
private fun lcsDiff(current: List<Row>, proposed: List<Row>): List<FlowDiffRow> {
    val lengths = Array(current.size + 1) { IntArray(proposed.size + 1) }
    for (i in current.indices.reversed()) {
        for (j in proposed.indices.reversed()) {
            lengths[i][j] = if (current[i] == proposed[j]) {
                lengths[i + 1][j + 1] + 1
            } else {
                maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
    }

    val result = mutableListOf<FlowDiffRow>()
    var i = 0
    var j = 0
    while (i < current.size && j < proposed.size) {
        when {
            current[i] == proposed[j] -> {
                result += FlowDiffRow(DiffKind.UNCHANGED, proposed[j].type, proposed[j].config)
                i++
                j++
            }

            lengths[i + 1][j] >= lengths[i][j + 1] -> {
                result += FlowDiffRow(DiffKind.REMOVED, current[i].type, current[i].config)
                i++
            }

            else -> {
                result += FlowDiffRow(DiffKind.ADDED, proposed[j].type, proposed[j].config)
                j++
            }
        }
    }
    while (i < current.size) {
        result += FlowDiffRow(DiffKind.REMOVED, current[i].type, current[i].config)
        i++
    }
    while (j < proposed.size) {
        result += FlowDiffRow(DiffKind.ADDED, proposed[j].type, proposed[j].config)
        j++
    }
    return result
}

/**
 * Collapses a removal immediately followed by an addition of the same type into one CHANGED
 * row — an edited step, not a swap. Only adjacent pairs collapse, so a genuine delete-here /
 * add-there stays two rows.
 */
private fun pairChanges(rows: List<FlowDiffRow>): List<FlowDiffRow> {
    val result = mutableListOf<FlowDiffRow>()
    var index = 0
    while (index < rows.size) {
        val row = rows[index]
        val next = rows.getOrNull(index + 1)
        if (row.kind == DiffKind.REMOVED && next?.kind == DiffKind.ADDED && next.type == row.type) {
            result += FlowDiffRow(
                kind = DiffKind.CHANGED,
                type = next.type,
                config = next.config,
                previousConfig = row.config,
            )
            index += 2
        } else {
            result += row
            index++
        }
    }
    return result
}

private fun JsonObject.toStringMap(): Map<String, String> =
    entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
