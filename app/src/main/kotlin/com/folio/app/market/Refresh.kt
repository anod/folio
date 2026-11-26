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
import androidx.glance.appwidget.updateAll
import com.folio.app.widget.FolioWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = Store.get(applicationContext).refresh(manual = inputData.getBoolean("manual", false))
        FolioWidget().updateAll(applicationContext)
        return if (summary.retryPending && runAttemptCount < 3) Result.retry() else Result.success()
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
    }
    fun retry(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("folio-retry", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RefreshWorker>().setInitialDelay(1, TimeUnit.MINUTES)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
    }
}
