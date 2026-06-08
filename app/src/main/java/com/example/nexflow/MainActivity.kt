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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexflow.event.ImportEventSource
import com.nexflow.event.NfcEventSource
import com.nexflow.service.FlowExecutionService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.nexflow.ui.navigation.NexFlowBottomBar
import com.nexflow.ui.navigation.NexFlowNavHost
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        FlowExecutionService.start(this)
        enableEdgeToEdge()
        setContent {
            NexFlowTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { NexFlowBottomBar(navController) },
                ) { innerPadding ->
                    // Each screen's own Scaffold + TopAppBar consumes the top inset.
                    // We only pass bottom padding here so the NavBar is avoided.
                    NexFlowNavHost(
                        navController = navController,
                        modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                    )
                }
            }
        }
        handleNfcIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> dispatchNfcTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
        handleShareIntent(intent)
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

    private fun handleNfcIntent(intent: Intent?) {
        val tag = intent?.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        dispatchNfcTag(tag)
    }

    private fun dispatchNfcTag(tag: Tag) {
        val tagId = tag.id.joinToString("") { "%02X".format(it) }
        NfcEventSource.emit(tagId)
    }
}
