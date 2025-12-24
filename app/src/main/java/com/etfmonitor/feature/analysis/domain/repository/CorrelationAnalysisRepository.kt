package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.feature.analysis.domain.model.AIAnalysis
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import kotlinx.coroutines.flow.Flow

/**
 * 상관관계 분석 Repository 인터페이스
 */
interface CorrelationAnalysisRepository {

    /**
     * 상관관계 분석 실행 (로컬 계산)
     */
    suspend fun runCorrelationAnalysis(
        market: String,
        endDate: String,
        periodDays: Int = 30
    ): Result<CorrelationAnalysis>

    /**
     * 최신 데이터로 상관관계 분석 실행
     */
    suspend fun runLatestCorrelationAnalysis(
        market: String,
        periodDays: Int = 30
    ): Result<CorrelationAnalysis>

    /**
     * AI를 통한 상관관계 분석 해석
     */
    suspend fun interpretWithAI(
        correlationResult: CorrelationAnalysis
    ): Result<AIAnalysis>

    /**
     * 상관관계 분석 + AI 해석 통합 실행
     */
    suspend fun runFullAnalysis(
        market: String,
        endDate: String? = null,
        periodDays: Int = 30
    ): Result<FullAnalysis>

    /**
     * 저장된 상관관계 분석 결과 조회 (Flow)
     */
    fun getCorrelationResults(market: String): Flow<List<CorrelationAnalysis>>

    /**
     * 특정 날짜의 상관관계 분석 결과 조회
     */
    suspend fun getCorrelationResult(market: String, date: String): CorrelationAnalysis?

    /**
     * 최신 상관관계 분석 결과 조회
     */
    suspend fun getLatestCorrelationResult(market: String): CorrelationAnalysis?

    /**
     * AI 분석 결과 조회 (Flow)
     */
    fun getAIAnalysisResults(market: String): Flow<List<AIAnalysis>>

    /**
     * 최신 AI 분석 결과 조회
     */
    suspend fun getLatestAIResult(market: String): AIAnalysis?
}
