package com.etfmonitor.feature.stock.domain.model

/**
 * Oscillator Result Domain Model
 *
 * 수급 오실레이터 계산 결과입니다.
 *
 * @property dates 날짜 목록
 * @property marketCap 시가총액 목록
 * @property oscillator 수급 오실레이터 값 목록
 * @property ema EMA 값 목록
 * @property macd MACD 값 목록
 * @property signal Signal 값 목록
 * @property histogram Histogram 값 목록
 */
data class OscillatorResult(
    val dates: List<String>,
    val marketCap: List<Long>,
    val oscillator: List<Double>,
    val ema: List<Double>,
    val macd: List<Double>,
    val signal: List<Double>,
    val histogram: List<Double>
)

/**
 * Trade Signal Enum
 *
 * 매매 신호 유형
 */
enum class TradeSignal {
    STRONG_BUY,    // 강력 매수
    BUY,           // 매수
    NEUTRAL,       // 중립
    SELL,          // 매도
    STRONG_SELL    // 강력 매도
}

/**
 * Signal Analysis Domain Model
 *
 * 매매 신호 분석 결과입니다.
 *
 * @property signal 매매 신호
 * @property score 점수 (-100 ~ +100)
 * @property trend 추세 설명
 * @property foreignTrend 외국인 동향
 * @property institutionTrend 기관 동향
 * @property recommendation 투자 권고
 */
data class SignalAnalysis(
    val signal: TradeSignal,
    val score: Double,
    val trend: String,
    val foreignTrend: String,
    val institutionTrend: String,
    val recommendation: String
)
