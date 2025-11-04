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
    val etfList: String
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