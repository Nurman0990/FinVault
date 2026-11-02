package com.example.finvault.util

/**
 * Локализатор категорий и названий операций.
 * При смене языка приложения автоматически переводит стандартные категории
 * (Groceries <-> Продукты <-> Азық-түлік, Salary <-> Зарплата <-> Жалақы и др.)
 * во всех списках транзакций и на экране аналитики, сохраняя пользовательские
 * уникальные названия неизменными.
 */
object CategoryLocalizer {

    fun localize(title: String, strings: AppStrings): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return strings.expense

        val isAutoPay = trimmed.contains("(Автоплатеж)", ignoreCase = true) ||
                trimmed.contains("(Auto-pay)", ignoreCase = true) ||
                trimmed.contains("(Автотөлем)", ignoreCase = true)

        // Очищаем от суффикса автоплатежа для распознавания категории
        val cleanTitle = trimmed
            .replace("(Автоплатеж)", "", ignoreCase = true)
            .replace("(Auto-pay)", "", ignoreCase = true)
            .replace("(Автотөлем)", "", ignoreCase = true)
            .trim()

        val lower = cleanTitle.lowercase()

        val localizedBase = when {
            // Расходы
            lower.contains("grocer") || lower.contains("продукт") || lower.contains("азық") ->
                strings.defaultExpenseCategories.getOrElse(0) { "Продукты" }

            lower.contains("transport") || lower.contains("транспорт") || lower.contains("көлік") ->
                strings.defaultExpenseCategories.getOrElse(1) { "Транспорт" }

            lower.contains("cafe") || lower.contains("кафе") || lower.contains("дәмхана") || lower.contains("restaur") || lower.contains("ресторан") ->
                strings.defaultExpenseCategories.getOrElse(2) { "Кафе" }

            lower.contains("shopping") || lower.contains("покупк") || lower.contains("сатып") || lower.contains("магазин") ->
                strings.defaultExpenseCategories.getOrElse(3) { "Покупки" }

            lower.contains("housing") || lower.contains("жильё") || lower.contains("жилье") || lower.contains("тұрғын") || lower.contains("rent") || lower.contains("аренд") ->
                strings.defaultExpenseCategories.getOrElse(4) { "Жильё" }

            lower.contains("health") || lower.contains("здоровь") || lower.contains("денсаулық") || lower.contains("медиц") || lower.contains("аптек") ->
                strings.defaultExpenseCategories.getOrElse(5) { "Здоровье" }

            lower.contains("entertain") || lower.contains("развлечен") || lower.contains("ойын") ->
                strings.defaultExpenseCategories.getOrElse(6) { "Развлечения" }

            lower.contains("servic") || lower.contains("связь") || lower.contains("байланыс") ->
                strings.defaultExpenseCategories.getOrElse(7) { "Связь" }

            lower == "трата" || lower == "expense" || lower == "шығыс" ->
                strings.expense

            // Доходы
            lower.contains("salary") || lower.contains("зарплат") || lower.contains("жалақы") ->
                strings.defaultIncomeCategories.getOrElse(0) { "Зарплата" }

            lower.contains("freelance") || lower.contains("фриланс") ->
                strings.defaultIncomeCategories.getOrElse(1) { "Фриланс" }

            lower.contains("transfer") || lower.contains("перевод") || lower.contains("аударым") ->
                strings.defaultIncomeCategories.getOrElse(2) { "Перевод" }

            lower.contains("invest") || lower.contains("инвестиц") || lower.contains("инвестиция") ->
                strings.defaultIncomeCategories.getOrElse(3) { "Инвестиции" }

            lower.contains("gift") || lower.contains("подарок") || lower.contains("сыйлық") ->
                strings.defaultIncomeCategories.getOrElse(4) { "Подарок" }

            lower.contains("cashback") || lower.contains("кэшбэк") ->
                strings.defaultIncomeCategories.getOrElse(5) { "Кэшбэк" }

            lower == "пополнение" || lower == "income" || lower == "кіріс" ->
                strings.income

            // Если пользователь ввёл своё особое название (например "Мама", "Такси", "Кроссовки") — оставляем как есть
            else -> cleanTitle
        }

        return if (isAutoPay) {
            "$localizedBase ${strings.autoPaymentSuffix}"
        } else {
            localizedBase
        }
    }
}
