package com.etfmonitor.feature.market.domain.model

/**
 * 증시 자금 도메인 모델
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
 * 증시 자금 동향 데이터 (리스트 기반)
 * UI에서 차트 및 통계 표시에 사용
 */
data class MarketDepositTrend(
    val dates: List<String>,
    val depositAmounts: List<Double>,
    val depositChanges: List<Double>,
    val creditAmounts: List<Double>,
    val creditChanges: List<Double>
) {
    val isEmpty: Boolean
        get() = dates.isEmpty()

    val size: Int
        get() = dates.size

    /**
     * 최신 데이터 인덱스
     */
    val latestIndex: Int
        get() = if (dates.isNotEmpty()) dates.size - 1 else -1

    /**
     * 최신 날짜
     */
    val latestDate: String?
        get() = dates.lastOrNull()

    companion object {
        val EMPTY = MarketDepositTrend(
            dates = emptyList(),
            depositAmounts = emptyList(),
            depositChanges = emptyList(),
            creditAmounts = emptyList(),
            creditChanges = emptyList()
        )
    }
}
