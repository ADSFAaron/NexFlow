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
package com.nexflow.permissions

import com.nexflow.core.automation.repository.FlowRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Overview shown before the guided setup: the flow and everything it still needs. */
data class PermissionReminder(
    val flowId: String,
    val flowName: String,
    val missing: List<MissingPermission>,
    val autoEnableOnComplete: Boolean,
)

/**
 * State of the step-by-step permission wizard. [remaining] is recomputed after every grant
 * so the user is walked through one permission at a time; [skipped] labels are dropped so a
 * permission the user declined (or an ADB-only one) does not loop forever. [attempted] holds
 * runtime permissions already requested once, so the UI can tell a first-time request apart
 * from a permanent "Don't ask again" denial (where the system dialog no longer appears).
 */
data class PermissionSetup(
    val flowId: String,
    val flowName: String,
    val remaining: List<MissingPermission>,
    val autoEnableOnComplete: Boolean,
    val skipped: Set<String> = emptySet(),
    val attempted: Set<String> = emptySet(),
)

/** Outcome of the wizard: [allGranted] is false when the user skipped a still-missing permission. */
data class PermissionSetupResult(val flowName: String, val allGranted: Boolean)

/**
 * The guided, one-at-a-time permission setup state machine, shared by the Flows list and
 * the flow detail screen (each ViewModel owns an instance and drives it from its own
 * coroutine scope). See [PermissionSetup] for the state semantics.
 */
class PermissionSetupManager(
    private val repository: FlowRepository,
    private val permissionChecker: FlowPermissionChecker,
) {

    private val _setup = MutableStateFlow<PermissionSetup?>(null)
    val setup: StateFlow<PermissionSetup?> = _setup.asStateFlow()

    // Emits when the wizard finishes (all granted, or closed with some skipped-and-still-missing).
    private val _complete = MutableSharedFlow<PermissionSetupResult>(extraBufferCapacity = 1)
    val complete: SharedFlow<PermissionSetupResult> = _complete.asSharedFlow()

    /**
     * Start the wizard for a flow. If nothing is actually missing (a race with the user
     * granting elsewhere), it just enables the flow when [autoEnable] is set.
     */
    suspend fun begin(flowId: String, autoEnable: Boolean) {
        val flow = repository.getById(flowId) ?: return
        val missing = permissionChecker.missingPermissions(flow)
        if (missing.isEmpty()) {
            if (autoEnable) repository.setEnabled(flowId, true)
            return
        }
        _setup.value = PermissionSetup(flowId, flow.name, missing, autoEnable)
    }

    /**
     * Re-check the active setup after the user returns from a grant step and advance to the
     * next still-missing permission — or finish (optionally enabling the flow) when done.
     * Safe to call repeatedly (e.g. on every ON_RESUME); it is a no-op with no active setup.
     */
    suspend fun advance() {
        val setup = _setup.value ?: return
        val flow = repository.getById(setup.flowId) ?: run {
            if (_setup.value === setup) _setup.value = null
            return
        }
        // Returning from a settings page fires both the result callback and ON_RESUME, so two
        // advances can run concurrently. If another has already superseded/finished this setup,
        // stop — otherwise the completion below would emit twice (duplicate snackbar).
        if (_setup.value !== setup) return
        val stillMissing = permissionChecker.missingPermissions(flow)
        val remaining = stillMissing.filter { it.label !in setup.skipped }
        if (remaining.isNotEmpty()) {
            if (_setup.value === setup) _setup.value = setup.copy(remaining = remaining)
            return
        }
        // Claim completion atomically: null the (still-current) setup *before* the suspending
        // setEnabled below, so a concurrent advance sees null and bails at the guard above.
        if (_setup.value !== setup) return
        _setup.value = null
        // Only enable the flow when *nothing* is actually missing — a skipped-but-required
        // permission would otherwise let the engine subscribe a trigger without its permission
        // and crash. Skipped ⇒ report "not fully granted".
        val allGranted = stillMissing.isEmpty()
        if (allGranted && setup.autoEnableOnComplete) {
            repository.setEnabled(setup.flowId, true)
        }
        _complete.emit(PermissionSetupResult(setup.flowName, allGranted))
    }

    /** Record that a runtime permission has been requested at least once in this session. */
    fun markAttempted(permissions: List<String>) {
        val setup = _setup.value ?: return
        _setup.value = setup.copy(attempted = setup.attempted + permissions)
    }

    /** Drop the current permission from the queue (user declined / ADB-only) and move on. */
    suspend fun skip() {
        val setup = _setup.value ?: return
        val current = setup.remaining.firstOrNull() ?: return
        _setup.value = setup.copy(skipped = setup.skipped + current.label)
        advance()
    }

    fun cancel() {
        _setup.value = null
    }
}
