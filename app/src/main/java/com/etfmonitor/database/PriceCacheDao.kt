package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.PriceCache

/**
 * 가격 캐시 DAO
 * ML 예측을 위한 가격 데이터 캐싱 관리
 */
@Dao
interface PriceCacheDao {

    /**
     * 특정 종목들의 특정 날짜 가격 캐시 조회
     */
    @Query("SELECT * FROM price_cache WHERE ticker IN (:tickers) AND date = :date")
    suspend fun getPrices(tickers: List<String>, date: String): List<PriceCache>

    /**
     * 특정 종목의 가격 캐시 조회
     */
    @Query("SELECT * FROM price_cache WHERE ticker = :ticker AND date = :date")
    suspend fun getPrice(ticker: String, date: String): PriceCache?

    /**
     * 날짜 범위 내 모든 가격 캐시 조회
     */
    @Query("SELECT * FROM price_cache WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getPricesByDateRange(startDate: String, endDate: String): List<PriceCache>

    /**
     * 가격 캐시 저장/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<PriceCache>)

    /**
     * 단일 가격 캐시 저장/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(price: PriceCache)

    /**
     * 오래된 캐시 삭제
     * @param cutoff 삭제 기준 시간 (밀리초)
     */
    @Query("DELETE FROM price_cache WHERE updatedAt < :cutoff")
    suspend fun deleteOldCache(cutoff: Long)

    /**
     * 특정 날짜의 캐시 삭제
     */
    @Query("DELETE FROM price_cache WHERE date = :date")
    suspend fun deleteCacheByDate(date: String)

    /**
     * 모든 캐시 삭제
     */
    @Query("DELETE FROM price_cache")
    suspend fun deleteAll()

    /**
     * 캐시된 종목 수 조회
     */
    @Query("SELECT COUNT(DISTINCT ticker) FROM price_cache")
    suspend fun getCachedTickerCount(): Int

    /**
     * 캐시된 날짜 수 조회
     */
    @Query("SELECT COUNT(DISTINCT date) FROM price_cache")
    suspend fun getCachedDateCount(): Int

    /**
     * 특정 날짜의 캐시된 종목 수 조회
     */
    @Query("SELECT COUNT(*) FROM price_cache WHERE date = :date")
    suspend fun getCachedCountByDate(date: String): Int

    /**
     * 5일 후 가격 변화율이 있는 캐시만 조회
     */
    @Query("SELECT * FROM price_cache WHERE priceChange5d IS NOT NULL AND date = :date")
    suspend fun getValidPriceChanges(date: String): List<PriceCache>
}
