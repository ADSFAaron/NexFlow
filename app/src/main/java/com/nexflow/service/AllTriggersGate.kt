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
package com.nexflow.service

import com.nexflow.core.automation.model.TriggerLogic

/**
 * Implements [TriggerLogic.ALL]: a flow runs only once *every* one of its triggers has fired
 * within [windowMs] of each other. Triggers are moments, not states — they never overlap in time —
 * so "all of them" can only mean "all of them recently", and the window is what makes it decidable.
 *
 * State is per flow and in memory only: a partially satisfied flow that is edited, disabled or
 * outlived by a process restart starts over. Persisting it would mean reviving a half-finished
 * combination hours later, which is not what "within a few minutes" promises.
 *
 * Manual runs (Run button, widget, tile, shortcut) never pass through here — pressing Run means
 * run, not "record one third of a combination".
 */
class AllTriggersGate(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private class Fire(val atMs: Long, val variables: Map<String, String>)

    /** flow id → trigger id → its most recent fire, in fire order. */
    private val pending = mutableMapOf<String, LinkedHashMap<String, Fire>>()

    /**
     * Records that [firedTriggerId] fired for [flowId].
     *
     * @param requiredTriggerIds every trigger the flow currently has. Ids that are no longer part
     *   of the flow are dropped, so editing a flow can never leave it permanently waiting on a
     *   trigger that no longer exists.
     * @return the merged `{{trigger.x}}` values when this fire completed the set — later fires win
     *   over earlier ones, so `trigger.type`/`trigger.timestamp` describe the trigger that closed
     *   the combination — or null while the flow is still waiting.
     */
    @Synchronized
    fun onFire(
        flowId: String,
        requiredTriggerIds: List<String>,
        firedTriggerId: String,
        variables: Map<String, String>,
    ): Map<String, String>? {
        val nowMs = now()
        val fires = pending.getOrPut(flowId) { LinkedHashMap() }
        val required = requiredTriggerIds.toSet()
        fires.entries.removeAll { (id, fire) -> id !in required || nowMs - fire.atMs > windowMs }

        // Remove before put so re-firing moves the trigger to the end: insertion order is fire
        // order, and the merge below relies on it.
        fires.remove(firedTriggerId)
        fires[firedTriggerId] = Fire(nowMs, variables)

        if (!required.all { it in fires.keys }) return null

        val merged = buildMap { fires.values.forEach { putAll(it.variables) } }
        pending.remove(flowId)
        return merged
    }

    /** Drops a flow's half-finished combination — used when the engine stops. */
    @Synchronized
    fun clear() = pending.clear()

    companion object {
        /**
         * How long a fired trigger counts towards the combination. Long enough to chain things a
         * person does in sequence (plug in headphones, then get home), short enough that this
         * morning's trigger cannot complete a set tonight.
         */
        const val DEFAULT_WINDOW_MS = 5 * 60 * 1000L
    }
}
