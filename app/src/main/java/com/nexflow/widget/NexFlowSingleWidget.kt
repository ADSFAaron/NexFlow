package com.nexflow.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.currentState
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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.service.FlowExecutionService
import com.nexflow.ui.common.FlowIcons

class NexFlowSingleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Legacy fallback only: widgets configured before the Glance-state migration
        // stored their flow in SharedPreferences keyed by appWidgetId.
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        val sp = context.getSharedPreferences(WidgetConfigureActivity.PREFS, Context.MODE_PRIVATE)
        val legacyFlowId = sp.getString("${WidgetConfigureActivity.KEY_FLOW_ID}_$appWidgetId", null)
        val legacyFlowName = sp.getString("${WidgetConfigureActivity.KEY_FLOW_NAME}_$appWidgetId", null)
        provideContent {
            // currentState re-reads on every recomposition, so the running session picks
            // up the configuration the moment WidgetConfigureActivity writes it — values
            // captured outside provideContent would stay frozen for the session lifetime.
            val state = currentState<Preferences>()
            val flowId = state[WidgetConfigureActivity.PREF_FLOW_ID] ?: legacyFlowId
            val flowName = state[WidgetConfigureActivity.PREF_FLOW_NAME] ?: legacyFlowName
            val iconPath = state[WidgetConfigureActivity.PREF_ICON_PATH]
            val iconStamp = state[WidgetConfigureActivity.PREF_ICON_STAMP]
            val iconBitmap = remember(iconPath, iconStamp) {
                iconPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
            }
            val iconColor = FlowIcons.color(
                state[WidgetConfigureActivity.PREF_ICON_COLOR]?.takeIf { it.isNotEmpty() },
            )
            Content(context, flowId, flowName, iconBitmap, iconColor)
        }
    }

    @Composable
    private fun Content(
        context: Context,
        flowId: String?,
        flowName: String?,
        iconBitmap: Bitmap?,
        iconColor: Color?,
    ) {
        GlanceTheme {
            if (flowId == null) {
                // Not yet configured — tap to open app
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            } else {
                // The whole widget is one big run button — at 1×1 a separate inner
                // button would be a needlessly small target.
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(24.dp)
                        .clickable(
                            actionStartService(
                                Intent(context, FlowExecutionService::class.java).apply {
                                    action = FlowExecutionService.ACTION_RUN_FLOW
                                    putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
                                },
                                isForegroundService = true,
                            ),
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The flow's own icon in its user-picked colour (falls back to a
                    // primary play circle for pre-icon configurations).
                    val circle = GlanceModifier.size(44.dp).cornerRadius(22.dp).let {
                        if (iconColor != null) it.background(iconColor)
                        else it.background(GlanceTheme.colors.primary)
                    }
                    Box(modifier = circle, contentAlignment = Alignment.Center) {
                        if (iconBitmap != null) {
                            Image(
                                provider = ImageProvider(iconBitmap),
                                contentDescription = null,
                                modifier = GlanceModifier.size(24.dp),
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.drawable.ic_play),
                                contentDescription = null,
                                modifier = GlanceModifier.size(22.dp),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                            )
                        }
                    }
                    Text(
                        text = flowName ?: "",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
