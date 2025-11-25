package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "holdings",
    primaryKeys = ["etfTicker", "stockTicker", "date"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["etfTicker"]),
        Index(value = ["etfTicker", "date"]),
        Index(value = ["etfTicker", "stockTicker"]),
        Index(value = ["stockTicker", "date"])  // ✅ 추가
    ]
)
data class Holding(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val date: String,
    val weight: Float,
    val amount: Float
)

data class HoldingWithComparison(
    val stockTicker: String,
    val stockName: String,
    val previousWeight: Float,
    val currentWeight: Float,
    val change: Float,
    val currentAmount: Float,
    val status: HoldingStatus
)

enum class HoldingStatus {
    NEW,
    INCREASE,
    DECREASE,
    MAINTAIN,
    REMOVED
}

data class HoldingTimeSeries(
    val date: String,
    val weight: Float,
    val amount: Float
)

data class OverlapStock(
    val stockTicker: String,
    val stockName: String,
    val etfCount: Int,
    val totalAmount: Float,
    val etfList: String
)

data class AmountRank(
    val stockTicker: String,
    val stockName: String,
    val etfName: String,
    val weight: Float,
    val amount: Float
)

// ✅ 전체 통계용 데이터 클래스 추가
data class StockAmountRanking(
    val stockTicker: String,
    val stockName: String,
    val totalAmount: Float,
    val etfCount: Int,
    val maxWeight: Float,
    val etfList: String,
    val newEtfCount: Int = 0,        // 신규 편입 ETF 수
    val increasedEtfCount: Int = 0,  // 비중 증가 ETF 수
    val decreasedEtfCount: Int = 0,  // 비중 감소 ETF 수
    val removedEtfCount: Int = 0     // 제외된 ETF 수
)

data class StockChangeInfo(
    val stockTicker: String,
    val stockName: String,
    val etfTicker: String,
    val etfName: String,
    val previousWeight: Float,
    val currentWeight: Float,
    val change: Float,
    val currentAmount: Float
)

// ✅ 원화예금 추이 데이터
data class CashDepositTrend(
    val date: String,
    val totalAmount: Float,
    val etfCount: Int
)

// ✅ 종목의 전체 ETF 통합 추이
data class StockAggregatedTrend(
    val stockTicker: String,
    val stockName: String,
    val timeSeries: List<StockAggregatedTimePoint>
)

data class StockAggregatedTimePoint(
    val date: String,
    val totalAmount: Float,
    val etfCount: Int,
    val maxWeight: Float,
    val avgWeight: Float
)

// ✅ 종목 분석 결과
data class StockAnalysisResult(
    val stockTicker: String,
    val stockName: String,
    val currentEtfCount: Int,  // 현재 포함된 ETF 수
    val previousEtfCount: Int,  // 이전 포함된 ETF 수
    val increasedCount: Int,  // 비중 증가 ETF 수
    val decreasedCount: Int,  // 비중 감소 ETF 수
    val newIncludedCount: Int,  // 신규 편입 ETF 수
    val removedCount: Int,  // 제외된 ETF 수
    val totalAmount: Float,  // 현재 총 평가금액
    val avgWeight: Float,  // 평균 비중
    val maxWeight: Float,  // 최대 비중
    val etfDetails: List<StockEtfDetail>  // ETF별 상세 정보
)

data class StockEtfDetail(
    val etfTicker: String,
    val etfName: String,
    val previousWeight: Float,
    val currentWeight: Float,
    val change: Float,
    val amount: Float,
    val status: HoldingStatus
)