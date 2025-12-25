package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.FearGreedIndex
import kotlinx.coroutines.flow.Flow

@Dao
interface FearGreedDao {
    @Query("SELECT * FROM fear_greed_index WHERE market = :market ORDER BY date DESC")
    fun getAllByMarket(market: String): Flow<List<FearGreedIndex>>

    @Query("SELECT * FROM fear_greed_index WHERE market = :market AND date = :date")
    suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex?

    @Query("SELECT * FROM fear_greed_index WHERE market = :market ORDER BY date DESC LIMIT :limit")
    fun getRecentByMarket(market: String, limit: Int): Flow<List<FearGreedIndex>>

    @Query("SELECT * FROM fear_greed_index WHERE market = :market AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<FearGreedIndex>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indices: List<FearGreedIndex>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(index: FearGreedIndex)

    @Query("DELETE FROM fear_greed_index WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    @Query("DELETE FROM fear_greed_index")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM fear_greed_index WHERE market = :market")
    suspend fun getCountByMarket(market: String): Int

    @Query("SELECT MAX(date) FROM fear_greed_index WHERE market = :market")
    suspend fun getLatestDate(market: String): String?

    @Query("SELECT MAX(lastUpdated) FROM fear_greed_index WHERE market = :market")
    suspend fun getLastUpdateTime(market: String): Long?
}
