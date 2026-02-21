package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult
import kotlinx.coroutines.flow.Flow

/**
 * 상관관계 분석 결과 DAO
 */
@Dao
interface CorrelationAnalysisDao {

    /**
     * 특정 시장의 모든 분석 결과 조회 (날짜 내림차순)
     */
    @Query("SELECT * FROM correlation_analysis_result WHERE market = :market ORDER BY analysisDate DESC LIMIT 365")
    fun getAllByMarket(market: String): Flow<List<CorrelationAnalysisResult>>

    /**
     * 특정 시장의 특정 날짜 분석 결과 조회
     */
    @Query("SELECT * FROM correlation_analysis_result WHERE market = :market AND analysisDate = :date")
    suspend fun getByMarketAndDate(market: String, date: String): CorrelationAnalysisResult?

    /**
     * ID로 조회
     */
    @Query("SELECT * FROM correlation_analysis_result WHERE id = :id")
    suspend fun getById(id: String): CorrelationAnalysisResult?

    /**
     * 최근 N개 결과 조회
     */
    @Query("SELECT * FROM correlation_analysis_result WHERE market = :market ORDER BY analysisDate DESC LIMIT :limit")
    fun getRecentByMarket(market: String, limit: Int): Flow<List<CorrelationAnalysisResult>>

    /**
     * 특정 기간의 결과 조회
     */
    @Query("""
        SELECT * FROM correlation_analysis_result
        WHERE market = :market AND analysisDate >= :startDate AND analysisDate <= :endDate
        ORDER BY analysisDate ASC
    """)
    suspend fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): List<CorrelationAnalysisResult>

    /**
     * 최신 분석 결과 조회
     */
    @Query("SELECT * FROM correlation_analysis_result WHERE market = :market ORDER BY analysisDate DESC LIMIT 1")
    suspend fun getLatestByMarket(market: String): CorrelationAnalysisResult?

    /**
     * 데이터 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: CorrelationAnalysisResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<CorrelationAnalysisResult>)

    /**
     * 특정 시장 데이터 삭제
     */
    @Query("DELETE FROM correlation_analysis_result WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    /**
     * 특정 날짜 이전 데이터 삭제
     */
    @Query("DELETE FROM correlation_analysis_result WHERE analysisDate < :beforeDate")
    suspend fun deleteBeforeDate(beforeDate: String)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM correlation_analysis_result")
    suspend fun deleteAll()

    /**
     * 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM correlation_analysis_result WHERE market = :market")
    suspend fun getCountByMarket(market: String): Int

    /**
     * 특정 신호의 개수 조회 (백테스트용)
     */
    @Query("""
        SELECT COUNT(*) FROM correlation_analysis_result
        WHERE market = :market AND signal = :signal AND analysisDate >= :startDate AND analysisDate <= :endDate
    """)
    suspend fun getSignalCount(market: String, signal: String, startDate: String, endDate: String): Int

    /**
     * 신호별 통계 조회
     */
    @Query("""
        SELECT signal, COUNT(*) as count, AVG(confidence) as avgConfidence
        FROM correlation_analysis_result
        WHERE market = :market AND analysisDate >= :startDate
        GROUP BY signal
    """)
    suspend fun getSignalStatistics(market: String, startDate: String): List<SignalStatistics>
}

/**
 * 신호 통계 데이터 클래스
 */
data class SignalStatistics(
    val signal: String,
    val count: Int,
    val avgConfidence: Double
)
