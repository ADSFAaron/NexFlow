package com.nexflow.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.ui.common.FlowIcons
import com.nexflow.ui.theme.NexFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
                                    onClick = { iconBitmap ->
                                        lifecycleScope.launch {
                                            saveAndUpdate(appWidgetId, flow, iconBitmap)
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

    private suspend fun saveAndUpdate(appWidgetId: Int, flow: Flow, iconBitmap: Bitmap) {
        // Legacy copy: widgets placed before the Glance-state migration read these on
        // their next full session start.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("${KEY_FLOW_ID}_$appWidgetId", flow.id)
            .putString("${KEY_FLOW_NAME}_$appWidgetId", flow.name)
            .apply()

        // Glance renders RemoteViews and cannot draw Compose ImageVectors, so the flow
        // icon is pre-rendered (white glyph) to a PNG here and loaded by the widget.
        val iconFile = withContext(Dispatchers.IO) {
            val dir = File(filesDir, "widget_icons").apply { mkdirs() }
            File(dir, "icon_$appWidgetId.png").also { file ->
                file.outputStream().use { iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        val manager = GlanceAppWidgetManager(this)
        val glanceId = manager.getGlanceIdBy(appWidgetId)
        // Write through Glance state. update() on an already-running session only
        // *recomposes* provideContent — values captured in provideGlance stay frozen —
        // so the widget must read the configuration via currentState to see this change
        // (the session is usually still alive: it started when the widget was placed,
        // seconds before this configuration screen closes).
        updateAppWidgetState(this, glanceId) { prefs ->
            prefs[PREF_FLOW_ID] = flow.id
            prefs[PREF_FLOW_NAME] = flow.name
            prefs[PREF_ICON_PATH] = iconFile.absolutePath
            prefs[PREF_ICON_COLOR] = flow.iconColor.orEmpty()
            // The path is stable per widget id; the stamp busts the widget's decode
            // cache when the user re-configures to a different flow.
            prefs[PREF_ICON_STAMP] = System.currentTimeMillis()
        }
        val providerClass = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
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

        /** Glance-state keys — the source of truth the widgets read via currentState. */
        val PREF_FLOW_ID = stringPreferencesKey("flow_id")
        val PREF_FLOW_NAME = stringPreferencesKey("flow_name")
        val PREF_ICON_PATH = stringPreferencesKey("icon_path")
        val PREF_ICON_COLOR = stringPreferencesKey("icon_color")
        val PREF_ICON_STAMP = longPreferencesKey("icon_stamp")
    }
}

@Composable
private fun FlowPickerItem(flow: Flow, onClick: (Bitmap) -> Unit) {
    // The vector painter must be created in composition; the actual rasterisation
    // happens on click, off the UI clock.
    val iconPainter = rememberVectorPainter(FlowIcons.vector(flow.icon))
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(renderWidgetIcon(iconPainter, density)) })
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

/** Rasterises the flow's icon as a white glyph — the widget tints the circle behind it. */
private fun renderWidgetIcon(painter: Painter, density: Density): Bitmap {
    val sizePx = with(density) { 48.dp.roundToPx() }
    val imageBitmap = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(imageBitmap),
        Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(Color.White)) }
    }
    return imageBitmap.asAndroidBitmap()
}
