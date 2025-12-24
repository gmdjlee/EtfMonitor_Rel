package com.etfmonitor.feature.market.domain.model

/**
 * 시장 지수 도메인 모델
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
