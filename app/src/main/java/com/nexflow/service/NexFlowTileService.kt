package com.nexflow.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nexflow.R
import com.nexflow.service.TileConfigureActivity.Companion.KEY_FLOW_ID
import com.nexflow.service.TileConfigureActivity.Companion.KEY_FLOW_NAME
import com.nexflow.service.TileConfigureActivity.Companion.PREFS
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NexFlowTileService : TileService() {

    private val tilePrefs get() = getSharedPreferences(PREFS, MODE_PRIVATE)

    override fun onStartListening() {
        refreshTile()
    }

    override fun onClick() {
        val flowId = tilePrefs.getString(KEY_FLOW_ID, null)
        if (flowId != null) {
            startForegroundService(
                Intent(this, FlowExecutionService::class.java).apply {
                    action = FlowExecutionService.ACTION_RUN_FLOW
                    putExtra(FlowExecutionService.EXTRA_FLOW_ID, flowId)
                }
            )
        } else {
            openConfigureActivity()
        }
    }

    private fun refreshTile() {
        val flowName = tilePrefs.getString(KEY_FLOW_NAME, null)
        qsTile?.apply {
            state = if (flowName != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            label = flowName?.take(20) ?: "NexFlow"
            contentDescription = if (flowName != null) "執行 $flowName" else "點此設定流程"
            icon = Icon.createWithResource(this@NexFlowTileService, R.drawable.ic_notification)
            updateTile()
        }
    }

    // Called after TileConfigureActivity saves and finishes, so the tile label refreshes
    fun onConfigurationChanged() = refreshTile()

    private fun openConfigureActivity() {
        val intent = Intent(this, TileConfigureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
