package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * 최적화된 Holding 엔티티
 * - Float → Short/Int 변환으로 용량 절감
 * - 스냅샷 타입으로 델타 압축 지원
 */
@Entity(
    tableName = "holdings",
    primaryKeys = ["etfTicker", "stockTicker", "date"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["etfTicker"]),
        Index(value = ["etfTicker", "date"]),
        Index(value = ["etfTicker", "stockTicker"]),
        Index(value = ["stockTicker", "date"]),
        Index(value = ["snapshotType"]),
        Index(value = ["date", "snapshotType"])
    ]
)
data class Holding(
    val etfTicker: String,
    val stockTicker: String,
    val stockName: String,
    val date: String,
    val weightBps: Short, // 비중을 basis point (0.01%)로 저장: 5.25% → 525
    val amountMillion: Int, // 금액을 백만원 단위로 저장: 1,234,567,890 → 1234
    val snapshotType: String // "DAILY", "WEEKLY", "MONTHLY"
) {
    // Float 값으로 변환하는 헬퍼 함수
    val weight: Float
        get() = weightBps.toFloat() / 10000f

    val amount: Float
        get() = amountMillion.toFloat() * 1_000_000f

    companion object {
        // Float → Short/Int 변환 헬퍼
        fun Float.toBps(): Short = (this * 10000).toInt().coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
        fun Float.toMillion(): Int = (this / 1_000_000).toInt()

        // 편의 생성자
        fun create(
            etfTicker: String,
            stockTicker: String,
            stockName: String,
            date: String,
            weight: Float,
            amount: Float,
            snapshotType: SnapshotType = SnapshotType.DAILY
        ): Holding {
            return Holding(
                etfTicker = etfTicker,
                stockTicker = stockTicker,
                stockName = stockName,
                date = date,
                weightBps = weight.toBps(),
                amountMillion = amount.toMillion(),
                snapshotType = snapshotType.value
            )
        }
    }
}

/**
 * 스냅샷 타입 정의
 * - DAILY: 최근 1년 데이터 (일별 전체)
 * - WEEKLY: 1~3년 데이터 (주별 스냅샷)
 * - MONTHLY: 3~5년 데이터 (월별 스냅샷)
 */
enum class SnapshotType(val value: String) {
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY");

    companion object {
        fun fromValue(value: String): SnapshotType {
            return values().find { it.value == value } ?: DAILY
        }
    }
}

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
