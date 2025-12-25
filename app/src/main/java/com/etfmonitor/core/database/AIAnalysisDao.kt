package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.AIAnalysisResult
import kotlinx.coroutines.flow.Flow

/**
 * AI 분석 결과 DAO
 */
@Dao
interface AIAnalysisDao {

    /**
     * 특정 시장의 모든 AI 분석 결과 조회 (날짜 내림차순)
     */
    @Query("SELECT * FROM ai_analysis_result WHERE market = :market ORDER BY analysisDate DESC")
    fun getAllByMarket(market: String): Flow<List<AIAnalysisResult>>

    /**
     * 특정 시장의 특정 날짜 AI 분석 결과 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE market = :market AND analysisDate = :date ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByMarketAndDate(market: String, date: String): AIAnalysisResult?

    /**
     * ID로 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE id = :id")
    suspend fun getById(id: String): AIAnalysisResult?

    /**
     * 상관관계 분석 ID로 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE correlationResultId = :correlationId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByCorrelationId(correlationId: String): AIAnalysisResult?

    /**
     * 최근 N개 결과 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE market = :market ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentByMarket(market: String, limit: Int): Flow<List<AIAnalysisResult>>

    /**
     * 특정 기간의 결과 조회
     */
    @Query("""
        SELECT * FROM ai_analysis_result
        WHERE market = :market AND analysisDate >= :startDate AND analysisDate <= :endDate
        ORDER BY analysisDate ASC
    """)
    suspend fun getByMarketAndDateRange(market: String, startDate: String, endDate: String): List<AIAnalysisResult>

    /**
     * 최신 AI 분석 결과 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE market = :market ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestByMarket(market: String): AIAnalysisResult?

    /**
     * 특정 AI 제공자의 결과만 조회
     */
    @Query("SELECT * FROM ai_analysis_result WHERE market = :market AND aiProvider = :provider ORDER BY createdAt DESC")
    fun getByMarketAndProvider(market: String, provider: String): Flow<List<AIAnalysisResult>>

    /**
     * 데이터 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: AIAnalysisResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<AIAnalysisResult>)

    /**
     * ID로 삭제
     */
    @Query("DELETE FROM ai_analysis_result WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 특정 시장 데이터 삭제
     */
    @Query("DELETE FROM ai_analysis_result WHERE market = :market")
    suspend fun deleteByMarket(market: String)

    /**
     * 특정 날짜 이전 데이터 삭제
     */
    @Query("DELETE FROM ai_analysis_result WHERE analysisDate < :beforeDate")
    suspend fun deleteBeforeDate(beforeDate: String)

    /**
     * 모든 데이터 삭제
     */
    @Query("DELETE FROM ai_analysis_result")
    suspend fun deleteAll()

    /**
     * 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM ai_analysis_result WHERE market = :market")
    suspend fun getCountByMarket(market: String): Int

    /**
     * 신호별 정확도 통계 (백테스트용)
     * 실제 시장 변동과 비교하여 정확도 계산
     */
    @Query("""
        SELECT signal, COUNT(*) as totalCount, AVG(confidence) as avgConfidence
        FROM ai_analysis_result
        WHERE market = :market AND analysisDate >= :startDate
        GROUP BY signal
    """)
    suspend fun getSignalAccuracyStats(market: String, startDate: String): List<AISignalStats>
}

/**
 * AI 신호 통계 데이터 클래스
 */
data class AISignalStats(
    val signal: String,
    val totalCount: Int,
    val avgConfidence: Double
)
