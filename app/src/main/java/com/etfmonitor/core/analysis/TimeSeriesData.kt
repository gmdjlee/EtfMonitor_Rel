package com.etfmonitor.core.analysis

import com.etfmonitor.core.database.entities.TrendDirection
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

// ============================================================
// 종목 주가 시계열 분석 모델
// ============================================================

/**
 * 분석 대상 타입
 */
enum class AnalysisTargetType(val displayName: String) {
    INDEX("지수"),       // KOSPI/KOSDAQ 지수
    STOCK("종목")        // 개별 종목
}

/**
 * 종목 OHLCV 시계열 데이터
 */
@Serializable
data class StockTimeSeriesData(
    val ticker: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val dataPoints: List<StockTimeSeriesPoint>
) {
    val totalDays: Int get() = dataPoints.size

    fun getDates(): List<String> = dataPoints.map { it.date }
    fun getClosePrices(): List<Double> = dataPoints.map { it.close }
    fun getVolumes(): List<Long> = dataPoints.map { it.volume }
    fun getChangeRates(): List<Double> = dataPoints.map { it.changeRate }
}

/**
 * 종목 주가 데이터 포인트
 */
@Serializable
data class StockTimeSeriesPoint(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val changeRate: Double
)

/**
 * 종목 시계열 분석 결과
 */
@Serializable
data class StockTimeSeriesAnalysisResult(
    val stockData: StockTimeSeriesData,
    val priceTrend: TrendAnalysis,
    val volumeTrend: TrendAnalysis,
    val volatility: Double,
    val avgVolume: Long,
    val priceRange: Pair<Double, Double>,
    val anomalies: List<AnomalyPoint>,
    val summary: String
)

/**
 * 전체 종목 시계열 분석 결과 (데이터 + 분석 + AI 해석)
 */
data class FullStockTimeSeriesResult(
    val analysisResult: StockTimeSeriesAnalysisResult,
    val aiInterpretation: AIStockTimeSeriesInterpretation?,
    val errorMessage: String?
)

/**
 * AI 종목 시계열 해석 결과
 */
data class AIStockTimeSeriesInterpretation(
    val ticker: String,
    val name: String,
    val period: String,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val trendSummary: String,
    val keyInsights: List<String>,
    val recommendation: String,
    val reasoning: String
)

// ============================================================
// 종목-지표 상관관계 분석 모델
// ============================================================

/**
 * 종목-시장지표 상관관계 분석용 지표 타입
 */
enum class MarketIndicatorType(val displayName: String, val category: String) {
    // Fear & Greed 계열
    FEAR_GREED("Fear & Greed Index", "심리"),
    FEAR_GREED_RSI("RSI", "심리"),
    FEAR_GREED_MOMENTUM("모멘텀", "심리"),

    // 시장 Oscillator 계열
    OSCILLATOR("시장 과매수/과매도", "기술"),

    // 자금 동향 계열
    DEPOSIT_AMOUNT("고객예탁금", "자금"),
    DEPOSIT_CHANGE("예탁금 변화", "자금"),
    CREDIT_AMOUNT("신용잔고", "자금"),
    CREDIT_CHANGE("신용 변화", "자금"),

    // ETF 통계 계열
    ETF_NEW_STOCK_COUNT("ETF 신규편입 수", "ETF"),
    ETF_NEW_STOCK_AMOUNT("ETF 신규편입 금액", "ETF"),
    ETF_REMOVED_STOCK_COUNT("ETF 편출 수", "ETF"),
    ETF_REMOVED_STOCK_AMOUNT("ETF 편출 금액", "ETF"),
    ETF_INCREASED_COUNT("ETF 비중증가 수", "ETF"),
    ETF_DECREASED_COUNT("ETF 비중감소 수", "ETF"),
    ETF_NET_FLOW("ETF 순편입", "ETF"),
    ETF_CASH_DEPOSIT("ETF 원화예금", "ETF");

    companion object {
        fun getByCategory(category: String): List<MarketIndicatorType> =
            entries.filter { it.category == category }

        val categories: List<String> = entries.map { it.category }.distinct()
    }
}

/**
 * 종목 가격 지표 타입
 */
enum class StockMetricType(val displayName: String) {
    CLOSE_PRICE("종가"),
    CHANGE_RATE("등락률"),
    VOLUME("거래량"),
    MARKET_CAP("시가총액")    // ETF 보유금액 기준
}

