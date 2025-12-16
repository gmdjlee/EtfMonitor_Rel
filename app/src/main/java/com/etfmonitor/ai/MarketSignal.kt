package com.etfmonitor.ai

import kotlinx.serialization.Serializable

/**
 * AI 분석 결과 - 시장 신호
 */
@Serializable
data class MarketSignal(
    val market: String, // "KOSPI" or "KOSDAQ"
    val date: String, // "2025-01-01"
    val signal: SignalType, // 매수/매도/중립
    val confidence: Double, // 신뢰도 (0.0 ~ 1.0)
    val upProbability: Double, // 상승 확률 (%)
    val downProbability: Double, // 하락 확률 (%)
    val reasoning: String, // AI의 분석 이유
    val keyFactors: List<String>, // 주요 영향 요인
    val recommendation: String, // 투자 권장사항
    val riskLevel: RiskLevel, // 위험 수준
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * 신호 타입
 */
enum class SignalType {
    STRONG_BUY,  // 강력 매수
    BUY,         // 매수
    NEUTRAL,     // 중립
    SELL,        // 매도
    STRONG_SELL; // 강력 매도

    fun toKorean(): String = when (this) {
        STRONG_BUY -> "강력 매수"
        BUY -> "매수"
        NEUTRAL -> "중립"
        SELL -> "매도"
        STRONG_SELL -> "강력 매도"
    }

    fun toEmoji(): String = when (this) {
        STRONG_BUY -> "🚀"
        BUY -> "📈"
        NEUTRAL -> "➡️"
        SELL -> "📉"
        STRONG_SELL -> "⚠️"
    }
}

/**
 * 위험 수준
 */
enum class RiskLevel {
    LOW,    // 낮음
    MEDIUM, // 중간
    HIGH;   // 높음

    fun toKorean(): String = when (this) {
        LOW -> "낮음"
        MEDIUM -> "중간"
        HIGH -> "높음"
    }
}

/**
 * 시장 분석 데이터 (AI 입력용)
 */
@Serializable
data class MarketAnalysisData(
    val market: String,
    val date: String,
    val currentIndex: Double,
    val indexChange: Double,

    // ETF 통계
    val newStocks: Int,
    val newStocksAmount: Long,
    val removedStocks: Int,
    val removedStocksAmount: Long,
    val increasedStocks: Int,
    val increasedStocksAmount: Long,
    val decreasedStocks: Int,
    val decreasedStocksAmount: Long,

    // 원화예금
    val cashDeposit: Long,
    val cashDepositChange: Long,
    val cashDepositChangeRate: Double,

    // 증시 자금 동향
    val depositAmount: Double?,
    val depositChange: Double?,

    // Fear & Greed
    val fearGreedValue: Double?,
    val fearGreedOscillator: Double?,

    // 과매수/과매도
    val marketOscillator: Double?,

    // 상관관계 데이터 (선택)
    val correlationData: Map<String, Double>? = null
)

/**
 * AI 분석 요청
 */
data class AIAnalysisRequest(
    val data: MarketAnalysisData,
    val analysisType: AnalysisType = AnalysisType.COMPREHENSIVE,
    val includeBacktest: Boolean = false,
    val historicalDays: Int = 30
)

/**
 * 분석 타입
 */
enum class AnalysisType {
    COMPREHENSIVE,  // 종합 분석
    ETF_ONLY,      // ETF 통계만
    TECHNICAL_ONLY, // 기술적 지표만
    SENTIMENT_ONLY  // 시장 심리만
}

/**
 * AI 분석 응답
 */
data class AIAnalysisResponse(
    val signal: MarketSignal,
    val alternativeScenarios: List<Scenario> = emptyList(),
    val historicalAccuracy: BacktestResult? = null,
    val processingTime: Long
)

/**
 * 대안 시나리오
 */
data class Scenario(
    val condition: String, // 조건 설명
    val signal: SignalType,
    val probability: Double // 발생 확률
)

/**
 * 백테스트 결과
 */
data class BacktestResult(
    val totalSignals: Int,
    val correctSignals: Int,
    val accuracy: Double, // 정확도 (%)
    val averageReturn: Double, // 평균 수익률 (%) - 거래비용 차감 전
    val netReturn: Double = averageReturn, // 순수익률 (%) - 거래비용 차감 후
    val winRate: Double, // 승률 (%)
    val maxDrawdown: Double, // 최대 낙폭 (%)
    val sharpeRatio: Double? = null, // 샤프 비율 (거래비용 차감 전)
    val netSharpeRatio: Double? = null, // 순샤프 비율 (거래비용 차감 후)
    val period: String, // 분석 기간
    val totalTransactionCost: Double = 0.0, // 총 거래비용 (%)
    val commissionRate: Double = 0.015, // 수수료율 (%)
    val slippageRate: Double = 0.05 // 슬리피지율 (%)
)

/**
 * 신호 기록 (백테스팅용)
 */
data class SignalRecord(
    val date: String,
    val signal: SignalType,
    val confidence: Double,
    val indexAtSignal: Double,
    val indexAfter1Day: Double? = null,
    val indexAfter5Days: Double? = null,
    val indexAfter10Days: Double? = null,
    val actualReturn1Day: Double? = null,
    val actualReturn5Days: Double? = null,
    val actualReturn10Days: Double? = null,
    val wasCorrect: Boolean? = null
)
