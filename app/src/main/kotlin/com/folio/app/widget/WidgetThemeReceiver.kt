package com.folio.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.folio.app.market.updateWidgets

class WidgetThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) updateWidgetColors(context)
    }
}

fun updateWidgetColors(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "folio-widget-theme", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<WidgetThemeWorker>().build(),
    )
}

class WidgetThemeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        updateWidgets(applicationContext)
        return Result.success()
    }
}
