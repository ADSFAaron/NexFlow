package com.nexflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.core.automation.model.Flow
import com.nexflow.service.FlowExecutionService

class NexFlowWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(context: Context) {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val enabled = prefs[KEY_ENABLED] ?: 0
        val total = prefs[KEY_TOTAL] ?: 0
        // Encoded as "id1|name1,id2|name2,..."
        val recentFlows = prefs[KEY_RECENT_FLOWS]
            ?.split(",")
            ?.filter { it.contains("|") }
            ?.map { entry ->
                val pipe = entry.indexOf("|")
                entry.substring(0, pipe) to entry.substring(pipe + 1)
            }
            ?: emptyList()

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(24.dp)
                    .padding(16.dp),
            ) {
                // Header row — tapping it opens the app.
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "NexFlow",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        text = "$enabled/$total",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }

                if (recentFlows.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = context.getString(R.string.widget_no_runs),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        )
                    }
                } else {
                    // Scrollable list: rows keep their full 52dp height at every widget size
                    // instead of being squeezed to fit, and older entries stay reachable.
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        itemsIndexed(recentFlows) { _, entry ->
                            val (flowId, flowName) = entry
                            Column {
                                // One pill per flow; the whole pill is the touch target.
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .background(GlanceTheme.colors.secondaryContainer)
                                        .cornerRadius(16.dp)
                                        .clickable(
                                            actionStartService(
                                                Intent(context, FlowExecutionService::class.java).apply {
                                                    action = FlowExecutionService.ACTION_RUN_FLOW
                                                    putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
                                                },
                                                isForegroundService = true,
                                            ),
                                        )
                                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = flowName,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSecondaryContainer,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        modifier = GlanceModifier.defaultWeight(),
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = GlanceModifier.width(10.dp))
                                    Box(
                                        modifier = GlanceModifier
                                            .size(40.dp)
                                            .background(GlanceTheme.colors.primary)
                                            .cornerRadius(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            provider = ImageProvider(R.drawable.ic_play),
                                            contentDescription = null,
                                            modifier = GlanceModifier.size(20.dp),
                                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                                        )
                                    }
                                }
                                Spacer(modifier = GlanceModifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val KEY_ENABLED = intPreferencesKey("enabled_count")
        val KEY_TOTAL = intPreferencesKey("total_count")
        val KEY_RECENT_FLOWS = stringPreferencesKey("recent_flows")

        suspend fun updateCounts(context: Context, enabled: Int, total: Int) {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            manager.getGlanceIds(NexFlowWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_ENABLED] = enabled
                        this[KEY_TOTAL] = total
                    }
                }
                NexFlowWidget().update(context, id)
            }
        }

        suspend fun updateRecentFlows(context: Context, flows: List<Flow>) {
            val encoded = flows.joinToString(",") { "${it.id}|${it.name}" }
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            manager.getGlanceIds(NexFlowWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_RECENT_FLOWS] = encoded
                    }
                }
                NexFlowWidget().update(context, id)
            }
        }
    }
}
