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
package com.nexflow.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.model.Trigger
import com.nexflow.core.automation.model.TriggerLogic
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.permissions.FlowPermissionChecker
import com.nexflow.permissions.MissingPermission
import com.nexflow.service.FlowEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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

@HiltViewModel
class FlowsViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val flowEngine: FlowEngine,
    private val permissionChecker: FlowPermissionChecker,
) : ViewModel() {

    val flows: StateFlow<List<Flow>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _navigateToFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToFlow: SharedFlow<String> = _navigateToFlow.asSharedFlow()

    private val _permissionReminder = MutableSharedFlow<PermissionReminder>(extraBufferCapacity = 1)
    val permissionReminder: SharedFlow<PermissionReminder> = _permissionReminder.asSharedFlow()

    private val _permissionSetup = MutableStateFlow<PermissionSetup?>(null)
    val permissionSetup: StateFlow<PermissionSetup?> = _permissionSetup.asStateFlow()

    // Emits when the wizard finishes (all granted, or closed with some skipped-and-still-missing).
    private val _setupComplete = MutableSharedFlow<PermissionSetupResult>(extraBufferCapacity = 1)
    val setupComplete: SharedFlow<PermissionSetupResult> = _setupComplete.asSharedFlow()

    // Bumped whenever the screen resumes so the warning set is recomputed: the user may have
    // granted (or revoked) a permission in system Settings while the app was backgrounded.
    private val _permissionRefresh = MutableStateFlow(0)

    /**
     * Ids of flows that are enabled but still missing a required permission — so the user can see
     * at a glance that an "on" flow is not actually able to run. Recomputed on every flow-list
     * change and on each [refreshPermissionWarnings] (screen resume).
     */
    val flowsMissingPermissions: StateFlow<Set<String>> =
        combine(flows, _permissionRefresh) { list, _ ->
            list.filter { it.enabled && permissionChecker.missingPermissions(it).isNotEmpty() }
                .map { it.id }
                .toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun refreshPermissionWarnings() {
        _permissionRefresh.value++
    }

    /** Re-open the permission guidance dialog for a specific flow (tapping the warning chip). */
    fun showMissingPermissions(id: String) {
        viewModelScope.launch { remindIfMissingPermissions(id, autoEnable = false) }
    }

    /**
     * Start the guided, one-at-a-time permission setup for a flow. Called from the reminder
     * dialog's primary button. If nothing is actually missing (a race with the user granting
     * elsewhere), it just enables the flow when [autoEnable] is set.
     */
    fun beginPermissionSetup(id: String, autoEnable: Boolean) {
        viewModelScope.launch {
            val flow = repository.getById(id) ?: return@launch
            val missing = permissionChecker.missingPermissions(flow)
            if (missing.isEmpty()) {
                if (autoEnable) repository.setEnabled(id, true)
                return@launch
            }
            _permissionSetup.value = PermissionSetup(id, flow.name, missing, autoEnable)
        }
    }

    /**
     * Re-check the active setup after the user returns from a grant step and advance to the
     * next still-missing permission — or finish (optionally enabling the flow) when done.
     * Safe to call repeatedly (e.g. on every ON_RESUME); it is a no-op with no active setup.
     */
    fun advancePermissionSetup() {
        val setup = _permissionSetup.value ?: return
        viewModelScope.launch {
            val flow = repository.getById(setup.flowId) ?: run {
                if (_permissionSetup.value === setup) _permissionSetup.value = null
                return@launch
            }
            // Returning from a settings page fires both the result callback and ON_RESUME, so two
            // advances can run concurrently. If another has already superseded/finished this setup,
            // stop — otherwise the completion below would emit twice (duplicate snackbar).
            if (_permissionSetup.value !== setup) return@launch
            val stillMissing = permissionChecker.missingPermissions(flow)
            val remaining = stillMissing.filter { it.label !in setup.skipped }
            if (remaining.isNotEmpty()) {
                if (_permissionSetup.value === setup) _permissionSetup.value = setup.copy(remaining = remaining)
                return@launch
            }
            // Claim completion atomically: null the (still-current) setup *before* the suspending
            // setEnabled below, so a concurrent advance sees null and bails at the guard above.
            if (_permissionSetup.value !== setup) return@launch
            _permissionSetup.value = null
            // Only enable the flow when *nothing* is actually missing — a skipped-but-required
            // permission would otherwise let the engine subscribe a trigger without its permission
            // and crash. Skipped ⇒ report "not fully granted".
            val allGranted = stillMissing.isEmpty()
            if (allGranted && setup.autoEnableOnComplete) {
                repository.setEnabled(setup.flowId, true)
            }
            _setupComplete.emit(PermissionSetupResult(setup.flowName, allGranted))
        }
    }

    /** Record that a runtime permission has been requested at least once in this session. */
    fun markPermissionAttempted(permissions: List<String>) {
        val setup = _permissionSetup.value ?: return
        _permissionSetup.value = setup.copy(attempted = setup.attempted + permissions)
    }

    /** Drop the current permission from the queue (user declined / ADB-only) and move on. */
    fun skipCurrentPermission() {
        val setup = _permissionSetup.value ?: return
        val current = setup.remaining.firstOrNull() ?: return
        _permissionSetup.value = setup.copy(skipped = setup.skipped + current.label)
        advancePermissionSetup()
    }

    fun cancelPermissionSetup() {
        _permissionSetup.value = null
    }

    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            // Block enabling while required permissions are missing — otherwise the engine would
            // immediately subscribe the trigger (e.g. GeofencingClient.addGeofences) and a
            // missing-permission SecurityException would crash the app. Guide the user to grant
            // first; the DB stays disabled so the Switch reverts to off. Disabling is never blocked.
            if (enabled) {
                val flow = repository.getById(id) ?: return@launch
                val missing = permissionChecker.missingPermissions(flow)
                if (missing.isNotEmpty()) {
                    // Enable path: once the guided setup grants everything, turn the flow on.
                    _permissionReminder.emit(PermissionReminder(id, flow.name, missing, autoEnableOnComplete = true))
                    return@launch
                }
            }
            repository.setEnabled(id, enabled)
        }
    }

    fun deleteFlow(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun runFlow(id: String) {
        viewModelScope.launch {
            // If something is missing, guide the user to grant it rather than running a flow
            // that would silently no-op (or throw). Otherwise run it now.
            if (!remindIfMissingPermissions(id, autoEnable = false)) {
                flowEngine.runNow(id)
            }
        }
    }

    /** @return true if a reminder was shown (permissions missing), false if all granted. */
    private suspend fun remindIfMissingPermissions(id: String, autoEnable: Boolean): Boolean {
        val flow = repository.getById(id) ?: return false
        val missing = permissionChecker.missingPermissions(flow)
        if (missing.isEmpty()) return false
        _permissionReminder.emit(PermissionReminder(id, flow.name, missing, autoEnable))
        return true
    }

    fun createFlow(name: String, description: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            repository.save(
                Flow(
                    id = id,
                    schemaVersion = 1,
                    name = name,
                    description = description,
                    author = null,
                    tags = emptyList(),
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                    triggers = listOf(
                        Trigger(
                            id = UUID.randomUUID().toString(),
                            type = TriggerType.MANUAL,
                            config = emptyMap(),
                        ),
                    ),
                    triggerLogic = TriggerLogic.ANY,
                    conditions = emptyList(),
                    actions = emptyList(),
                    variables = emptyList(),
                ),
            )
            _navigateToFlow.emit(id)
        }
    }
}
