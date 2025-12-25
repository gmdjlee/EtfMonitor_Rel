package com.etfmonitor.feature.stock.domain.model

import com.etfmonitor.core.database.entities.HoldingStatus

/**
 * Stock Amount Ranking Domain Model
 *
 * 종목별 평가금액 합계 순위를 나타내는 도메인 모델입니다.
 *
 * @property stockTicker 종목코드
 * @property stockName 종목명
 * @property totalAmount 총 평가금액 (ETF 합계)
 * @property etfCount 보유 ETF 수
 * @property newEtfCount 신규 편입 ETF 수
 * @property increasedEtfCount 비중 증가 ETF 수
 * @property decreasedEtfCount 비중 감소 ETF 수
 * @property removedEtfCount 제외된 ETF 수
 */
data class StockAmountRanking(
    val stockTicker: String,
    val stockName: String,
    val totalAmount: Float,
    val etfCount: Int,
    val newEtfCount: Int,
    val increasedEtfCount: Int,
    val decreasedEtfCount: Int,
    val removedEtfCount: Int
)

/**
 * Stock Change Info Domain Model
 *
 * 신규 편입/제외된 종목 정보를 나타내는 도메인 모델입니다.
 *
 * @property stockTicker 종목코드
 * @property stockName 종목명
 * @property etfTicker ETF 종목코드
 * @property etfName ETF 이름
 * @property currentWeight 현재 비중 (%)
 * @property currentAmount 현재 평가금액
 * @property previousWeight 이전 비중 (%)
 * @property change 비중 변화 (%)
 */
data class StockChangeInfo(
    val stockTicker: String,
    val stockName: String,
    val etfTicker: String,
    val etfName: String,
    val currentWeight: Float,
    val currentAmount: Float,
    val previousWeight: Float = 0f,
    val change: Float = 0f
)

/**
 * Stock Analysis Result Domain Model
 *
 * 특정 종목의 ETF별 보유 분석 결과입니다.
 *
 * @property stockTicker 종목코드
 * @property stockName 종목명
 * @property etfDetails ETF별 보유 상세
 * @property totalAmount 총 평가금액
 * @property currentEtfCount 현재 포함된 ETF 수
 * @property previousEtfCount 이전 포함된 ETF 수
 * @property increasedCount 비중 증가 ETF 수
 * @property decreasedCount 비중 감소 ETF 수
 * @property newIncludedCount 신규 편입 ETF 수
 * @property removedCount 제외된 ETF 수
 * @property avgWeight 평균 비중
 * @property maxWeight 최대 비중
 */
data class StockAnalysisResult(
    val stockTicker: String,
    val stockName: String,
    val etfDetails: List<StockEtfDetail>,
    val totalAmount: Float,
    val currentEtfCount: Int,
    val previousEtfCount: Int = 0,
    val increasedCount: Int = 0,
    val decreasedCount: Int = 0,
    val newIncludedCount: Int = 0,
    val removedCount: Int = 0,
    val avgWeight: Float = 0f,
    val maxWeight: Float = 0f
)

/**
 * ETF Detail for Stock Analysis (with status)
 *
 * @property etfTicker ETF 종목코드
 * @property etfName ETF 이름
 * @property previousWeight 이전 비중 (%)
 * @property currentWeight 현재 비중 (%)
 * @property change 비중 변화 (%)
 * @property amount 평가금액
 * @property status 보유 상태
 */
data class StockEtfDetail(
    val etfTicker: String,
    val etfName: String,
    val previousWeight: Float = 0f,
    val currentWeight: Float,
    val change: Float = 0f,
    val amount: Float,
    val status: HoldingStatus = HoldingStatus.MAINTAINED
)

/**
 * Simple ETF Detail (for basic usage)
 *
 * @property etfTicker ETF 종목코드
 * @property etfName ETF 이름
 * @property weight 비중 (%)
 * @property amount 평가금액
 */
data class EtfDetail(
    val etfTicker: String,
    val etfName: String,
    val weight: Float,
    val amount: Float
)

/**
 * Cash Deposit Trend Domain Model
 *
 * 원화예금 추이 데이터입니다.
 *
 * @property date 날짜
 * @property totalAmount 총 평가금액
 * @property etfCount 보유 ETF 수
 */
data class CashDepositTrend(
    val date: String,
    val totalAmount: Float,
    val etfCount: Int
)

/**
 * Stock Aggregated Trend Domain Model
 *
 * 특정 종목의 일별 통합 추이 데이터입니다.
 *
 * @property stockTicker 종목코드
 * @property stockName 종목명
 * @property timeSeries 시계열 데이터
 */
data class StockAggregatedTrend(
    val stockTicker: String,
    val stockName: String,
    val timeSeries: List<StockAggregatedTimePoint>
)

/**
 * Stock Aggregated Time Point Domain Model
 *
 * 특정 날짜의 종목 통합 데이터입니다.
 *
 * @property date 날짜
 * @property totalAmount 총 평가금액
 * @property etfCount 보유 ETF 수
 * @property maxWeight 최대 비중 (%)
 * @property avgWeight 평균 비중 (%)
 */
data class StockAggregatedTimePoint(
    val date: String,
    val totalAmount: Float,
    val etfCount: Int,
    val maxWeight: Float,
    val avgWeight: Float
)
