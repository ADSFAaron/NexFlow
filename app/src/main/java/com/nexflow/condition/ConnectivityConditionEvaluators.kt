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
package com.nexflow.condition

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.nexflow.core.automation.condition.ConditionEvaluator
import com.nexflow.core.automation.model.Condition
import com.nexflow.core.automation.model.ConditionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * "Only on Wi-Fi", optionally "only on this network".
 * Config: `state` = `CONNECTED` (default) / `DISCONNECTED`, and an optional `ssid`.
 *
 * Matching an SSID needs location permission — Android withholds the name otherwise. When the
 * name cannot be read the condition does not hold: the app cannot confirm the user's network, and
 * a constraint that cannot be confirmed must not let the flow through.
 */
@Singleton
class WifiConnectedConditionEvaluator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConditionEvaluator {

    override val supportedType = ConditionType.WIFI_CONNECTED

    @Suppress("DEPRECATION")
    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        val wantConnected = condition.config["state"]?.trim()?.uppercase() != "DISCONNECTED"
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager?.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!wantConnected) return !onWifi
        if (!onWifi) return false

        val targetSsid = condition.config["ssid"]?.trim().orEmpty()
        if (targetSsid.isEmpty()) return true

        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        val currentSsid = runCatching {
            wifiManager?.connectionInfo?.ssid
                ?.removePrefix("\"")?.removeSuffix("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
        return currentSsid?.equals(targetSsid, ignoreCase = true) == true
    }
}

/**
 * "Only while the car kit is connected".
 * Config: `state` = `CONNECTED` (default) / `DISCONNECTED`, and an optional `device_name`
 * (substring, case-insensitive).
 *
 * Only audio devices (A2DP and hands-free) are visible here — that covers headphones, speakers
 * and car kits, but not arbitrary BLE peripherals. Reading any of it needs the Bluetooth
 * permission; without it nothing is connected as far as this condition can tell.
 */
@Singleton
class BluetoothConnectedConditionEvaluator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConditionEvaluator {

    override val supportedType = ConditionType.BLUETOOTH_CONNECTED

    override suspend fun isSatisfied(condition: Condition, variables: Map<String, String>): Boolean {
        val wantConnected = condition.config["state"]?.trim()?.uppercase() != "DISCONNECTED"
        val targetName = condition.config["device_name"]?.trim().orEmpty()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

        val connected = when {
            adapter == null || !adapter.isEnabled -> false
            targetName.isEmpty() -> adapter.anyAudioDeviceConnected()
            else -> adapter.connectedAudioDeviceNames()
                .any { it.contains(targetName, ignoreCase = true) }
        }
        return connected == wantConnected
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.anyAudioDeviceConnected(): Boolean = AUDIO_PROFILES.any { profile ->
        runCatching { getProfileConnectionState(profile) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED) ==
            BluetoothProfile.STATE_CONNECTED
    }

    private suspend fun BluetoothAdapter.connectedAudioDeviceNames(): List<String> =
        AUDIO_PROFILES.flatMap { profile ->
            withTimeoutOrNull(PROXY_TIMEOUT_MS) { deviceNames(profile) } ?: emptyList()
        }

    /**
     * Device names on one profile. The proxy is delivered asynchronously and is a system-wide
     * resource, so it is closed again as soon as the names have been read; a proxy that never
     * arrives is bounded by [PROXY_TIMEOUT_MS] at the call site rather than hanging the run.
     */
    @SuppressLint("MissingPermission")
    private suspend fun BluetoothAdapter.deviceNames(profile: Int): List<String> =
        suspendCancellableCoroutine { continuation ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(connectedProfile: Int, proxy: BluetoothProfile) {
                    val names = runCatching {
                        proxy.connectedDevices.mapNotNull { device -> device.name }
                    }.getOrDefault(emptyList())
                    runCatching { closeProfileProxy(connectedProfile, proxy) }
                    if (continuation.isActive) continuation.resume(names)
                }

                override fun onServiceDisconnected(disconnectedProfile: Int) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
            val requested = runCatching {
                getProfileProxy(context, listener, profile)
            }.getOrDefault(false)
            if (!requested && continuation.isActive) continuation.resume(emptyList())
        }

    private companion object {
        val AUDIO_PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

        /** A profile proxy normally binds in a few ms; never let a stuck one hold up a flow. */
        const val PROXY_TIMEOUT_MS = 1_500L
    }
}
