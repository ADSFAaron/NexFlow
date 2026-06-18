package com.nexflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var flowRepository: FlowRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Default RESULT_CANCELED: if user backs out, widget is not added to home screen
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

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
                                FlowPickerItem(
                                    flow = flow,
                                    onClick = {
                                        lifecycleScope.launch {
                                            saveAndUpdate(appWidgetId, flow)
                                            setResult(
                                                RESULT_OK,
                                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                            )
                                            finish()
                                        }
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

    private suspend fun saveAndUpdate(appWidgetId: Int, flow: Flow) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("${KEY_FLOW_ID}_$appWidgetId", flow.id)
            .putString("${KEY_FLOW_NAME}_$appWidgetId", flow.name)
            .apply()

        val manager = GlanceAppWidgetManager(this)
        val glanceId = manager.getGlanceIdBy(appWidgetId)
        val providerClass = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId).provider.className
        val widget = when {
            providerClass.endsWith("NexFlowSingleWidgetReceiver") -> NexFlowSingleWidget()
            else -> NexFlowWideWidget()
        }
        widget.update(this, glanceId)
    }

    companion object {
        const val PREFS = "nexflow_widget_prefs"
        const val KEY_FLOW_ID = "flow_id"
        const val KEY_FLOW_NAME = "flow_name"
    }
}

@Composable
private fun FlowPickerItem(flow: Flow, onClick: () -> Unit) {
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
