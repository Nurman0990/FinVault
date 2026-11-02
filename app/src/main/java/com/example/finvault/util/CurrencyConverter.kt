package com.example.finvault.util

import com.example.finvault.RateEntity
import java.util.Locale

/**
 * Универсальный конвертер валют и форматировщик для FinVault.
 * Обеспечивает согласованную работу транзакций, баланса, лимитов и аналитики
 * в мультивалютном режиме.
 */
object CurrencyConverter {

    /**
     * Конвертирует сумму из валюты [fromCode] в валюту [toCode]
     * через курсы в базе данных [rates] (базовая валюта курсов — KZT).
     */
    fun convert(
        amount: Double,
        fromCode: String,
        toCode: String,
        rates: List<RateEntity>
    ): Double {
        if (amount == 0.0) return 0.0
        val from = fromCode.trim().uppercase()
        val to = toCode.trim().uppercase()
        if (from == to) return amount

        // Стоимость 1 единицы fromCode в тенге (KZT)
        val fromRateInKzt: Double = when (from) {
            "KZT" -> 1.0
            else -> rates.firstOrNull { it.code.equals(from, ignoreCase = true) }?.rateInKzt ?: 1.0
        }

        // Стоимость 1 единицы toCode в тенге (KZT)
        val toRateInKzt: Double = when (to) {
            "KZT" -> 1.0
            else -> rates.firstOrNull { it.code.equals(to, ignoreCase = true) }?.rateInKzt ?: 1.0
        }

        val inKzt = amount * fromRateInKzt
        return if (toRateInKzt > 0) inKzt / toRateInKzt else inKzt
    }

    /**
     * Возвращает общепринятый символ валюты по коду.
     */
    fun getSymbol(code: String): String {
        return when (code.trim().uppercase()) {
            "KZT" -> "₸"
            "USD" -> "$"
            "EUR" -> "€"
            "RUB" -> "₽"
            "TRY" -> "₺"
            "AED" -> "AED"
            "GBP" -> "£"
            "CNY" -> "¥"
            else -> code.trim().uppercase()
        }
    }

    private val decimalSymbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    private val intFormat = java.text.DecimalFormat("#,##0", decimalSymbols)
    private val decimalFormat = java.text.DecimalFormat("#,##0.00", decimalSymbols)

    /**
     * Красивое форматирование суммы:
     * - Разделение тысяч пробелами: 49 975
     * - Дробная часть отделяется запятой: 598,56 $
     */
    fun formatAmount(amount: Double, code: String): String {
        val sym = getSymbol(code)
        val isWhole = (amount % 1.0 == 0.0)
        val formattedNumber = if (isWhole) {
            intFormat.format(amount.toLong())
        } else {
            decimalFormat.format(amount)
        }
        return "$formattedNumber $sym"
    }
}
