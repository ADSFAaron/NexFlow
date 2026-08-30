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

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexflow.core.automation.model.TriggerType
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.event.ImportEventSource
import com.nexflow.event.NfcEventSource
import com.nexflow.event.NfcReaderMode
import com.nexflow.prefs.OnboardingPrefs
import com.nexflow.prefs.ServiceEnabledPrefs
import com.nexflow.service.FlowExecutionService
import androidx.navigation.compose.rememberNavController
import com.nexflow.ui.navigation.NexFlowNavigationScaffold
import com.nexflow.ui.navigation.NexFlowNavHost
import com.nexflow.ui.onboarding.OnboardingScreen
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: FlowRepository

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        observeReaderModeNeed()
        // Respect the persistent master switch: if the user stopped the automation
        // service, opening the app must not silently restart it.
        if (ServiceEnabledPrefs.get(this)) {
            FlowExecutionService.start(this)
        }
        enableEdgeToEdge()
        setContent {
            NexFlowTheme {
                var onboardingDone by rememberSaveable {
                    mutableStateOf(OnboardingPrefs.isCompleted(this))
                }
                if (!onboardingDone) {
                    OnboardingScreen(
                        onFinished = {
                            OnboardingPrefs.setCompleted(this)
                            onboardingDone = true
                        },
                    )
                } else {
                    val navController = rememberNavController()
                    // NavigationSuiteScaffold renders a bottom bar on phones and a
                    // navigation rail on large screens, and owns the navigation insets.
                    // Each destination's own Scaffold + TopAppBar consumes the top inset.
                    NexFlowNavigationScaffold(navController) {
                        // Keep content clear of a landscape display cutout (notch on the side);
                        // vertical system-bar insets are handled by each screen's own Scaffold.
                        NexFlowNavHost(
                            navController = navController,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                            ),
                        )
                    }
                }
            }
        }
        handleShareIntent(intent)
        handleRunFlowIntent(intent)
    }

    /**
     * Hold the NFC controller in reader mode only while something actually needs it.
     *
     * This used to be an unconditional `enableReaderMode` in `onResume`, which meant that
     * merely having NexFlow open swallowed every tap on the device: reader mode is exclusive
     * and FLAG_READER_SKIP_NDEF_CHECK disables NDEF dispatch outright, so a URL tag opened
     * nothing and a transit card did nothing — even for users with no NFC flow at all.
     *
     * Collection is scoped to RESUMED because reader mode is only legal for a foreground
     * activity, which is also the only time an NFC trigger can fire.
     */
    private fun observeReaderModeNeed() {
        val adapter = nfcAdapter ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    repository.observeEnabled().map { flows ->
                        flows.any { flow -> flow.triggers.any { it.type == TriggerType.NFC_TAG } }
                    },
                    FlowExecutionService.running,
                    NfcReaderMode.scanRequests.map { it > 0 },
                ) { hasNfcFlow, engineRunning, scanning ->
                    // A tag can only start a flow when the engine is actually up, so with the
                    // master switch off there is nothing to gain from claiming the controller.
                    // A scan is exempt: naming a tag is how the first NFC flow gets configured.
                    (hasNfcFlow && engineRunning) || scanning
                }
                    .distinctUntilChanged()
                    .collect { needed ->
                        if (needed) {
                            adapter.enableReaderMode(
                                this@MainActivity,
                                { tag -> dispatchNfcTag(tag) },
                                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                                null,
                            )
                        } else {
                            adapter.disableReaderMode(this@MainActivity)
                        }
                    }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Unconditional, and before onPause returns: the collector above is cancelled by the
        // lifecycle without getting a chance to turn reader mode off itself, and leaving it on
        // would keep NFC hijacked after the app is no longer in front. A no-op when it was
        // never enabled.
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        handleRunFlowIntent(intent)
    }

    private fun handleRunFlowIntent(intent: Intent?) {
        if (intent?.action != FlowExecutionService.ACTION_RUN_FLOW) return
        val flowId = intent.getStringExtra(FlowExecutionService.EXTRA_FLOW_ID) ?: return
        startForegroundService(Intent(this, FlowExecutionService::class.java).apply {
            action = FlowExecutionService.ACTION_RUN_FLOW
            putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
        })
    }

    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "application/json" || intent.type == "text/plain") {
                    // File-based share (EXTRA_STREAM) takes priority over plain text
                    @Suppress("DEPRECATION")
                    val streamUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    if (streamUri != null) {
                        val content = runCatching {
                            contentResolver.openInputStream(streamUri)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull() ?: return
                        ImportEventSource.push(content)
                    } else {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                        if (text.trimStart().startsWith("{")) ImportEventSource.push(text)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val content = runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull() ?: return
                ImportEventSource.push(content)
            }
        }
    }

    private fun dispatchNfcTag(tag: Tag) {
        val tagId = tag.id.joinToString("") { "%02X".format(it) }
        // A scan in progress claims the tag outright. The user is naming a tag, not asking for
        // the flows bound to it to run — and one of those is very likely the flow being edited.
        if (NfcReaderMode.scanRequests.value > 0) {
            NfcReaderMode.emitScannedTag(tagId)
        } else {
            NfcEventSource.emit(tagId)
        }
    }
}
