package com.nexflow.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.common.FlowPickerPage
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TileConfigureActivity : ComponentActivity() {

    @Inject lateinit var flowRepository: FlowRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars like MainActivity, so the page colour
        // reaches the screen edges; Android 15+ forces this, but on 12-14 the bars stay black.
        enableEdgeToEdge()
        val current = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FLOW_ID, null)

        setContent {
            NexFlowTheme {
                val flows by flowRepository.observeAll().collectAsState(emptyList())
                FlowPickerPage(
                    flows = flows,
                    selectedId = current,
                    onPick = { flow ->
                        saveAndRefreshTile(flow)
                        finish()
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    private fun saveAndRefreshTile(flow: Flow) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FLOW_ID, flow.id)
            .putString(KEY_FLOW_NAME, flow.name)
            .apply()
        // Ask the system to call onStartListening again so the tile label updates immediately
        TileService.requestListeningState(
            this,
            ComponentName(this, NexFlowTileService::class.java),
        )
    }

    companion object {
        const val PREFS = "nexflow_tile_prefs"
        const val KEY_FLOW_ID = "tile_flow_id"
        const val KEY_FLOW_NAME = "tile_flow_name"
    }
}
