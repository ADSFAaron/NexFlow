package com.nexflow.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.ColorFilter
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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

    // Exact, not Responsive: the layout is a grid whose column and row counts come out of the
    // real size, and no set of breakpoint sizes can tell a three-column widget from a four-column
    // one. Exact costs a re-provide on each resize, which is when the grid has to reflow anyway.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent { Content(context, appWidgetId) }
    }

    @Composable
    private fun Content(context: Context, appWidgetId: Int) {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val enabled = prefs[KEY_ENABLED] ?: 0
        val total = prefs[KEY_TOTAL] ?: 0
        val flows = decodeFlows(prefs[KEY_RECENT_FLOWS])
        val manual = prefs[KEY_PINNED_IDS] != null
        // The launcher's own "widget settings" entry is a long-press away and not every launcher
        // has one, so the widget carries its own way into the same screen.
        val configure = actionStartActivity(
            Intent(context, OverviewWidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // One task per widget id: without distinct data, PendingIntent treats two
                // widgets' intents as the same one and both open the first widget's settings.
                .setData("nexflow://widget/$appWidgetId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )

        val size = LocalSize.current
        // The header is the first thing to go: on a one-row widget every dp belongs to the
        // flows, and "NexFlow" over two cards is the launcher telling the user what they can
        // already see. It comes back as soon as there is room for it.
        val showHeader = size.height >= HEADER_MIN_HEIGHT
        val outerPadding = if (showHeader) 16.dp else 10.dp
        val contentWidth = size.width - outerPadding * 2
        // Columns out of how wide a card is allowed to get, not out of fixed screen widths. A
        // phone's home screen is around 400dp across, so a "four columns past 430dp" rule was a
        // layout nobody could reach, while "two columns past 170dp" handed out 69dp cards that
        // could not hold a name. Adding a column only when another card actually fits keeps every
        // width the user can drag to a width that reads, on a phone and on a tablet alike.
        val columns = ((contentWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP)).toInt()
            .coerceIn(1, MAX_COLUMNS)
        val contentHeight = size.height - outerPadding * 2 - if (showHeader) HEADER_HEIGHT else 0.dp
        // Whole rows only, then stretched to fill what is left over. The launcher clips a widget
        // that overflows — it does not scale it — so a row that half fits is a row cut in half,
        // and a fixed card height in a tall widget is a band of empty background at the bottom.
        val rows = ((contentHeight + CARD_GAP) / (IDEAL_CARD_HEIGHT + CARD_GAP)).toInt().coerceAtLeast(1)
        val cardHeight = ((contentHeight - CARD_GAP * (rows - 1)) / rows)
            .coerceIn(MIN_CARD_HEIGHT, MAX_CARD_HEIGHT)

        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(24.dp)
                    .padding(outerPadding),
            ) {
                if (showHeader) {
                    // Tapping the header opens the app.
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = HEADER_GAP)
                            .clickable(actionStartActivity<MainActivity>()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "NexFlow",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        // "2/22" on its own said nothing to anyone who had not written it. The
                        // label is what makes the pair of numbers a fact about the flows.
                        Text(
                            text = context.getString(R.string.widget_enabled_count, enabled, total),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSecondaryContainer,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.secondaryContainer)
                                .cornerRadius(10.dp)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Box(
                            modifier = GlanceModifier
                                .size(HEADER_ICON_TOUCH)
                                .cornerRadius(HEADER_ICON_TOUCH / 2)
                                .clickable(configure),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_edit),
                                contentDescription = context.getString(R.string.widget_overview_edit),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                                modifier = GlanceModifier.size(18.dp),
                            )
                        }
                    }
                }

                if (flows.isEmpty()) {
                    // A hand-picked widget with nothing in it (its flows were all deleted, or none
                    // were picked) says so and leads back to the picker, not into the app.
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(if (manual) configure else actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = context.getString(
                                if (manual) R.string.widget_overview_none_picked else R.string.widget_no_runs,
                            ),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        )
                    }
                } else {
                    // A plain Column, not a LazyColumn: the lazy one is a RemoteViews collection
                    // backed by a ListView, whose adapter reloads asynchronously — so every
                    // resize showed an empty widget for a moment while the launcher waited for
                    // the rows, and left a scrollbar down the side. The grid already works out
                    // exactly what fits, so there is nothing to scroll and everything can travel
                    // in the one RemoteViews and land with the resize.
                    val chunks = flows.take(rows * columns).chunked(columns)
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        chunks.forEachIndexed { rowIndex, chunk ->
                            if (rowIndex > 0) Spacer(modifier = GlanceModifier.height(CARD_GAP))
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                chunk.forEachIndexed { index, flow ->
                                    if (index > 0) Spacer(modifier = GlanceModifier.width(CARD_GAP))
                                    FlowCard(context, flow, cardHeight, GlanceModifier.defaultWeight())
                                }
                                // Keeps a lone trailing card at one column's width instead of
                                // letting it stretch across the row and break the grid.
                                repeat(columns - chunk.size) {
                                    Spacer(modifier = GlanceModifier.width(CARD_GAP))
                                    Spacer(modifier = GlanceModifier.defaultWeight())
                                }
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
     *
     * [height] is what the grid worked out this row can have, and it decides how much of the card
     * there is room to draw: a short widget gets the icon beside the name on one line, a taller
     * one stacks them and adds what triggers the flow.
     */
    @Composable
    private fun FlowCard(
        context: Context,
        flow: WidgetFlow,
        height: Dp,
        modifier: GlanceModifier,
    ) {
        val compact = height < COMPACT_CARD_HEIGHT
        val card = modifier
            .height(height)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(if (compact) 16.dp else 20.dp)
            .clickable(
                actionStartService(
                    Intent(context, FlowExecutionService::class.java).apply {
                        action = FlowExecutionService.ACTION_RUN_FLOW
                        putExtra(FlowExecutionService.EXTRA_FLOW_ID, flow.id)
                    },
                    isForegroundService = true,
                ),
            )

        if (compact) {
            // Sized off the row so the icon and the card's own padding always add up to less
            // than the height the grid handed this card.
            val badge = (height - 16.dp).coerceIn(16.dp, 28.dp)
            Row(
                modifier = card.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(context, flow, badge)
                Spacer(modifier = GlanceModifier.width(10.dp))
                Text(
                    text = flow.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        } else {
            // Each line only goes in if the row is tall enough to hold it; otherwise the card
            // would draw text the launcher then clips off at the bottom edge.
            val nameLines = if (height >= TWO_LINE_MIN_HEIGHT) 2 else 1
            // Which flow this is beats what triggers it, so the second line of the name is bought
            // before the subtitle is: a card tall enough for one but not both drops the subtitle.
            val subtitleFloor = if (nameLines == 2) SUBTITLE_WITH_TWO_LINES else SUBTITLE_MIN_HEIGHT
            val showSubtitle = height >= subtitleFloor && flow.subtitle.isNotEmpty()
            Column(modifier = card.padding(12.dp)) {
                Badge(context, flow, BADGE_SIZE)
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = flow.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = nameLines,
                )
                if (showSubtitle) {
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
    }

    @Composable
    private fun Badge(context: Context, flow: WidgetFlow, size: Dp) {
        Image(
            provider = ImageProvider(
                FlowShortcutIcon.renderBadge(context, flow.icon, flow.color, size.value),
            ),
            // The card's own name follows immediately; announcing the icon as well would
            // read every flow out twice.
            contentDescription = null,
            modifier = GlanceModifier.size(size),
        )
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
        /** The cards this widget draws — whichever [pickFlows] chose, despite the key's name. */
        val KEY_RECENT_FLOWS = stringPreferencesKey("recent_flows")

        /**
         * The flows the user picked for this widget, as a JSON list of ids in display order.
         * Absent means the widget fills itself from recent runs; present but empty means the
         * user asked for hand-picked flows and has none left.
         */
        val KEY_PINNED_IDS = stringPreferencesKey("pinned_flow_ids")

        private val CARD_GAP = 10.dp

        /** Narrowest a card may be before the grid drops back to fewer columns. */
        private val MIN_CARD_WIDTH = 92.dp

        /** Four columns needs roughly a 600dp screen — a tablet or an unfolded foldable. */
        private const val MAX_COLUMNS = 4

        /** Height a card is given when the widget has room for it to be comfortable. */
        private val IDEAL_CARD_HEIGHT = 104.dp
        private val MIN_CARD_HEIGHT = 28.dp
        private val MAX_CARD_HEIGHT = 132.dp

        /** Below this a card puts its icon beside the name instead of above it. */
        private val COMPACT_CARD_HEIGHT = 84.dp
        private val SUBTITLE_MIN_HEIGHT = 98.dp
        private val SUBTITLE_WITH_TWO_LINES = 119.dp
        private val TWO_LINE_MIN_HEIGHT = 102.dp

        /** Below this the header is dropped and the whole widget is flows. */
        private val HEADER_MIN_HEIGHT = 150.dp
        private val HEADER_GAP = 12.dp

        /** Header row height plus [HEADER_GAP] — what the grid has to budget for it. */
        private val HEADER_HEIGHT = 34.dp

        /** Tap target around the header's edit icon; the icon itself is smaller. */
        private val HEADER_ICON_TOUCH = 28.dp

        /** Badge diameter on a stacked card; also the size its bitmap is rasterised at. */
        private val BADGE_SIZE = 32.dp

        /**
         * How many flows to keep in widget state, and how many the user can pick. Not how many are
         * shown — that is whatever the grid works out the widget's current size fits.
         */
        const val MAX_FLOWS = 12

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

        internal fun decodePinnedIds(encoded: String?): List<String>? =
            encoded?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()) }

        internal fun encodePinnedIds(ids: List<String>): String = json.encodeToString(ids)

        /**
         * What a widget shows. With no pick ([pinnedIds] null) it is the recently run flows,
         * topped up with the rest so a large widget is not half empty. With a pick it is exactly
         * those flows in the order chosen, minus any since deleted — never topped up, because a
         * flow the user left out showing up anyway is the thing picking was meant to stop.
         */
        internal fun pickFlows(pinnedIds: List<String>?, recent: List<Flow>, all: List<Flow>): List<Flow> {
            if (pinnedIds == null) return (recent + all).distinctBy { it.id }.take(MAX_FLOWS)
            val byId = all.associateBy { it.id }
            return pinnedIds.distinct().mapNotNull { byId[it] }.take(MAX_FLOWS)
        }

        private fun encodeFlows(context: Context, flows: List<Flow>): String =
            // JSON, not the old "id|name" join: a flow named "Home, work" or "A|B" used to split
            // into entries that pointed at no flow at all.
            json.encodeToString(
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

        /** Re-fills every overview widget, each according to its own pick. */
        suspend fun updateFlows(context: Context, recent: List<Flow>, all: List<Flow>) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(NexFlowWidget::class.java).forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        val pinned = decodePinnedIds(this[KEY_PINNED_IDS])
                        this[KEY_RECENT_FLOWS] = encodeFlows(context, pickFlows(pinned, recent, all))
                    }
                }
                NexFlowWidget().update(context, id)
            }
        }

        /**
         * Saves one widget's pick — null [pinnedIds] for automatic — and redraws it at once,
         * rather than waiting for the engine's next sync, which does not run while it is off.
         */
        suspend fun configure(
            context: Context,
            id: GlanceId,
            pinnedIds: List<String>?,
            recent: List<Flow>,
            all: List<Flow>,
        ) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    if (pinnedIds == null) remove(KEY_PINNED_IDS) else this[KEY_PINNED_IDS] = encodePinnedIds(pinnedIds)
                    this[KEY_RECENT_FLOWS] = encodeFlows(context, pickFlows(pinnedIds, recent, all))
                    // The header counts are otherwise written only by the engine; a widget
                    // configured while it is off would show "0 of 0" until it next starts.
                    this[KEY_ENABLED] = all.count { it.enabled }
                    this[KEY_TOTAL] = all.size
                }
            }
            NexFlowWidget().update(context, id)
        }
    }
}
