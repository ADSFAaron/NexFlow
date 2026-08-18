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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import com.nexflow.shortcut.FlowShortcutIcon
import com.nexflow.ui.flows.detail.config.info
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        val recentFlows = decodeFlows(prefs[KEY_RECENT_FLOWS])

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
                    // Two cards per row, built as a LazyColumn of pairs rather than with
                    // LazyVerticalGrid: the grid is still @ExperimentalGlanceApi in 1.1.1, and
                    // its GridCells.Adaptive is @RequiresApi(31) while minSdk here is 30. Rows of
                    // pairs give the same grid and the same scrolling out of stable API.
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(recentFlows.chunked(COLUMNS)) { pair ->
                            Column {
                                Row(modifier = GlanceModifier.fillMaxWidth()) {
                                    pair.forEachIndexed { index, flow ->
                                        if (index > 0) Spacer(modifier = GlanceModifier.width(CARD_GAP))
                                        FlowCard(context, flow, GlanceModifier.defaultWeight())
                                    }
                                    // Keeps a lone trailing card at one column's width instead of
                                    // letting it stretch across the row and break the grid.
                                    repeat(COLUMNS - pair.size) {
                                        Spacer(modifier = GlanceModifier.width(CARD_GAP))
                                        Spacer(modifier = GlanceModifier.defaultWeight())
                                    }
                                }
                                Spacer(modifier = GlanceModifier.height(CARD_GAP))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * One flow as a card: its icon on its own color, its name, and what starts it. Tapping
     * anywhere on the card runs the flow.
     */
    @Composable
    private fun FlowCard(context: Context, flow: WidgetFlow, modifier: GlanceModifier) {
        Column(
            modifier = modifier
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(20.dp)
                .clickable(
                    actionStartService(
                        Intent(context, FlowExecutionService::class.java).apply {
                            action = FlowExecutionService.ACTION_RUN_FLOW
                            putExtra(FlowExecutionService.EXTRA_FLOW_ID, flow.id)
                        },
                        isForegroundService = true,
                    ),
                )
                .padding(10.dp),
        ) {
            Image(
                provider = ImageProvider(
                    FlowShortcutIcon.renderBadge(context, flow.icon, flow.color, BADGE_DP),
                ),
                // The card's own name follows immediately; announcing the icon as well would
                // read every flow out twice.
                contentDescription = null,
                modifier = GlanceModifier.size(BADGE_DP.dp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = flow.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            if (flow.subtitle.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = flow.subtitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    /**
     * What one card needs to draw itself. Held in widget state rather than read from the
     * database, because [provideGlance] can be called by the launcher at any time, including
     * when nothing of the app is running.
     */
    @Serializable
    data class WidgetFlow(
        val id: String,
        val name: String,
        val icon: String? = null,
        val color: String? = null,
        /** What starts this flow, already localized — the widget cannot resolve a trigger type. */
        val subtitle: String = "",
    )

    companion object {
        val KEY_ENABLED = intPreferencesKey("enabled_count")
        val KEY_TOTAL = intPreferencesKey("total_count")
        val KEY_RECENT_FLOWS = stringPreferencesKey("recent_flows")

        private const val COLUMNS = 2
        private val CARD_GAP = 8.dp

        /** Badge diameter; also the size its bitmap is rasterised at. */
        private const val BADGE_DP = 32f

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reads the stored flows, tolerating the `"id|name,id|name"` string written by versions
         * before the card layout. Without that fallback every widget already on a home screen
         * would go blank until whatever made it update happened to happen again.
         */
        internal fun decodeFlows(encoded: String?): List<WidgetFlow> {
            val raw = encoded?.takeIf { it.isNotBlank() } ?: return emptyList()
            runCatching { json.decodeFromString<List<WidgetFlow>>(raw) }
                .onSuccess { return it }
            return raw.split(",")
                .filter { it.contains("|") }
                .map { entry ->
                    val pipe = entry.indexOf("|")
                    WidgetFlow(id = entry.substring(0, pipe), name = entry.substring(pipe + 1))
                }
        }

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
            // JSON, not the old "id|name" join: a flow named "Home, work" or "A|B" used to split
            // into entries that pointed at no flow at all.
            val encoded = json.encodeToString(
                flows.map { flow ->
                    WidgetFlow(
                        id = flow.id,
                        name = flow.name,
                        icon = flow.icon,
                        color = flow.iconColor,
                        subtitle = flow.triggers.firstOrNull()?.type?.info(context)?.label.orEmpty(),
                    )
                },
            )
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
