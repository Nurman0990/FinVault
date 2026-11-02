package com.example.finvault.di

import android.content.Context
import androidx.room.Room
import com.example.finvault.AppDatabase
import com.example.finvault.RateDao
import com.example.finvault.TransactionDao
import com.example.finvault.network.ApiService
import com.example.finvault.repository.RatesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "finvault_database"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideRateDao(database: AppDatabase): RateDao {
        return database.rateDao()
    }

    @Provides
    fun provideRecurringPaymentDao(database: AppDatabase): com.example.finvault.RecurringPaymentDao {
        return database.recurringPaymentDao()
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRatesRepository(rateDao: RateDao): RatesRepository {
        return RatesRepository(rateDao)
    }
}
