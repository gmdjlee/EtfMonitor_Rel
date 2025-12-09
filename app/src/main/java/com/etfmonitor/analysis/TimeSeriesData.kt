package com.etfmonitor.analysis

import com.etfmonitor.database.entities.TrendDirection
import kotlinx.serialization.Serializable

/**
 * 시계열 분석을 위한 통합 데이터 모델
 * 모든 수집된 시장 데이터를 시간 순서로 정렬하여 보관
 */
@Serializable
data class TimeSeriesData(
    val market: String,
    val startDate: String,
    val endDate: String,
    val dataPoints: List<TimeSeriesDataPoint>
) {
    val totalDays: Int get() = dataPoints.size

    /**
     * 특정 지표의 시계열 값 추출
     */
    fun getSeriesValues(indicator: TimeSeriesIndicator): List<Double?> {
        return dataPoints.map { point ->
            when (indicator) {
                TimeSeriesIndicator.MARKET_INDEX -> point.marketIndex?.closePrice
                TimeSeriesIndicator.INDEX_CHANGE_RATE -> point.marketIndex?.changeRate
                TimeSeriesIndicator.FEAR_GREED -> point.fearGreed?.fearGreedValue
                TimeSeriesIndicator.FEAR_GREED_RSI -> point.fearGreed?.rsi
                TimeSeriesIndicator.FEAR_GREED_MOMENTUM -> point.fearGreed?.momentum
                TimeSeriesIndicator.OSCILLATOR -> point.oscillator?.oscillator
                TimeSeriesIndicator.DEPOSIT_AMOUNT -> point.deposit?.depositAmount
                TimeSeriesIndicator.DEPOSIT_CHANGE -> point.deposit?.depositChange
                TimeSeriesIndicator.CREDIT_AMOUNT -> point.deposit?.creditAmount
                TimeSeriesIndicator.CREDIT_CHANGE -> point.deposit?.creditChange
                TimeSeriesIndicator.ETF_NEW_STOCKS -> point.etfStatistics?.newStockCount?.toDouble()
                TimeSeriesIndicator.ETF_REMOVED_STOCKS -> point.etfStatistics?.removedStockCount?.toDouble()
                TimeSeriesIndicator.ETF_NET_FLOW -> point.etfStatistics?.let {
                    (it.newStockCount - it.removedStockCount).toDouble()
                }
                TimeSeriesIndicator.ETF_CASH_DEPOSIT -> point.etfStatistics?.cashDepositAmount
                TimeSeriesIndicator.ETF_CASH_CHANGE_RATE -> point.etfStatistics?.cashDepositChangeRate
            }
        }
    }

    /**
     * 날짜 목록
     */
    fun getDates(): List<String> = dataPoints.map { it.date }

    /**
     * 통계 요약 생성
     */
    fun getSummary(): TimeSeriesSummary {
        val validPoints = dataPoints.filter { it.hasAnyData() }

        return TimeSeriesSummary(
            market = market,
            totalDays = totalDays,
            validDays = validPoints.size,
            startDate = startDate,
            endDate = endDate,
            hasMarketIndex = dataPoints.any { it.marketIndex != null },
            hasFearGreed = dataPoints.any { it.fearGreed != null },
            hasOscillator = dataPoints.any { it.oscillator != null },
            hasDeposit = dataPoints.any { it.deposit != null },
            hasEtfStatistics = dataPoints.any { it.etfStatistics != null }
        )
    }
}

/**
 * 시계열 데이터 포인트 (단일 날짜의 모든 지표)
 */
@Serializable
data class TimeSeriesDataPoint(
    val date: String,
    val marketIndex: MarketIndexPoint? = null,
    val fearGreed: FearGreedPoint? = null,
    val oscillator: OscillatorPoint? = null,
    val deposit: DepositPoint? = null,
    val etfStatistics: EtfStatisticsPoint? = null
) {
    fun hasAnyData(): Boolean =
        marketIndex != null || fearGreed != null || oscillator != null ||
        deposit != null || etfStatistics != null
}

/**
 * 시장 지수 데이터 포인트
 */
