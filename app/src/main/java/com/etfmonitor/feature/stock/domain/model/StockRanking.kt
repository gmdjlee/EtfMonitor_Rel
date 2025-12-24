package com.etfmonitor.feature.stock.domain.model

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
 * @property weight 비중 (%)
 * @property amount 평가금액
 * @property previousWeight 이전 비중 (%)
 */
data class StockChangeInfo(
    val stockTicker: String,
    val stockName: String,
    val etfTicker: String,
    val etfName: String,
    val weight: Float,
    val amount: Float,
    val previousWeight: Float = 0f
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
 * @property etfCount 보유 ETF 수
 */
data class StockAnalysisResult(
    val stockTicker: String,
    val stockName: String,
    val etfDetails: List<EtfDetail>,
    val totalAmount: Float,
    val etfCount: Int
)

/**
 * ETF Detail for Stock Analysis
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
 * @property depositAmount 고객예탁금 (원)
 * @property creditAmount 신용잔고 (원)
 */
data class CashDepositTrend(
    val date: String,
    val depositAmount: Float,
    val creditAmount: Float
)
