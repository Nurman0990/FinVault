package com.example.finvault.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.finvault.AppDatabase
import com.example.finvault.TransactionEntity
import java.util.concurrent.TimeUnit

class RecurringPaymentsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val now = System.currentTimeMillis()
            val duePayments = db.recurringPaymentDao().getDuePayments(now)

            for (payment in duePayments) {
                // Создаем транзакцию из регулярного платежа
                db.transactionDao().insertTransaction(
                    TransactionEntity(
                        amount = payment.amount.toDouble(),
                        isIncome = payment.isIncome,
                        title = "${payment.title} (Автоплатеж)",
                        timestamp = now,
                        currencyCode = payment.currencyCode
                    )
                )

                // Вычисляем следующее время платежа
                val nextDue = now + TimeUnit.DAYS.toMillis(payment.intervalDays.toLong())
                db.recurringPaymentDao().insertRecurringPayment(
                    payment.copy(
                        nextDueTimestamp = nextDue,
                        lastProcessedTimestamp = now
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
