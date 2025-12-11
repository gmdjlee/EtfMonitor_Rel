package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.database.entities.StockIndicatorAIResult
import kotlinx.coroutines.flow.Flow

/**
 * 종목-지표 상관관계 AI 분석 결과 DAO
 */
@Dao
interface StockIndicatorAIResultDao {

    /**
     * 특정 종목의 모든 AI 분석 결과 조회 (날짜 내림차순)
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE ticker = :ticker ORDER BY createdAt DESC")
    fun getAllByTicker(ticker: String): Flow<List<StockIndicatorAIResult>>

    /**
     * 특정 종목의 최근 N개 결과 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE ticker = :ticker ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByTicker(ticker: String, limit: Int): Flow<List<StockIndicatorAIResult>>

    /**
     * 특정 종목의 특정 날짜 AI 분석 결과 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE ticker = :ticker AND analysisDate = :date ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByTickerAndDate(ticker: String, date: String): StockIndicatorAIResult?

    /**
     * ID로 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE id = :id")
    suspend fun getById(id: String): StockIndicatorAIResult?

    /**
     * 특정 종목의 최신 AI 분석 결과 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE ticker = :ticker ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByTicker(ticker: String): StockIndicatorAIResult?

    /**
     * 모든 분석 결과 조회 (최신순)
     */
    @Query("SELECT * FROM stock_indicator_ai_result ORDER BY createdAt DESC")
    fun getAll(): Flow<List<StockIndicatorAIResult>>

    /**
     * 최근 N개 분석 결과 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<StockIndicatorAIResult>>

    /**
     * 특정 시장의 분석 결과 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE market = :market ORDER BY createdAt DESC")
    fun getAllByMarket(market: String): Flow<List<StockIndicatorAIResult>>

    /**
     * 특정 AI 제공자의 결과만 조회
     */
    @Query("SELECT * FROM stock_indicator_ai_result WHERE aiProvider = :provider ORDER BY createdAt DESC")
    fun getByProvider(provider: String): Flow<List<StockIndicatorAIResult>>

    /**
     * 특정 기간의 결과 조회
     */
    @Query("""
        SELECT * FROM stock_indicator_ai_result
        WHERE analysisDate >= :startDate AND analysisDate <= :endDate
        ORDER BY createdAt DESC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<StockIndicatorAIResult>

    /**
     * 데이터 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: StockIndicatorAIResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<StockIndicatorAIResult>)

    /**
     * ID로 삭제
     */
    @Query("DELETE FROM stock_indicator_ai_result WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 특정 종목 데이터 삭제
     */
    @Query("DELETE FROM stock_indicator_ai_result WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    /**
     * 특정 날짜 이전 데이터 삭제
     */
    @Query("DELETE FROM stock_indicator_ai_result WHERE createdAt < :beforeTimestamp")
    suspend fun deleteBeforeTimestamp(beforeTimestamp: Long)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM stock_indicator_ai_result")
    suspend fun deleteAll()

    /**
     * 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM stock_indicator_ai_result")
    suspend fun getCount(): Int

    /**
     * 특정 종목 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM stock_indicator_ai_result WHERE ticker = :ticker")
    suspend fun getCountByTicker(ticker: String): Int
}
