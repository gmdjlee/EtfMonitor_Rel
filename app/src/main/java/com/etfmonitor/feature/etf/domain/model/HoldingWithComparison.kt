package com.etfmonitor.feature.etf.domain.model

/**
 * Holding with Comparison Domain Model
 *
 * 두 기간 간 ETF 보유 종목의 비중 변화를 나타냅니다.
 * Entity의 압축 저장(weightBps, amountMillion)을 실제 Float 값으로 변환하여 제공합니다.
 */
data class HoldingWithComparison(
    val stockTicker: String,
    val stockName: String,
    /** 이전 비중 (%) - Float으로 직접 표현 */
    val previousWeight: Float,
    /** 현재 비중 (%) - Float으로 직접 표현 */
    val currentWeight: Float,
    /** 변화량 (percentage points) */
    val change: Float,
    /** 현재 평가금액 (원) - Float으로 직접 표현 */
    val currentAmount: Float,
    /** 변화 상태 */
    val status: HoldingStatus
)
