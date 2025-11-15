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
