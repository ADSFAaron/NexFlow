package com.nexflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartService
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

class NexFlowWideWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        val prefs = context.getSharedPreferences(WidgetConfigureActivity.PREFS, Context.MODE_PRIVATE)
        val flowId = prefs.getString("${WidgetConfigureActivity.KEY_FLOW_ID}_$appWidgetId", null)
        val flowName = prefs.getString("${WidgetConfigureActivity.KEY_FLOW_NAME}_$appWidgetId", null)
        provideContent { Content(context, flowId, flowName) }
    }

    @Composable
    private fun Content(context: Context, flowId: String?, flowName: String?) {
        GlanceTheme {
            if (flowId == null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "點此設定流程",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 12.sp,
                        ),
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = flowName ?: "",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1,
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .background(GlanceTheme.colors.primary)
                            .cornerRadius(20.dp)
                            .clickable(
                                actionStartService(
                                    Intent(context, FlowExecutionService::class.java).apply {
                                        action = FlowExecutionService.ACTION_RUN_FLOW
                                        putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
                                    },
                                    isForegroundService = true,
                                )
                            ),
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
            }
        }
    }
}
