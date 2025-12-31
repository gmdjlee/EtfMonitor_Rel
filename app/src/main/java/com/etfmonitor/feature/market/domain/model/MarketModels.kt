package com.etfmonitor.feature.market.domain.model

/**
 * Fear & Greed Index Domain Model
 */
data class FearGreedIndex(
    val id: String,
    val market: String,
    val date: String,
    val indexValue: Double,
    val fearGreedValue: Double,
    val oscillator: Double,
    val rsi: Double,
    val momentum: Double,
    val putCallRatio: Double,
    val volatility: Double,
    val spread: Double,
    val lastUpdated: Long
) {
    /**
     * Fear & Greed 상태 문자열 반환
     */
    fun getStatus(): String = when {
        fearGreedValue >= 0.8 -> "Extreme Greed"
        fearGreedValue >= 0.6 -> "Greed"
        fearGreedValue >= 0.4 -> "Neutral"
        fearGreedValue >= 0.2 -> "Fear"
        else -> "Extreme Fear"
    }

    /**
     * Fear & Greed 상태 한글 반환
     */
    fun getStatusKorean(): String = when {
        fearGreedValue >= 0.8 -> "극단적 탐욕"
        fearGreedValue >= 0.6 -> "탐욕"
        fearGreedValue >= 0.4 -> "중립"
        fearGreedValue >= 0.2 -> "공포"
        else -> "극단적 공포"
    }
}

/**
 * Market Deposit Domain Model
 */
data class MarketDeposit(
    val date: String,
    val depositAmount: Double,
    val depositChange: Double,
    val creditAmount: Double,
    val creditChange: Double,
    val lastUpdated: Long
)

/**
 * Market Deposit Data (for chart display)
 */
data class MarketDepositData(
    val dates: List<String>,
    val depositAmounts: List<Double>,
    val depositChanges: List<Double>,
    val creditAmounts: List<Double>,
    val creditChanges: List<Double>
) {
    companion object {
        fun empty() = MarketDepositData(
            dates = emptyList(),
            depositAmounts = emptyList(),
            depositChanges = emptyList(),
            creditAmounts = emptyList(),
            creditChanges = emptyList()
        )
    }
}

/**
 * Market Oscillator Domain Model
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
     * 과매수/과매도 상태 반환
     */
    fun getStatus(): String = when {
        oscillator >= 80 -> "Overbought"
        oscillator >= 60 -> "Strong"
        oscillator >= 40 -> "Neutral"
        oscillator >= 20 -> "Weak"
        else -> "Oversold"
    }

    /**
     * 과매수/과매도 상태 한글 반환
     */
    fun getStatusKorean(): String = when {
        oscillator >= 80 -> "과매수"
        oscillator >= 60 -> "강세"
        oscillator >= 40 -> "중립"
        oscillator >= 20 -> "약세"
        else -> "과매도"
    }
}

/**
 * Market Index Domain Model
 */
data class MarketIndex(
    val id: String,
    val market: String,
    val date: String,
    val closePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Long,
    val changeRate: Double,
    val lastUpdated: Long
)

/**
 * Market Type Enum
 */
enum class MarketType(val code: String, val displayName: String) {
    KOSPI("KOSPI", "코스피"),
    KOSDAQ("KOSDAQ", "코스닥");

    companion object {
        fun fromCode(code: String): MarketType? = entries.find { it.code == code }
    }
}

/**
 * Blood Indicator Domain Model (v2.0 - FRED API)
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread from FRED)
 * - 100주 SMA 위 (RISK_ON): Green - 시장이 건강하고 위험 자산 선호
 * - 100주 SMA 아래 (RISK_OFF): Red - 시장 스트레스, 안전 자산 선호
 *
 * Data Sources:
 * - US03MY: Yahoo Finance (^IRX)
 * - BAMLH0A0HYM2: FRED API (free API key required)
 */
data class BloodIndicator(
    val id: String,
    val date: String,
    val bloodValue: Double,
    val bloodSma: Double,           // 100-week SMA of BLOOD value
    val us03my: Double,             // 3-Month T-Bill Rate (^IRX from Yahoo)
    val highYieldSpread: Double,    // ICE BofA High Yield Spread (BAMLH0A0HYM2 from FRED)
    val spyClose: Double?,
    val signalType: BloodSignalType,
    val signalColor: String,        // "green", "red", "gray"
    val lastUpdated: Long
) {
    /**
     * Get trend description
     */
    fun getTrendDescription(): String = when (signalType) {
        BloodSignalType.RISK_ON -> "Risk On - SMA 상향 돌파"
        BloodSignalType.RISK_OFF -> "Risk Off - SMA 하향 돌파"
        BloodSignalType.NEUTRAL -> "Neutral - 중립"
    }

    /**
     * Get health status
     */
    fun getHealthStatus(): String = when (signalType) {
        BloodSignalType.RISK_ON -> "Healthy"
        BloodSignalType.RISK_OFF -> "Stressed"
        BloodSignalType.NEUTRAL -> "Neutral"
    }

    /**
     * Check if current value is above SMA
     */
    fun isAboveSma(): Boolean = bloodValue > bloodSma
}

/**
 * Blood Indicator Signal Type
 */
enum class BloodSignalType(val code: String, val displayName: String) {
    RISK_ON("RISK_ON", "Risk On"),
    RISK_OFF("RISK_OFF", "Risk Off"),
    NEUTRAL("NEUTRAL", "Neutral");

    companion object {
        fun fromCode(code: String): BloodSignalType =
            entries.find { it.code == code } ?: NEUTRAL
    }
}
