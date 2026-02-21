package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.MarketDeposit
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketDepositDao {
    @Query("SELECT * FROM market_deposits ORDER BY date DESC LIMIT 730")
    fun getAllDeposits(): Flow<List<MarketDeposit>>

    @Query("SELECT * FROM market_deposits WHERE date = :date")
    suspend fun getDepositByDate(date: String): MarketDeposit?

    @Query("SELECT * FROM market_deposits ORDER BY date DESC LIMIT :limit")
    fun getRecentDeposits(limit: Int): Flow<List<MarketDeposit>>

    @Query("SELECT * FROM market_deposits WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>>

    @Query("SELECT * FROM market_deposits WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getByDateRangeSuspend(startDate: String, endDate: String): List<MarketDeposit>

    @Query("SELECT * FROM market_deposits ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDeposit(): MarketDeposit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deposits: List<MarketDeposit>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deposit: MarketDeposit)

    @Query("DELETE FROM market_deposits")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM market_deposits")
    suspend fun getCount(): Int

    @Query("SELECT MAX(lastUpdated) FROM market_deposits")
    suspend fun getLastUpdateTime(): Long?
}
