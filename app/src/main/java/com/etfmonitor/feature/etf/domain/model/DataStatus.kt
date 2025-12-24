package com.etfmonitor.feature.etf.domain.model

/**
 * Data Status Domain Model
 *
 * ETF 데이터 상태 정보를 나타냅니다.
 */
data class DataStatus(
    val hasData: Boolean,
    val latestDate: String?
)
