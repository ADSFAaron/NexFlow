package com.nexflow.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TileConfigureActivity : ComponentActivity() {

    @Inject lateinit var flowRepository: FlowRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NexFlowTheme {
                val flows by flowRepository.observeAll().collectAsState(emptyList())

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.widget_configure_title)) })
                    },
                ) { innerPadding ->
                    if (flows.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.widget_no_flows),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(flows, key = { it.id }) { flow ->
                                TileFlowPickerItem(
                                    flow = flow,
                                    onClick = {
                                        saveAndRefreshTile(flow)
                                        finish()
                                    },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
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

@androidx.compose.runtime.Composable
private fun TileFlowPickerItem(flow: Flow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = flow.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (flow.description.isNotBlank()) {
            Text(
                text = flow.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
