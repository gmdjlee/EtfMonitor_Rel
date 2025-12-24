package com.etfmonitor.feature.etf.domain.model

/**
 * ETF Domain Model
 *
 * 순수 도메인 객체로 데이터베이스/UI와 분리됨
 */
data class Etf(
    val ticker: String,
    val name: String
)
