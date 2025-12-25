package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.LiquidityAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface LiquidityAnalysisDao {

    // ==================== Insert ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: LiquidityAnalysis)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(analyses: List<LiquidityAnalysis>)

    // ==================== Query ====================

    /**
     * 특정 날짜의 유동성 분석 조회
     */
    @Query("SELECT * FROM liquidity_analysis WHERE date = :date")
    suspend fun getByDate(date: String): LiquidityAnalysis?

    /**
     * 최근 유동성 분석 조회
     */
    @Query("SELECT * FROM liquidity_analysis ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): LiquidityAnalysis?

    /**
     * 최근 유동성 분석 (Flow)
     */
    @Query("SELECT * FROM liquidity_analysis ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<LiquidityAnalysis?>

    /**
     * 기간 내 유동성 분석 이력 조회
     */
    @Query("""
        SELECT * FROM liquidity_analysis
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<LiquidityAnalysis>

    /**
     * 최근 N일 유동성 분석 이력 조회
     */
    @Query("SELECT * FROM liquidity_analysis ORDER BY date DESC LIMIT :days")
    suspend fun getRecentHistory(days: Int = 30): List<LiquidityAnalysis>

    /**
     * 최근 N일 유동성 분석 이력 (Flow)
     */
    @Query("SELECT * FROM liquidity_analysis ORDER BY date DESC LIMIT :days")
    fun observeRecentHistory(days: Int = 30): Flow<List<LiquidityAnalysis>>

    /**
     * 특정 신호의 유동성 분석 조회
     */
    @Query("""
        SELECT * FROM liquidity_analysis
        WHERE signal = :signal
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getBySignal(signal: String, limit: Int = 20): List<LiquidityAnalysis>

    /**
     * 특정 위험 수준의 유동성 분석 조회
     */
    @Query("""
        SELECT * FROM liquidity_analysis
        WHERE riskLevel = :riskLevel
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getByRiskLevel(riskLevel: String, limit: Int = 20): List<LiquidityAnalysis>

    /**
     * 예탁금/시총 비율 상위 조회 (유동성 풍부)
     */
    @Query("""
        SELECT * FROM liquidity_analysis
        ORDER BY depositToMarketCapRatio DESC
        LIMIT :limit
    """)
    suspend fun getTopLiquidityDates(limit: Int = 10): List<LiquidityAnalysis>

    /**
     * 신용/예탁금 비율 상위 조회 (레버리지 과열)
     */
    @Query("""
        SELECT * FROM liquidity_analysis
        ORDER BY creditToDepositRatio DESC
        LIMIT :limit
    """)
    suspend fun getTopLeverageDates(limit: Int = 10): List<LiquidityAnalysis>

    /**
     * 평균 예탁금/시총 비율 계산 (기간)
     */
    @Query("""
        SELECT AVG(depositToMarketCapRatio)
        FROM liquidity_analysis
        WHERE date BETWEEN :startDate AND :endDate
    """)
    suspend fun getAvgDepositRatio(startDate: String, endDate: String): Double?

    /**
     * 평균 신용/예탁금 비율 계산 (기간)
     */
    @Query("""
        SELECT AVG(creditToDepositRatio)
        FROM liquidity_analysis
        WHERE date BETWEEN :startDate AND :endDate
    """)
    suspend fun getAvgCreditRatio(startDate: String, endDate: String): Double?

    /**
     * 예탁금/시총 비율 백분위 계산
     * (현재 값보다 낮은 비율의 데이터 비율)
     */
    @Query("""
        SELECT CAST(COUNT(*) AS REAL) * 100 /
               (SELECT COUNT(*) FROM liquidity_analysis)
        FROM liquidity_analysis
        WHERE depositToMarketCapRatio < :currentRatio
    """)
    suspend fun getDepositRatioPercentile(currentRatio: Double): Double?

    /**
     * 최근 분석 날짜 조회
     */
    @Query("SELECT MAX(date) FROM liquidity_analysis")
    suspend fun getLatestDate(): String?

    /**
     * 가장 오래된 분석 날짜 조회
     */
    @Query("SELECT MIN(date) FROM liquidity_analysis")
    suspend fun getOldestDate(): String?

    // ==================== Statistics ====================

    /**
     * 기간 내 통계 요약
     */
    @Query("""
        SELECT
            AVG(depositAmount) as avgDeposit,
            AVG(creditAmount) as avgCredit,
            AVG(depositToMarketCapRatio) as avgDepositRatio,
            AVG(creditToDepositRatio) as avgCreditRatio,
            MIN(depositToMarketCapRatio) as minDepositRatio,
            MAX(depositToMarketCapRatio) as maxDepositRatio,
            MIN(creditToDepositRatio) as minCreditRatio,
            MAX(creditToDepositRatio) as maxCreditRatio
        FROM liquidity_analysis
        WHERE date BETWEEN :startDate AND :endDate
    """)
    suspend fun getStatistics(startDate: String, endDate: String): LiquidityStatistics?

    // ==================== Delete ====================

    @Query("DELETE FROM liquidity_analysis WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM liquidity_analysis WHERE date < :date")
    suspend fun deleteOldData(date: String)

    @Query("DELETE FROM liquidity_analysis")
    suspend fun deleteAll()

    // ==================== Count ====================

    @Query("SELECT COUNT(*) FROM liquidity_analysis")
    suspend fun getCount(): Int
}

/**
 * 유동성 통계 결과
 */
data class LiquidityStatistics(
    val avgDeposit: Double?,
    val avgCredit: Double?,
    val avgDepositRatio: Double?,
    val avgCreditRatio: Double?,
    val minDepositRatio: Double?,
    val maxDepositRatio: Double?,
    val minCreditRatio: Double?,
    val maxCreditRatio: Double?
)
