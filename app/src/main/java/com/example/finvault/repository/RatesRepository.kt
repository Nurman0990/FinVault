package com.example.finvault.repository

import com.example.finvault.RateDao
import com.example.finvault.RateEntity
import com.example.finvault.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RatesRepository(private val rateDao: RateDao) {

    val allRates: Flow<List<RateEntity>> = rateDao.getAllRates()

    suspend fun refreshRates(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entities = mutableListOf<RateEntity>()
            val now = System.currentTimeMillis()

            // 1. Загрузка фиатных курсов (относительно USD)
            val fiatResponse = NetworkClient.apiService.getFiatRates()
            val rates = fiatResponse.rates ?: emptyMap()
            val usdToKzt = rates["KZT"] ?: 480.0

            // Фиатные валюты в тенге (KZT)
            val fiatCurrencies = listOf(
                "USD" to "Доллар США",
                "EUR" to "Евро",
                "RUB" to "Российский рубль",
                "GBP" to "Фунт стерлингов",
                "CNY" to "Китайский юань",
                "TRY" to "Турецкая лира",
                "AED" to "Дирхам ОАЭ",
                "KGS" to "Киргизский сом",
                "UZS" to "Узбекский сум"
            )

            for ((code, name) in fiatCurrencies) {
                val rateToUsd = rates[code]
                if (rateToUsd != null && rateToUsd > 0) {
                    val rateInKzt = usdToKzt / rateToUsd
                    entities.add(
                        RateEntity(
                            code = code,
                            name = name,
                            rateInKzt = rateInKzt,
                            isCrypto = false,
                            lastUpdated = now
                        )
                    )
                }
            }

            // Добавим и сам KZT (базовая)
            entities.add(
                RateEntity(
                    code = "KZT",
                    name = "Казахстанский тенге",
                    rateInKzt = 1.0,
                    isCrypto = false,
                    lastUpdated = now
                )
            )

            // 2. Загрузка крипто-курсов
            try {
                val cryptoResponse = NetworkClient.apiService.getCryptoPrices()
                val cryptoMap = listOf(
                    Triple("BTC", "Bitcoin", "bitcoin"),
                    Triple("ETH", "Ethereum", "ethereum"),
                    Triple("BNB", "BNB", "binancecoin"),
                    Triple("SOL", "Solana", "solana"),
                    Triple("XRP", "Ripple", "ripple"),
                    Triple("TON", "Toncoin", "the-open-network"),
                    Triple("DOGE", "Dogecoin", "dogecoin"),
                    Triple("ADA", "Cardano", "cardano")
                )

                for ((code, name, id) in cryptoMap) {
                    val priceInUsd = cryptoResponse[id]?.usd
                    if (priceInUsd != null) {
                        val rateInKzt = priceInUsd * usdToKzt
                        entities.add(
                            RateEntity(
                                code = code,
                                name = name,
                                rateInKzt = rateInKzt,
                                isCrypto = true,
                                lastUpdated = now
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Если крипто API временно недоступен или лимит, сохраняем хотя бы фиат
                e.printStackTrace()
            }

            if (entities.isNotEmpty()) {
                rateDao.insertRates(entities)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