/**
 * 종목-지표 상관관계 분석 요청
 */
data class StockIndicatorCorrelationRequest(
    val ticker: String,
    val name: String,
    val market: String = "KOSPI",  // Fear&Greed, Oscillator가 사용할 시장
    val periodDays: Int = 30
)

/**
 * 단일 지표-종목 상관관계 결과
 */
@Serializable
data class IndicatorStockCorrelation(
    val indicatorType: String,          // MarketIndicatorType.name
    val stockMetricType: String,        // StockMetricType.name
    val correlation: Double,            // 상관계수 (-1 ~ 1)
    val significance: Double,           // 유의성 (p-value 근사)
    val dataPoints: Int,                // 분석에 사용된 데이터 포인트 수
    val leadLagDays: Int = 0,           // 선행/후행 일수 (양수: 지표 선행, 음수: 주가 선행)
    val description: String             // 상관관계 해석
)

/**
 * 지표 카테고리별 상관관계 요약
 */
@Serializable
data class IndicatorCategoryCorrelation(
    val category: String,               // "심리", "기술", "자금", "ETF"
    val correlations: List<IndicatorStockCorrelation>,
    val summary: String                 // 카테고리별 요약
)

/**
 * 종목-지표 상관관계 전체 분석 결과
 */
@Serializable
data class StockIndicatorCorrelationResult(
    val ticker: String,
    val stockName: String,
    val market: String,
    val startDate: String,
    val endDate: String,
    val totalDataPoints: Int,

    // 카테고리별 상관관계
    val fearGreedCorrelations: List<IndicatorStockCorrelation>,
    val oscillatorCorrelations: List<IndicatorStockCorrelation>,
    val depositCorrelations: List<IndicatorStockCorrelation>,
    val etfCorrelations: List<IndicatorStockCorrelation>,

    // 가장 강한 상관관계 Top N
    val topPositiveCorrelations: List<IndicatorStockCorrelation>,
    val topNegativeCorrelations: List<IndicatorStockCorrelation>,

    // 분석 요약
    val summary: String
)

/**
 * AI 해석이 포함된 종목-지표 상관관계 분석 결과
 */
data class FullStockIndicatorCorrelationResult(
    val correlationResult: StockIndicatorCorrelationResult?,
    val aiInterpretation: AIStockIndicatorInterpretation?,
    val errorMessage: String?
)

/**
 * AI 종목-지표 상관관계 해석
 */
data class AIStockIndicatorInterpretation(
    val ticker: String,
    val name: String,
    val period: String,
    val signal: String,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double,
    val riskLevel: String,
    val keyCorrelations: List<String>,      // 핵심 상관관계 설명
    val marketSentimentImpact: String,      // 시장 심리 영향 분석
    val fundFlowImpact: String,             // 자금 흐름 영향 분석
    val etfFlowImpact: String,              // ETF 수급 영향 분석
    val recommendation: String,
    val reasoning: String
)

/**
 * 종목별 ETF 보유 추이 데이터 (시계열)
 */
@Serializable
data class StockEtfHoldingTimeSeries(
    val ticker: String,
    val name: String,
    val dataPoints: List<StockEtfHoldingPoint>
) {
    val totalDays: Int get() = dataPoints.size

    fun getDates(): List<String> = dataPoints.map { it.date }
    fun getTotalAmounts(): List<Double> = dataPoints.map { it.totalAmount }
    fun getEtfCounts(): List<Int> = dataPoints.map { it.etfCount }
    fun getAmountChanges(): List<Double> = dataPoints.mapIndexed { index, point ->
        if (index == 0) 0.0
        else {
            val prev = dataPoints[index - 1].totalAmount
            if (prev != 0.0) ((point.totalAmount - prev) / prev) * 100 else 0.0
        }
    }
}

/**
 * 종목별 ETF 보유 데이터 포인트
 */
@Serializable
data class StockEtfHoldingPoint(
    val date: String,
    val totalAmount: Double,        // 전체 ETF 보유 금액
    val etfCount: Int,              // 보유 ETF 수
    val maxWeight: Double,          // 최대 비중
    val avgWeight: Double,          // 평균 비중
    val newEtfCount: Int = 0,       // 신규 편입 ETF 수
    val increasedCount: Int = 0,    // 비중 증가 ETF 수
    val decreasedCount: Int = 0,    // 비중 감소 ETF 수
    val removedCount: Int = 0       // 편출 ETF 수
)
