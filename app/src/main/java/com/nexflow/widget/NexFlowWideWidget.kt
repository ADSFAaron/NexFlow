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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.service.FlowExecutionService
import com.nexflow.ui.common.FlowIcons

class NexFlowWideWidget : GlanceAppWidget() {

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
            // Pre-rendered at configuration time; keyed on the stamp so re-configuring
            // the same widget to another flow refreshes the decoded bitmap.
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
                        text = context.getString(R.string.widget_not_configured),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                        ),
                    )
                }
            } else {
                // The whole widget is the touch target; the play circle is a visual cue.
                Row(
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The flow's own icon in its user-picked colour — same identity as
                    // the in-app list. Falls back to a primary play circle for widgets
                    // configured before icons were stored.
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
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = flowName ?: "",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 2,
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    // Small run cue at the trailing edge; the whole widget is the button.
                    Image(
                        provider = ImageProvider(R.drawable.ic_play),
                        contentDescription = null,
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                }
            }
        }
    }
}
