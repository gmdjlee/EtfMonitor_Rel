package com.etfmonitor.feature.market.domain.model

/**
 * Fear & Greed Index 도메인 모델
 */
data class FearGreed(
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
     * Fear & Greed 상태 (0-100 스케일)
     */
    val status: FearGreedStatus
        get() = when {
            fearGreedValue * 100 >= 70 -> FearGreedStatus.GREED
            fearGreedValue * 100 <= 30 -> FearGreedStatus.FEAR
            else -> FearGreedStatus.NEUTRAL
        }

    /**
     * Fear & Greed 값 (0-100 스케일)
     */
    val scaledValue: Int
        get() = (fearGreedValue * 100).toInt()
}

/**
 * Fear & Greed 상태
 */
enum class FearGreedStatus(val displayName: String) {
    GREED("Greed (탐욕)"),
    NEUTRAL("Neutral (중립)"),
    FEAR("Fear (공포)")
}

/**
 * Fear & Greed 기간 옵션
 */
data class FearGreedPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

/**
 * 기본 기간 옵션
 */
val DEFAULT_FEAR_GREED_PERIOD_OPTIONS = listOf(
    FearGreedPeriodOption(180, "6개월", "약 180일"),
    FearGreedPeriodOption(365, "12개월 (권장)", "약 365일"),
    FearGreedPeriodOption(540, "18개월", "약 540일"),
    FearGreedPeriodOption(730, "24개월", "약 730일")
)
