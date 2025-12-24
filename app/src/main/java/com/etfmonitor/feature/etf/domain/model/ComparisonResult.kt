package com.etfmonitor.feature.etf.domain.model

/**
 * ETF Comparison Result Domain Model
 *
 * ETF의 보유 종목 비교 분석 결과를 나타냅니다.
 */
data class ComparisonResult(
    val etfTicker: String,
    /** 현재 날짜 (yyyy-MM-dd) */
    val currentDate: String,
    /** 이전 날짜 (yyyy-MM-dd) */
    val previousDate: String,
    /** 보유 종목 비교 리스트 */
    val items: List<HoldingWithComparison>,
    /** 수집 시작일 */
    val collectionStartDate: String = "",
    /** 수집 종료일 */
    val collectionEndDate: String = ""
)
