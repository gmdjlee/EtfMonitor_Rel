package com.etfmonitor.feature.market.domain.model

/**
 * 시장 과매수/과매도 도메인 모델
 */
data class MarketOscillator(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val oscillator: Double,
    val lastUpdated: Long
) {
    /**
     * 과매수/과매도 상태
     */
    fun getStatus(
        overboughtThreshold: Double = 80.0,
        oversoldThreshold: Double = -80.0
    ): OscillatorStatus = when {
        oscillator >= overboughtThreshold -> OscillatorStatus.OVERBOUGHT
        oscillator <= oversoldThreshold -> OscillatorStatus.OVERSOLD
        else -> OscillatorStatus.NEUTRAL
    }
}

/**
 * 과매수/과매도 상태
 */
enum class OscillatorStatus(val displayName: String) {
    OVERBOUGHT("과매수"),
    NEUTRAL("중립"),
    OVERSOLD("과매도")
}
