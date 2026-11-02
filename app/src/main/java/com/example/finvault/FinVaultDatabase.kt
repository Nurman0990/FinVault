package com.example.finvault

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =============================================================================
// СУЩНОСТИ (Entities)
// =============================================================================

@Entity(tableName = "transactions_table")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val isIncome: Boolean,
    val title: String,
    val timestamp: Long,
    val currencyCode: String = "KZT"
)

/**
 * Таблица для кэширования курсов валют и крипты
 */
@Entity(tableName = "rates_table")
data class RateEntity(
    @PrimaryKey
    val code: String,
    val name: String,
    val rateInKzt: Double,
    val isCrypto: Boolean,
    val lastUpdated: Long
)

/**
 * Таблица регулярных (автоматических) платежей: подписки, аренда, зарплата
 * intervalDays: 30 (ежемесячно), 7 (еженедельно), 1 (ежедневно)
 * lastProcessedTimestamp: когда платеж был добавлен в транзакции последний раз
 */
@Entity(tableName = "recurring_payments_table")
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Int,
    val isIncome: Boolean,
    val intervalDays: Int = 30,
    val isActive: Boolean = true,
    val nextDueTimestamp: Long,
    val lastProcessedTimestamp: Long = 0L,
    val currencyCode: String = "KZT"
)

// =============================================================================
// ДАО (DAOs)
// =============================================================================

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions_table ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions_table")
    suspend fun clearAllTransactions()
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rates: List<RateEntity>)

    @Query("SELECT * FROM rates_table ORDER BY isCrypto ASC, code ASC")
    fun getAllRates(): Flow<List<RateEntity>>

    @Query("SELECT * FROM rates_table WHERE isCrypto = :isCrypto ORDER BY code ASC")
    fun getRatesByType(isCrypto: Boolean): Flow<List<RateEntity>>

    @Query("SELECT COUNT(*) FROM rates_table")
    suspend fun getRatesCount(): Int
}

@Dao
interface RecurringPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringPayment(payment: RecurringPaymentEntity)

    @Query("SELECT * FROM recurring_payments_table ORDER BY nextDueTimestamp ASC")
    fun getAllRecurringPayments(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments_table WHERE isActive = 1 AND nextDueTimestamp <= :currentTime")
    suspend fun getDuePayments(currentTime: Long): List<RecurringPaymentEntity>

    @Delete
    suspend fun deleteRecurringPayment(payment: RecurringPaymentEntity)
}

// =============================================================================
// БАЗА ДАННЫХ
// =============================================================================

@Database(
    entities = [TransactionEntity::class, RateEntity::class, RecurringPaymentEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun rateDao(): RateDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finvault_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}