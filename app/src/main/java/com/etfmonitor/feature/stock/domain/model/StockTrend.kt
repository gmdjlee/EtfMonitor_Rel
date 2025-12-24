package com.etfmonitor.feature.stock.domain.model

/**
 * Stock Trend Domain Model
 *
 * 특정 ETF 내 종목의 시계열 추이를 나타내는 도메인 모델입니다.
 *
 * @property etfTicker ETF 종목코드
 * @property stockTicker 종목코드
 * @property stockName 종목명
 * @property timeSeries 시계열 데이터 목록
 */
data class StockTrend(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val timeSeries: List<HoldingTimeSeries>
)

/**
 * Holding Time Series Data
 *
 * 보유 종목의 특정 날짜 데이터
 *
 * @property date 날짜 (yyyy-MM-dd)
 * @property weight 비중 (%)
 * @property amount 평가금액 (원)
 */
data class HoldingTimeSeries(
    val date: String,
    val weight: Float,
    val amount: Float
)