@Serializable
data class MarketIndexPoint(
    val closePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Long,
    val changeRate: Double
)

/**
 * Fear & Greed 지수 데이터 포인트
 */
@Serializable
data class FearGreedPoint(
    val fearGreedValue: Double,
    val rsi: Double,
    val momentum: Double,
    val putCallRatio: Double,
    val volatility: Double,
    val spread: Double
)

/**
 * 시장 Oscillator 데이터 포인트
 */
@Serializable
data class OscillatorPoint(
    val indexValue: Double,
    val oscillator: Double
)

/**
 * 자금 동향 데이터 포인트
 */
@Serializable
data class DepositPoint(
    val depositAmount: Double,
    val depositChange: Double,
    val creditAmount: Double,
    val creditChange: Double
)

/**
 * ETF 통계 데이터 포인트
 */
@Serializable
data class EtfStatisticsPoint(
    val newStockCount: Int,
    val newStockAmount: Long,
    val removedStockCount: Int,
    val removedStockAmount: Long,
    val increasedStockCount: Int,
    val decreasedStockCount: Int,
    val cashDepositAmount: Double,
    val cashDepositChangeRate: Double
)

/**
 * 시계열 데이터 요약
 */
@Serializable
data class TimeSeriesSummary(
    val market: String,
    val totalDays: Int,
    val validDays: Int,
    val startDate: String,
    val endDate: String,
    val hasMarketIndex: Boolean,
    val hasFearGreed: Boolean,
    val hasOscillator: Boolean,
    val hasDeposit: Boolean,
    val hasEtfStatistics: Boolean
)

/**
 * 시계열 지표 종류
 */
enum class TimeSeriesIndicator(val displayName: String, val category: String) {
    // 시장 지수
    MARKET_INDEX("시장 지수", "시장"),
    INDEX_CHANGE_RATE("등락률 (%)", "시장"),

    // Fear & Greed
    FEAR_GREED("Fear & Greed Index", "심리"),
    FEAR_GREED_RSI("RSI", "심리"),
    FEAR_GREED_MOMENTUM("모멘텀", "심리"),

    // Oscillator
    OSCILLATOR("시장 Oscillator", "기술"),

    // 자금
    DEPOSIT_AMOUNT("고객예탁금", "자금"),
    DEPOSIT_CHANGE("예탁금 변화", "자금"),
    CREDIT_AMOUNT("신용잔고", "자금"),
    CREDIT_CHANGE("신용 변화", "자금"),

    // ETF
    ETF_NEW_STOCKS("ETF 신규편입", "ETF"),
    ETF_REMOVED_STOCKS("ETF 편출", "ETF"),
    ETF_NET_FLOW("ETF 순편입", "ETF"),
    ETF_CASH_DEPOSIT("ETF 원화예금", "ETF"),
    ETF_CASH_CHANGE_RATE("원화예금 변화율", "ETF");

    companion object {
        fun getByCategory(category: String): List<TimeSeriesIndicator> =
            entries.filter { it.category == category }

        val categories: List<String> = entries.map { it.category }.distinct()
    }
}

/**
 * 시계열 분석 결과
 */
@Serializable
data class TimeSeriesAnalysisResult(
    val timeSeriesData: TimeSeriesData,
    val trends: List<TrendAnalysis>,
    val correlations: List<CorrelationPair>,
    val anomalies: List<AnomalyPoint>,
    val summary: String
)

/**
 * 추세 분석 결과
 */
@Serializable
data class TrendAnalysis(
    val indicator: String,
    val direction: TrendDirection,
    val strength: Double,       // -1.0 ~ 1.0
    val recentChange: Double,   // 최근 변화율
    val description: String
)

/**
 * 상관관계 쌍
 */
@Serializable
data class CorrelationPair(
    val indicator1: String,
    val indicator2: String,
    val correlation: Double,
    val significance: Double
)

/**
 * 이상치 포인트
 */
@Serializable
data class AnomalyPoint(
    val date: String,
    val indicator: String,
    val value: Double,
    val expectedRange: Pair<Double, Double>,
    val severity: AnomalySeverity
)

enum class AnomalySeverity {
    LOW, MEDIUM, HIGH
}
