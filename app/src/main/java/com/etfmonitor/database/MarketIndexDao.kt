package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.MarketIndex
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketIndexDao {
    /**
     * 특정 시장의 모든 데이터 조회 (날짜 역순)
     */
    @Query("SELECT * FROM market_index WHERE market = :market ORDER BY date DESC")
    fun getAllByMarket(market: String): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 특정 날짜 데이터 조회
     */
    @Query("SELECT * FROM market_index WHERE market = :market AND date = :date")
    suspend fun getByMarketAndDate(market: String, date: String): MarketIndex?

    /**
     * 특정 시장의 최근 N개 데이터 조회
     */
    @Query("SELECT * FROM market_index WHERE market = :market ORDER BY date DESC LIMIT :limit")
    fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 기간별 데이터 조회
     */
    @Query("SELECT * FROM market_index WHERE market = :market AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketIndex>>

    /**
     * 특정 시장의 기간별 데이터 조회 (suspend)
     */
    @Query("SELECT * FROM market_index WHERE market = :market AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getByMarketAndDateRangeSuspend(market: String, startDate: String, endDate: String): List<MarketIndex>

    /**
     * 모든 시장의 특정 날짜 데이터 조회
     */
    @Query("SELECT * FROM market_index WHERE date = :date ORDER BY market")
    suspend fun getByDate(date: String): List<MarketIndex>

    /**
     * 데이터 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indices: List<MarketIndex>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(index: MarketIndex)

    /**
     * 특정 시장 데이터 삭제
     */
    @Query("DELETE FROM market_index WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM market_index")
    suspend fun deleteAll()

    /**
     * 특정 시장의 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM market_index WHERE market = :market")
    suspend fun getCountByMarket(market: String): Int

    /**
     * 특정 시장의 최신 날짜
     */
    @Query("SELECT MAX(date) FROM market_index WHERE market = :market")
    suspend fun getLatestDate(market: String): String?

    /**
     * 특정 시장의 최종 업데이트 시간
     */
    @Query("SELECT MAX(lastUpdated) FROM market_index WHERE market = :market")
    suspend fun getLastUpdateTime(market: String): Long?

    /**
     * 최근 N일의 데이터 존재 여부 확인
     */
    @Query("SELECT COUNT(*) FROM market_index WHERE market = :market AND date >= :startDate")
    suspend fun hasDataSince(market: String, startDate: String): Int

    /**
     * 모든 날짜 목록 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT date FROM market_index ORDER BY date DESC")
    suspend fun getAllDates(): List<String>
}
