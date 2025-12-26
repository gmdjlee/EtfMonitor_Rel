package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.core.network.ai.MarketSignal
import com.etfmonitor.core.network.ai.SignalType

/**
 * AI 분석 Repository 인터페이스
 */
interface AIAnalysisRepository {

    /**
     * 종합 시장 분석 수행
     */
    suspend fun analyzeMarket(
        market: String,
        date: String,
        analysisType: AnalysisTypeRequest = AnalysisTypeRequest.COMPREHENSIVE
    ): Result<AIAnalysisResponse>

    /**
     * 최신 데이터로 시장 분석
     */
    suspend fun analyzeLatestMarket(market: String): Result<AIAnalysisResponse>

    /**
     * 빠른 신호 생성 (간소화 분석)
     */
    suspend fun generateQuickSignal(
        market: String,
        date: String
    ): Result<MarketSignal>

    /**
     * 여러 날짜의 신호 배치 생성 (백테스팅용)
     */
    suspend fun generateBatchSignals(
        market: String,
        startDate: String,
        endDate: String
    ): Result<List<SignalRecord>>

    /**
     * API 사용 가능 여부 확인
     */
    suspend fun isApiAvailable(): Boolean

    /**
     * API 키 테스트
     */
    suspend fun testApiConnection(): Result<Boolean>

    /**
     * 현재 선택된 AI 제공자
     */
    fun getSelectedProvider(): AIProvider

    /**
     * 사용 가능한 모든 AI 제공자 목록
     */
    fun getAvailableProviders(): List<AIProvider>

    /**
     * 특정 AI 제공자의 사용 가능한 모델 목록 조회
     */
    suspend fun listModels(provider: AIProvider): Result<List<AIModel>>
}

/**
 * 분석 타입 요청
 */
enum class AnalysisTypeRequest {
    COMPREHENSIVE,
    ETF_ONLY,
    TECHNICAL_ONLY,
    SENTIMENT_ONLY
}

/**
 * AI 분석 응답
 */
data class AIAnalysisResponse(
    val signal: MarketSignal,
    val alternativeScenarios: List<String>,
    val historicalAccuracy: Double?,
    val processingTime: Long
)

/**
 * 신호 기록
 */
data class SignalRecord(
    val date: String,
    val signal: SignalType,
    val confidence: Double,
    val indexAtSignal: Double
)
