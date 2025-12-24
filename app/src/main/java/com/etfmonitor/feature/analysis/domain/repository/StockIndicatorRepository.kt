package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.feature.analysis.domain.model.FullStockIndicatorAnalysis
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorCorrelation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorInterpretation
import com.etfmonitor.feature.analysis.domain.model.StockIndicatorRequest
import kotlinx.coroutines.flow.Flow

/**
 * 종목-지표 상관관계 분석 Repository 인터페이스
 */
interface StockIndicatorRepository {

    /**
     * 종목 검색
     */
    suspend fun searchStock(query: String): Pair<String, String>?

    /**
     * 종목-지표 상관관계 분석 (로컬 계산)
     */
    suspend fun analyzeStockIndicatorCorrelations(
        request: StockIndicatorRequest
    ): Result<StockIndicatorCorrelation>

    /**
     * 종목-지표 상관관계 + AI 해석 통합 분석
     */
    suspend fun runFullStockIndicatorCorrelationAnalysis(
        ticker: String,
        name: String,
        market: String,
        periodDays: Int = 30
    ): Result<FullStockIndicatorAnalysis>

    /**
     * AI 해석 추가
     */
    suspend fun interpretStockIndicatorCorrelationsWithAI(
        correlationResult: StockIndicatorCorrelation
    ): Result<StockIndicatorInterpretation>

    /**
     * 종목별 AI 분석 히스토리 조회 (Flow)
     */
    fun getStockIndicatorAIHistory(ticker: String): Flow<List<StockIndicatorAIHistoryItem>>

    /**
     * 전체 AI 분석 히스토리 조회 (Flow)
     */
    fun getAllStockIndicatorAIHistory(limit: Int): Flow<List<StockIndicatorAIHistoryItem>>

    /**
     * 히스토리 항목 삭제
     */
    suspend fun deleteStockIndicatorAIHistory(id: String)
}

/**
 * 종목-지표 AI 분석 히스토리 아이템
 */
data class StockIndicatorAIHistoryItem(
    val id: String,
    val ticker: String,
    val stockName: String,
    val market: String,
    val period: Int,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val keyCorrelations: String,
    val marketSentimentImpact: String,
    val fundFlowImpact: String,
    val etfFlowImpact: String,
    val recommendation: String,
    val reasoning: String,
    val createdAt: Long
)
