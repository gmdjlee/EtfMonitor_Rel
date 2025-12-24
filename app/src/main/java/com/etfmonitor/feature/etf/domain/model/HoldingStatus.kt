package com.etfmonitor.feature.etf.domain.model

/**
 * Holding Status Domain Enum
 *
 * ETF 보유 종목의 변화 상태를 나타냅니다.
 */
enum class HoldingStatus {
    /** 신규 편입 */
    NEW,
    /** 비중 증가 */
    INCREASE,
    /** 비중 감소 */
    DECREASE,
    /** 비중 유지 */
    MAINTAIN,
    /** 제외됨 */
    REMOVED
}
