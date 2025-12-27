package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.PriceCache
import kotlinx.coroutines.flow.Flow

/**
 * ML 예측용 가격 캐시 DAO
 */
@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE ticker = :ticker ORDER BY date DESC")
    fun getPricesByTicker(ticker: String): Flow<List<PriceCache>>

    @Query("SELECT * FROM price_cache WHERE ticker = :ticker AND date = :date")
    suspend fun getPrice(ticker: String, date: String): PriceCache?

    @Query("SELECT * FROM price_cache WHERE ticker = :ticker ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentPrices(ticker: String, limit: Int): List<PriceCache>

    @Query("SELECT * FROM price_cache WHERE date = :date")
    suspend fun getPricesByDate(date: String): List<PriceCache>

    @Query("SELECT DISTINCT ticker FROM price_cache")
    suspend fun getAllTickers(): List<String>

    @Query("SELECT MAX(date) FROM price_cache WHERE ticker = :ticker")
    suspend fun getLatestDate(ticker: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(priceCache: PriceCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<PriceCache>)

    @Query("DELETE FROM price_cache WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    @Query("DELETE FROM price_cache WHERE date < :date")
    suspend fun deleteOldData(date: String)

    @Query("DELETE FROM price_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM price_cache")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(DISTINCT ticker) FROM price_cache")
    suspend fun getTickerCount(): Int
}
