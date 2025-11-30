package com.etfmonitor.oscillator.model

/**
 * UI 상태
 */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : UiState<Nothing>()
}

/**
 * 주식 데이터
 */
data class StockData(
    val ticker: String,
    val name: String,
    val dates: List<String>,
    val marketCap: List<Long>,        // 시가총액
    val foreign5d: List<Long>,        // 외국인 5일 누적
    val institution5d: List<Long>     // 기관 5일 누적
)

/**
 * 증시 자금 동향 데이터
 */
data class MarketDepositData(
    val dates: List<String>,
    val depositAmounts: List<Double>,    // 고객예탁금 (억원)
    val depositChanges: List<Double>,    // 고객예탁금 변화 (억원)
    val creditAmounts: List<Double>,     // 신용잔고 (억원)
    val creditChanges: List<Double>      // 신용잔고 변화 (억원)
)

/**
 * 수급 오실레이터 계산 결과
 */
data class OscillatorResult(
    val dates: List<String>,
    val marketCap: List<Long>,       // 시가총액 (원본 데이터)
    val oscillator: List<Double>,     // 수급 오실레이터
    val ema: List<Double>,            // EMA
    val macd: List<Double>,           // MACD
    val signal: List<Double>,         // Signal
    val histogram: List<Double>       // Histogram
)

/**
 * 매매 신호
 */
enum class TradeSignal {
    STRONG_BUY,    // 강력 매수
    BUY,           // 매수
    NEUTRAL,       // 중립
    SELL,          // 매도
    STRONG_SELL    // 강력 매도
}

/**
 * 매매 신호 분석 결과
 */
data class SignalAnalysis(
    val signal: TradeSignal,
    val score: Double,              // -100 ~ +100
    val trend: String,              // 추세 설명
    val foreignTrend: String,       // 외국인 동향
    val institutionTrend: String,   // 기관 동향
    val recommendation: String      // 투자 권고
)

// ============================================================
// 추세 시그널 분석 (Trend Signal Analysis) 모델
// ============================================================

/**
 * 추세 시그널 OHLCV + 지표 데이터
 */
data class TrendSignalData(
    val ticker: String,
    val name: String,
    val interval: String,           // "d"=일별, "w"=주별
    val dates: List<String>,
    val open: List<Double>,
    val high: List<Double>,
    val low: List<Double>,
    val close: List<Double>,
    val volume: List<Long>,
    val ma: List<Double>,           // 이동평균
    val cmf: List<Double>,          // Chaikin Money Flow
    val fearGreed: List<Double>,    // Fear & Greed Index (-1 ~ +1)
    val buySignal: List<Int>,       // 매수 시그널 (1=매수, 0=없음)
    val sellSignal: List<Int>       // 매도 시그널 (1=매도, 0=없음)
)

/**
 * 추세 시그널 분석 결과
 */
data class TrendSignalAnalysis(
    val signal: TrendTradeSignal,
    val currentPrice: Double,
    val maPrice: Double,
    val cmfValue: Double,
    val fearGreedValue: Double,
    val trendDescription: String,
    val recommendation: String,
    val recentBuyCount: Int,        // 최근 N기간 매수 시그널 수
    val recentSellCount: Int        // 최근 N기간 매도 시그널 수
)

/**
 * 추세 매매 신호
 */
enum class TrendTradeSignal {
    STRONG_BUY,     // 강력 매수 (모든 조건 충족)
    BUY,            // 매수 (일부 조건 충족)
    NEUTRAL,        // 중립
    SELL,           // 매도 (일부 조건 충족)
    STRONG_SELL     // 강력 매도 (모든 조건 충족)
}

/**
 * Fear & Greed 상태
 */
enum class FearGreedState(val displayName: String) {
    EXTREME_FEAR("극도의 공포"),
    FEAR("공포"),
    NEUTRAL("중립"),
    GREED("탐욕"),
    EXTREME_GREED("극도의 탐욕");

    companion object {
        fun fromValue(value: Double): FearGreedState = when {
            value <= -0.6 -> EXTREME_FEAR
            value <= -0.2 -> FEAR
            value <= 0.2 -> NEUTRAL
            value <= 0.6 -> GREED
            else -> EXTREME_GREED
        }
    }
}
