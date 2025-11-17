package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.MarketOscillatorData
import kotlinx.coroutines.flow.Flow

/**
 * 시장 과매수/과매도 데이터 DAO
 */
@Dao
interface MarketOscillatorDao {

    /**
     * 특정 시장의 모든 데이터 조회 (날짜 내림차순)
     */
    @Query("SELECT * FROM market_oscillator WHERE market = :market ORDER BY date DESC")
    fun getMarketData(market: String): Flow<List<MarketOscillatorData>>

    /**
     * 특정 시장의 최근 N일 데이터 조회
     */
    @Query("SELECT * FROM market_oscillator WHERE market = :market ORDER BY date DESC LIMIT :limit")
    fun getRecentData(market: String, limit: Int): Flow<List<MarketOscillatorData>>

    /**
     * 특정 시장의 특정 기간 데이터 조회
     */
    @Query("SELECT * FROM market_oscillator WHERE market = :market AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getDataByDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketOscillatorData>>

    /**
     * 특정 시장의 최신 데이터 1개 조회
     */
    @Query("SELECT * FROM market_oscillator WHERE market = :market ORDER BY date DESC LIMIT 1")
    suspend fun getLatestData(market: String): MarketOscillatorData?

    /**
     * 특정 시장의 데이터 개수 조회
     */
    @Query("SELECT COUNT(*) FROM market_oscillator WHERE market = :market")
    suspend fun getDataCount(market: String): Int

    /**
     * 데이터 삽입 또는 업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<MarketOscillatorData>)

    /**
     * 특정 시장의 모든 데이터 삭제
     */
    @Query("DELETE FROM market_oscillator WHERE market = :market")
    suspend fun deleteMarketData(market: String)

    /**
     * 특정 시장의 오래된 데이터 삭제 (최근 N일 데이터만 유지)
     */
    @Query("""
        DELETE FROM market_oscillator
        WHERE market = :market
        AND date NOT IN (
            SELECT date FROM market_oscillator
            WHERE market = :market
            ORDER BY date DESC
            LIMIT :keepDays
        )
    """)
    suspend fun deleteOldData(market: String, keepDays: Int)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM market_oscillator")
    suspend fun deleteAll()
}
