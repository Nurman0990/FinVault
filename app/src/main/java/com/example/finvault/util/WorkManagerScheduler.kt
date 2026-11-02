package com.example.finvault.util

import android.content.Context
import androidx.work.*
import com.example.finvault.worker.EncryptedBackupWorker
import com.example.finvault.worker.RatesSyncWorker
import com.example.finvault.worker.RecurringPaymentsWorker
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    private const val RATES_SYNC_WORK = "FinVaultRatesDailySync"
    private const val RECURRING_PAYMENTS_WORK = "FinVaultRecurringPaymentsSync"
    private const val AUTO_BACKUP_WORK = "FinVaultAutoBackupSync"

    fun scheduleDailyRatesSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dailyWorkRequest = PeriodicWorkRequestBuilder<RatesSyncWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RATES_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
    }

    fun scheduleRecurringPaymentsCheck(context: Context) {
        val recurringWorkRequest = PeriodicWorkRequestBuilder<RecurringPaymentsWorker>(
            12, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECURRING_PAYMENTS_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            recurringWorkRequest
        )
    }

    fun scheduleDailyAutoBackup(context: Context) {
        val backupWorkRequest = PeriodicWorkRequestBuilder<EncryptedBackupWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )
    }

    fun triggerOneTimeSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<RatesSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }
}
