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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nexflow.MainActivity
import com.nexflow.R
import com.nexflow.service.FlowExecutionService

class NexFlowSingleWidget : GlanceAppWidget() {

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
                // Not yet configured — tap to open app
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(44.dp)
                            .background(GlanceTheme.colors.primary)
                            .cornerRadius(22.dp)
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
                            modifier = GlanceModifier.size(22.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                        )
                    }
                    Text(
                        text = flowName ?: "",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 9.sp,
                        ),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
