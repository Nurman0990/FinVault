package com.example.finvault.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// Ответ от Open Exchange Rates / exchangerate-api для фиатных валют
// https://open.er-api.com/v6/latest/USD
data class FiatRatesResponse(
    @SerializedName("result") val result: String?,
    @SerializedName("rates") val rates: Map<String, Double>?
)

// Ответ от CoinGecko для криптовалют
// https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,binancecoin,solana,ripple,cardano,dogecoin,toncoin&vs_currencies=usd
data class CryptoPrice(
    @SerializedName("usd") val usd: Double?
)

interface ApiService {
    @GET("https://open.er-api.com/v6/latest/USD")
    suspend fun getFiatRates(): FiatRatesResponse

    @GET("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,binancecoin,solana,ripple,cardano,dogecoin,the-open-network&vs_currencies=usd")
    suspend fun getCryptoPrices(): Map<String, CryptoPrice>
}

object NetworkClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://open.er-api.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
