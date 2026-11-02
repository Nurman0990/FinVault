package com.example.finvault.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.finvault.TransactionEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportTransactionsToCsv(context: Context, transactions: List<TransactionEntity>): Result<File> {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "finvault_transactions_$timestamp.csv")

            val writer = FileWriter(file)
            // UTF-8 BOM для корректного открытия в Excel на русском
            writer.write("\uFEFF")
            // Заголовки таблицы
            writer.write("ID;Дата и время;Категория/Описание;Тип;Сумма;Валюта\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (tx in transactions) {
                val dateStr = dateFormat.format(Date(tx.timestamp))
                val typeStr = if (tx.isIncome) "Доход" else "Расход"
                // Экранирование точки с запятой
                val titleClean = tx.title.replace(";", ",")
                writer.write("${tx.id};$dateStr;$titleClean;$typeStr;${tx.amount};${tx.currencyCode}\n")
            }

            writer.flush()
            writer.close()
            Result.success(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun shareCsvFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Экспорт транзакций FinVault")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Поделиться экспортом транзакций")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
