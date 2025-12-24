package com.etfmonitor.feature.analysis.domain.model

/**
 * AI 채팅 세션 도메인 모델
 */
data class ChatSession(
    val id: String,
    val title: String,
    val market: String?,
    val analysisDate: String?,
    val contextData: String?,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * AI 채팅 메시지 도메인 모델
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val aiProvider: String?,
    val aiModel: String?,
    val tokenCount: Int?,
    val timestamp: Long
)

/**
 * 메시지 역할
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 분석 컨텍스트 (ChatSession에 포함되는 데이터)
 */
data class AnalysisContext(
    val currentIndex: Double,
    val indexChangeRate: Double,
    val etfSummary: EtfSummary?,
    val fearGreedValue: Double?,
    val oscillatorValue: Double?
)

/**
 * ETF 요약 정보
 */
data class EtfSummary(
    val newStocks: Int,
    val removedStocks: Int,
    val increasedStocks: Int,
    val decreasedStocks: Int,
    val cashDepositChange: Double
)

/**
 * 종목-지표 상관관계 분석 요청
 */
data class StockIndicatorRequest(
    val ticker: String,
    val name: String,
    val market: String,
    val periodDays: Int
)

/**
 * 종목-지표 상관관계 분석 결과
 */
data class StockIndicatorCorrelation(
    val ticker: String,
    val name: String,
    val market: String,
    val period: Int,
    val indicatorCorrelations: List<IndicatorCorrelation>,
    val compositeScore: Double,
    val signal: String,
    val confidence: Double
)

/**
 * 개별 지표 상관관계
 */
data class IndicatorCorrelation(
    val indicatorName: String,
    val correlationValue: Double,
    val strength: CorrelationStrength,
    val description: String
)

/**
 * 상관관계 강도
 */
enum class CorrelationStrength {
    STRONG,     // |r| >= 0.7
    MODERATE,   // |r| >= 0.4
    WEAK,       // |r| >= 0.2
    NONE;       // |r| < 0.2

    companion object {
        fun fromValue(value: Double): CorrelationStrength {
            val absValue = kotlin.math.abs(value)
            return when {
                absValue >= 0.7 -> STRONG
                absValue >= 0.4 -> MODERATE
                absValue >= 0.2 -> WEAK
                else -> NONE
            }
        }
    }
}

/**
 * 종목-지표 AI 해석 결과
 */
data class StockIndicatorInterpretation(
    val ticker: String,
    val name: String,
    val period: Int,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val keyCorrelations: List<String>,
    val marketSentimentImpact: String,
    val fundFlowImpact: String,
    val etfFlowImpact: String,
    val recommendation: String,
    val reasoning: String
)

/**
 * 종목-지표 전체 분석 결과
 */
data class FullStockIndicatorAnalysis(
    val correlationResult: StockIndicatorCorrelation?,
    val aiInterpretation: StockIndicatorInterpretation?,
    val errorMessage: String?
)

/**
 * 신호 타입
 */
enum class SignalType {
    STRONG_BUY,
    BUY,
    NEUTRAL,
    SELL,
    STRONG_SELL;

    fun toKorean(): String = when (this) {
        STRONG_BUY -> "강력 매수"
        BUY -> "매수"
        NEUTRAL -> "중립"
        SELL -> "매도"
        STRONG_SELL -> "강력 매도"
    }
}

/**
 * 위험 수준
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
