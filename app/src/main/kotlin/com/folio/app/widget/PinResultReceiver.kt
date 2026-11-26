package com.folio.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.folio.app.market.Store
import com.folio.app.market.scheduleTradingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val encoded = intent.getStringExtra("config") ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, FolioWidgetReceiver::class.java)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Store.get(context).saveWidgetConfig(id, WidgetConfig.decode(encoded))
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                FolioWidget().update(context, glanceId)
                scheduleTradingStatus(context)
            } finally {
                pending.finish()
            }
        }
    }
}
