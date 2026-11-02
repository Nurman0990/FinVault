package com.example.finvault.util

import android.content.Context
import android.util.Base64
import com.example.finvault.AppDatabase
import com.example.finvault.RecurringPaymentEntity
import com.example.finvault.TransactionEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val transactions: List<TransactionEntity>,
    val recurringPayments: List<RecurringPaymentEntity>
)

object BackupRestoreManager {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    // Фиксированный ключ шифрования для бэкапа FinVault
    private val BACKUP_KEY = "FinVaultKey2026!SecuredSecretKey".toByteArray().copyOf(32)

    suspend fun createEncryptedBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val transactions = db.transactionDao().getAllTransactions().first()
            val recurring = db.recurringPaymentDao().getAllRecurringPayments().first()

            val backupObj = BackupData(
                transactions = transactions,
                recurringPayments = recurring
            )

            val jsonString = Gson().toJson(backupObj)
            val plainBytes = jsonString.toByteArray(Charsets.UTF_8)

            // Генерация случайного IV
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val keySpec = SecretKeySpec(BACKUP_KEY, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainBytes)

            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val backupFile = File(exportDir, "finvault_backup_${System.currentTimeMillis()}.finvault")
            val outputStream = FileOutputStream(backupFile)
            outputStream.write(iv) // первые 16 байт - IV
            outputStream.write(encryptedBytes)
            outputStream.flush()
            outputStream.close()

            Result.success(backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreEncryptedBackup(context: Context, backupFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = FileInputStream(backupFile)
            val iv = ByteArray(16)
            val bytesRead = inputStream.read(iv)
            if (bytesRead != 16) {
                return@withContext Result.failure(IllegalArgumentException("Неверный формат файла бэкапа"))
            }

            val encryptedBytes = inputStream.readBytes()
            inputStream.close()

            val ivSpec = IvParameterSpec(iv)
            val keySpec = SecretKeySpec(BACKUP_KEY, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            val backupData = Gson().fromJson(jsonString, BackupData::class.java)

            val db = AppDatabase.getDatabase(context)
            var count = 0
            for (tx in backupData.transactions) {
                db.transactionDao().insertTransaction(tx)
                count++
            }
            for (rp in backupData.recurringPayments) {
                db.recurringPaymentDao().insertRecurringPayment(rp)
            }

            Result.success(count)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
