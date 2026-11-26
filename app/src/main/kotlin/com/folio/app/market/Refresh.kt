package com.folio.app.market

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.folio.app.widget.FolioWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = Store.get(applicationContext).refresh(manual = inputData.getBoolean("manual", false))
        updateWidgets(applicationContext)
        return if (summary.retryPending && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

class TradingStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        updateWidgets(applicationContext)
        return Result.success()
    }
}

/** Redraws every widget with the current time and wakes again when an active session ends, without fetching. */
suspend fun updateWidgets(context: Context) {
    Store.get(context).tick()
    FolioWidget().updateAll(context)
    scheduleTradingStatus(context)
}

suspend fun scheduleTradingStatus(context: Context) {
    if (GlanceAppWidgetManager(context).getGlanceIds(FolioWidget::class.java).isEmpty()) return
    val now = System.currentTimeMillis()
    nextTradingStatusChange(Store.get(context).entries.value.mapNotNull { it.quote }, now)?.let { end ->
        WorkManager.getInstance(context).enqueueUniqueWork(
            "folio-status", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TradingStatusWorker>()
                .setInitialDelay(end - now + 5_000, TimeUnit.MILLISECONDS).build(),
        )
    }
}

object Refresh {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "folio-refresh", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(androidx.work.workDataOf("manual" to true))
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        )
    }
    fun schedule(context: Context, minutes: Long) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "folio-periodic", ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("folio-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("folio-refresh")
        WorkManager.getInstance(context).cancelUniqueWork("folio-retry")
        WorkManager.getInstance(context).cancelUniqueWork("folio-status")
    }
    fun retry(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("folio-retry", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>().setInitialDelay(1, TimeUnit.MINUTES)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
    }
}
