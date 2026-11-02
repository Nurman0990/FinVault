package com.example.finvault.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.finvault.util.AppPreferencesManager
import com.example.finvault.util.BackupRestoreManager
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Воркер для автоматического создания зашифрованного бэкапа базы данных
 * каждые 24 часа в фоновом режиме (AES-256).
 */
class EncryptedBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            AppPreferencesManager.init(applicationContext)
            val isEnabled = AppPreferencesManager.autoBackupEnabled.value
            if (!isEnabled) {
                return Result.success()
            }

            // Создаем зашифрованный бэкап
            val backupResult = BackupRestoreManager.createEncryptedBackup(applicationContext)
            if (backupResult.isSuccess) {
                val now = System.currentTimeMillis()
                AppPreferencesManager.setLastBackupTimestamp(applicationContext, now)

                // Ротация старых бэкапов: сохраняем последние 7 копий
                val exportDir = File(applicationContext.cacheDir, "exports")
                val backups = exportDir.listFiles()?.filter { it.extension == "finvault" }?.sortedByDescending { it.lastModified() }
                if (backups != null && backups.size > 7) {
                    backups.drop(7).forEach { oldFile ->
                        try { oldFile.delete() } catch (_: Exception) {}
                    }
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
