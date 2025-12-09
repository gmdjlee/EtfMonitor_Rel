package com.etfmonitor.repository

import com.etfmonitor.ai.AIApiClientFactory
import com.etfmonitor.analysis.*
import com.etfmonitor.database.*
import com.etfmonitor.database.entities.*
import com.etfmonitor.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 시계열 분석 Repository
 * 모든 수집된 데이터를 시계열로 통합하여 분석
 */
@Singleton
class TimeSeriesAnalysisRepository @Inject constructor(
    private val marketIndexDao: MarketIndexDao,
    private val fearGreedDao: FearGreedDao,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val marketDepositDao: MarketDepositDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val aiApiClientFactory: AIApiClientFactory
) {
    companion object {
        private val logger = AppLogger.getLogger("TimeSeriesRepo")
        private const val DEFAULT_PERIOD_DAYS = 30
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * 시계열 데이터 수집
     * @param market 시장 (KOSPI/KOSDAQ)
     * @param periodDays 분석 기간 (일)
     * @return TimeSeriesData
     */
    suspend fun collectTimeSeriesData(
        market: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<TimeSeriesData> = withContext(Dispatchers.IO) {
        try {
            logger.d("Collecting time series data for $market, $periodDays days")

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(periodDays.toLong())
            val startDateStr = startDate.format(dateFormatter)
            val endDateStr = endDate.format(dateFormatter)

            // 병렬로 모든 데이터 소스에서 데이터 수집
            val (marketIndices, fearGreedData, oscillatorData, depositData, etfStats) = coroutineScope {
                val marketIndicesDeferred = async {
                    marketIndexDao.getByMarketAndDateRangeSuspend(market, startDateStr, endDateStr)
                }
                val fearGreedDeferred = async {
                    fearGreedDao.getByMarketAndDateRange(market, startDateStr, endDateStr).first()
                }
                val oscillatorDeferred = async {
                    marketOscillatorDao.getDataByDateRange(market, startDateStr, endDateStr).first()
                }
                val depositDeferred = async {
                    marketDepositDao.getAllDeposits().first()
                        .filter { it.date in startDateStr..endDateStr }
                }
                val etfStatsDeferred = async {
                    dailyEtfStatisticsDao.getByDateRangeSuspend(startDateStr, endDateStr)
                }

                Tuple5(
                    marketIndicesDeferred.await(),
                    fearGreedDeferred.await(),
                    oscillatorDeferred.await(),
                    depositDeferred.await(),
                    etfStatsDeferred.await()
                )
            }

            // 모든 날짜 수집 및 정렬
            val allDates = (
                marketIndices.map { it.date } +
                fearGreedData.map { it.date } +
                oscillatorData.map { it.date } +
                depositData.map { it.date } +
                etfStats.map { it.date }
            ).distinct().sorted()

            // 각 날짜별로 데이터 포인트 생성
            val dataPoints = allDates.map { date ->
                TimeSeriesDataPoint(
                    date = date,
                    marketIndex = marketIndices.find { it.date == date }?.toPoint(),
                    fearGreed = fearGreedData.find { it.date == date }?.toPoint(),
                    oscillator = oscillatorData.find { it.date == date }?.toPoint(),
                    deposit = depositData.find { it.date == date }?.toPoint(),
                    etfStatistics = etfStats.find { it.date == date }?.toPoint()
                )
            }

            val timeSeriesData = TimeSeriesData(
                market = market,
                startDate = allDates.firstOrNull() ?: startDateStr,
                endDate = allDates.lastOrNull() ?: endDateStr,
                dataPoints = dataPoints
            )

            logger.d("Collected ${dataPoints.size} data points for $market")
            Result.success(timeSeriesData)

        } catch (e: Exception) {
            logger.e("Failed to collect time series data", e)
            Result.failure(e)
        }
    }

    /**
     * 시계열 데이터 분석 수행
     * 추세, 상관관계, 이상치 분석
     */
    suspend fun analyzeTimeSeries(
        timeSeriesData: TimeSeriesData
    ): Result<TimeSeriesAnalysisResult> = withContext(Dispatchers.Default) {
        try {
            logger.d("Analyzing time series data for ${timeSeriesData.market}")

            // 추세 분석
            val trends = analyzeTrends(timeSeriesData)

            // 상관관계 분석
            val correlations = analyzeCorrelations(timeSeriesData)

            // 이상치 탐지
            val anomalies = detectAnomalies(timeSeriesData)

            // 요약 생성
            val summary = generateSummary(timeSeriesData, trends, correlations, anomalies)

            Result.success(
                TimeSeriesAnalysisResult(
                    timeSeriesData = timeSeriesData,
                    trends = trends,
                    correlations = correlations,
                    anomalies = anomalies,
                    summary = summary
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to analyze time series", e)
            Result.failure(e)
        }
    }

    /**
     * AI를 활용한 시계열 분석 해석
     */
    suspend fun interpretWithAI(
        analysisResult: TimeSeriesAnalysisResult
    ): Result<AITimeSeriesInterpretation> = withContext(Dispatchers.IO) {
        try {
            logger.d("Interpreting time series with AI")

            val client = aiApiClientFactory.getClient()
            val prompt = createTimeSeriesPrompt(analysisResult)

            val signalResult = client.analyzeMarket(prompt, temperature = 0.5)

            if (signalResult.isFailure) {
                return@withContext Result.failure(
                    signalResult.exceptionOrNull() ?: Exception("AI 분석 실패")
                )
            }

            val signal = signalResult.getOrThrow()

            val interpretation = AITimeSeriesInterpretation(
                market = analysisResult.timeSeriesData.market,
                period = "${analysisResult.timeSeriesData.startDate} ~ ${analysisResult.timeSeriesData.endDate}",
                signal = signal.signal.name,
                confidence = signal.confidence,
                upProbability = signal.upProbability,
                downProbability = signal.downProbability,
                riskLevel = signal.riskLevel.name,
                trendSummary = extractTrendSummary(signal.reasoning),
                keyInsights = signal.keyFactors,
                recommendation = signal.recommendation,
                reasoning = signal.reasoning
            )

            Result.success(interpretation)
        } catch (e: Exception) {
            logger.e("Failed to interpret with AI", e)
            Result.failure(e)
        }
    }

    /**
     * 전체 시계열 분석 실행 (데이터 수집 + 분석 + AI 해석)
     */
    suspend fun runFullTimeSeriesAnalysis(
        market: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<FullTimeSeriesAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // 1. 데이터 수집
            val dataResult = collectTimeSeriesData(market, periodDays)
            if (dataResult.isFailure) {
                return@withContext Result.failure(
                    dataResult.exceptionOrNull() ?: Exception("데이터 수집 실패")
                )
            }
            val timeSeriesData = dataResult.getOrThrow()

            // 2. 분석 수행
            val analysisResult = analyzeTimeSeries(timeSeriesData)
            if (analysisResult.isFailure) {
                return@withContext Result.failure(
                    analysisResult.exceptionOrNull() ?: Exception("분석 실패")
                )
            }
            val analysis = analysisResult.getOrThrow()

            // 3. AI 해석
            val aiResult = interpretWithAI(analysis)

            Result.success(
                FullTimeSeriesAnalysisResult(
                    analysisResult = analysis,
                    aiInterpretation = aiResult.getOrNull(),
                    errorMessage = aiResult.exceptionOrNull()?.message
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to run full time series analysis", e)
            Result.failure(e)
        }
    }

    // ========== Private Helper Methods ==========

    private fun analyzeTrends(data: TimeSeriesData): List<TrendAnalysis> {
        val trends = mutableListOf<TrendAnalysis>()

        // 주요 지표별 추세 분석
        val indicatorsToAnalyze = listOf(
            TimeSeriesIndicator.MARKET_INDEX,
            TimeSeriesIndicator.FEAR_GREED,
            TimeSeriesIndicator.OSCILLATOR,
            TimeSeriesIndicator.DEPOSIT_AMOUNT,
            TimeSeriesIndicator.ETF_NET_FLOW
        )

        for (indicator in indicatorsToAnalyze) {
            val values = data.getSeriesValues(indicator).filterNotNull()
            if (values.size >= 5) {
                val trend = calculateTrend(values)
                trends.add(
                    TrendAnalysis(
                        indicator = indicator.displayName,
                        direction = trend.direction,
                        strength = trend.strength,
                        recentChange = trend.recentChange,
                        description = trend.description
                    )
                )
            }
        }

        return trends
    }

    private fun calculateTrend(values: List<Double>): TrendResult {
        if (values.isEmpty()) {
            return TrendResult(TrendDirection.STABLE, 0.0, 0.0, "데이터 부족")
        }

        val n = values.size
        val xMean = (n - 1) / 2.0
        val yMean = values.average()

        // 선형 회귀 기울기 계산
        var numerator = 0.0
        var denominator = 0.0
        for (i in values.indices) {
            numerator += (i - xMean) * (values[i] - yMean)
            denominator += (i - xMean) * (i - xMean)
        }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0

        // 기울기를 정규화 (-1 ~ 1)
        val normalizedSlope = if (yMean != 0.0) {
            (slope * n / yMean).coerceIn(-1.0, 1.0)
        } else {
            slope.coerceIn(-1.0, 1.0)
        }

        // 최근 변화율 계산
        val recentChange = if (values.size >= 2) {
            val recent = values.takeLast(5)
            if (recent.first() != 0.0) {
                ((recent.last() - recent.first()) / recent.first()) * 100
            } else 0.0
        } else 0.0

        // 방향 결정
        val direction = when {
            normalizedSlope > 0.3 -> TrendDirection.STRONG_UP
            normalizedSlope > 0.1 -> TrendDirection.UP
            normalizedSlope < -0.3 -> TrendDirection.STRONG_DOWN
            normalizedSlope < -0.1 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }

        val description = direction.displayName

        return TrendResult(direction, normalizedSlope, recentChange, description)
    }

    private fun analyzeCorrelations(data: TimeSeriesData): List<CorrelationPair> {
        val correlations = mutableListOf<CorrelationPair>()

        // 주요 상관관계 쌍
        val pairs = listOf(
            TimeSeriesIndicator.FEAR_GREED to TimeSeriesIndicator.INDEX_CHANGE_RATE,
            TimeSeriesIndicator.OSCILLATOR to TimeSeriesIndicator.INDEX_CHANGE_RATE,
            TimeSeriesIndicator.DEPOSIT_CHANGE to TimeSeriesIndicator.INDEX_CHANGE_RATE,
            TimeSeriesIndicator.ETF_NET_FLOW to TimeSeriesIndicator.INDEX_CHANGE_RATE,
            TimeSeriesIndicator.FEAR_GREED to TimeSeriesIndicator.OSCILLATOR,
            TimeSeriesIndicator.DEPOSIT_AMOUNT to TimeSeriesIndicator.MARKET_INDEX
        )

        for ((ind1, ind2) in pairs) {
            val values1 = data.getSeriesValues(ind1).filterNotNull()
            val values2 = data.getSeriesValues(ind2).filterNotNull()

            val minSize = minOf(values1.size, values2.size)
            if (minSize >= 10) {
                val corr = calculatePearsonCorrelation(
                    values1.takeLast(minSize),
                    values2.takeLast(minSize)
                )

                if (!corr.isNaN()) {
                    correlations.add(
                        CorrelationPair(
                            indicator1 = ind1.displayName,
                            indicator2 = ind2.displayName,
                            correlation = corr,
                            significance = calculateSignificance(corr, minSize)
                        )
                    )
                }
            }
        }

        return correlations.sortedByDescending { abs(it.correlation) }
    }

    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) return Double.NaN

        val n = x.size
        val xMean = x.average()
        val yMean = y.average()

        var numerator = 0.0
        var xDenom = 0.0
        var yDenom = 0.0

        for (i in 0 until n) {
            val xDiff = x[i] - xMean
            val yDiff = y[i] - yMean
            numerator += xDiff * yDiff
            xDenom += xDiff * xDiff
            yDenom += yDiff * yDiff
        }

        val denominator = sqrt(xDenom) * sqrt(yDenom)
        return if (denominator != 0.0) numerator / denominator else 0.0
    }

    private fun calculateSignificance(correlation: Double, n: Int): Double {
        // t-통계량 기반 유의성 계산
        if (n <= 2) return 0.0
        val t = correlation * sqrt((n - 2).toDouble() / (1 - correlation * correlation))
        // 간소화된 p-value 추정
        return (1 - abs(correlation)).coerceIn(0.0, 1.0)
    }

    private fun detectAnomalies(data: TimeSeriesData): List<AnomalyPoint> {
        val anomalies = mutableListOf<AnomalyPoint>()

        val indicatorsToCheck = listOf(
            TimeSeriesIndicator.INDEX_CHANGE_RATE,
            TimeSeriesIndicator.FEAR_GREED,
            TimeSeriesIndicator.OSCILLATOR,
            TimeSeriesIndicator.DEPOSIT_CHANGE
        )

        for (indicator in indicatorsToCheck) {
            val values = data.getSeriesValues(indicator)
            val nonNullValues = values.filterNotNull()

            if (nonNullValues.size >= 10) {
                val mean = nonNullValues.average()
                val stdDev = calculateStdDev(nonNullValues)

                data.dataPoints.forEachIndexed { index, point ->
                    val value = values[index]
                    if (value != null) {
                        val zScore = if (stdDev != 0.0) abs(value - mean) / stdDev else 0.0

                        if (zScore > 2.0) {
                            val severity = when {
                                zScore > 3.0 -> AnomalySeverity.HIGH
                                zScore > 2.5 -> AnomalySeverity.MEDIUM
                                else -> AnomalySeverity.LOW
                            }

                            anomalies.add(
                                AnomalyPoint(
                                    date = point.date,
                                    indicator = indicator.displayName,
                                    value = value,
                                    expectedRange = Pair(mean - 2 * stdDev, mean + 2 * stdDev),
                                    severity = severity
                                )
                            )
                        }
                    }
                }
            }
        }

        return anomalies.sortedByDescending { it.severity.ordinal }
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun generateSummary(
        data: TimeSeriesData,
        trends: List<TrendAnalysis>,
        correlations: List<CorrelationPair>,
        anomalies: List<AnomalyPoint>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("## ${data.market} 시계열 분석 요약")
        sb.appendLine("분석 기간: ${data.startDate} ~ ${data.endDate} (${data.totalDays}일)")
        sb.appendLine()

        // 추세 요약
        if (trends.isNotEmpty()) {
            sb.appendLine("### 추세 분석")
            trends.forEach { trend ->
                val arrow = when (trend.direction) {
                    TrendDirection.STRONG_UP -> "⬆⬆"
                    TrendDirection.UP -> "⬆"
                    TrendDirection.STABLE -> "➡"
                    TrendDirection.DOWN -> "⬇"
                    TrendDirection.STRONG_DOWN -> "⬇⬇"
                }
                sb.appendLine("- ${trend.indicator}: ${trend.description} $arrow (최근 변화: ${String.format("%+.1f", trend.recentChange)}%)")
            }
            sb.appendLine()
        }

        // 상관관계 요약
        val significantCorrelations = correlations.filter { abs(it.correlation) >= 0.3 }
        if (significantCorrelations.isNotEmpty()) {
            sb.appendLine("### 주요 상관관계")
            significantCorrelations.take(5).forEach { corr ->
                val strength = when {
                    abs(corr.correlation) >= 0.7 -> "강한"
                    abs(corr.correlation) >= 0.5 -> "중간"
                    else -> "약한"
                }
                val direction = if (corr.correlation > 0) "양의" else "음의"
                sb.appendLine("- ${corr.indicator1} vs ${corr.indicator2}: $strength $direction 상관관계 (${String.format("%.2f", corr.correlation)})")
            }
            sb.appendLine()
        }

        // 이상치 요약
        val highAnomalies = anomalies.filter { it.severity == AnomalySeverity.HIGH }
        if (highAnomalies.isNotEmpty()) {
            sb.appendLine("### 주의가 필요한 이상치")
            highAnomalies.take(3).forEach { anomaly ->
                sb.appendLine("- ${anomaly.date}: ${anomaly.indicator}에서 이상치 탐지 (값: ${String.format("%.2f", anomaly.value)})")
            }
        }

        return sb.toString()
    }

    private fun createTimeSeriesPrompt(result: TimeSeriesAnalysisResult): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 전문 애널리스트입니다.")
            appendLine("다음 시계열 데이터를 분석하여 시장 전망을 제공해주세요.")
            appendLine()
            appendLine("## 분석 개요")
            appendLine("- 시장: ${result.timeSeriesData.market}")
            appendLine("- 분석 기간: ${result.timeSeriesData.startDate} ~ ${result.timeSeriesData.endDate}")
            appendLine("- 총 데이터 포인트: ${result.timeSeriesData.totalDays}일")
            appendLine()

            // 추세 정보
            if (result.trends.isNotEmpty()) {
                appendLine("## 추세 분석 결과")
                result.trends.forEach { trend ->
                    appendLine("- ${trend.indicator}: ${trend.description}")
                    appendLine("  - 추세 강도: ${String.format("%.2f", trend.strength)}")
                    appendLine("  - 최근 변화율: ${String.format("%+.1f", trend.recentChange)}%")
                }
                appendLine()
            }

            // 상관관계 정보
            if (result.correlations.isNotEmpty()) {
                appendLine("## 상관관계 분석")
                result.correlations.take(5).forEach { corr ->
                    appendLine("- ${corr.indicator1} vs ${corr.indicator2}: ${String.format("%.3f", corr.correlation)}")
                }
                appendLine()
            }

            // 이상치 정보
            val highAnomalies = result.anomalies.filter { it.severity == AnomalySeverity.HIGH }
            if (highAnomalies.isNotEmpty()) {
                appendLine("## 주요 이상치")
                highAnomalies.take(3).forEach { anomaly ->
                    appendLine("- ${anomaly.date}: ${anomaly.indicator} = ${String.format("%.2f", anomaly.value)}")
                }
                appendLine()
            }

            // 최근 데이터 포인트
            val recentPoints = result.timeSeriesData.dataPoints.takeLast(5)
            appendLine("## 최근 5일 주요 지표")
            recentPoints.forEach { point ->
                appendLine("### ${point.date}")
                point.marketIndex?.let {
                    appendLine("- 지수: ${String.format("%.2f", it.closePrice)} (${String.format("%+.2f", it.changeRate)}%)")
                }
                point.fearGreed?.let {
                    appendLine("- Fear & Greed: ${String.format("%.2f", it.fearGreedValue)}")
                }
                point.oscillator?.let {
                    appendLine("- Oscillator: ${String.format("%.1f", it.oscillator)}")
                }
                point.deposit?.let {
                    appendLine("- 예탁금 변화: ${String.format("%+.0f", it.depositChange)}억원")
                }
                point.etfStatistics?.let {
                    appendLine("- ETF 순편입: ${it.newStockCount - it.removedStockCount}개")
                }
            }
            appendLine()

            appendLine("## 분석 요청")
            appendLine("위 시계열 데이터의 추세, 상관관계, 이상치를 종합적으로 분석하여")
            appendLine("다음 JSON 형식으로 투자 신호를 제공해주세요:")
            appendLine()
            appendLine("```json")
            appendLine("{")
            appendLine("  \"signal\": \"STRONG_BUY|BUY|NEUTRAL|SELL|STRONG_SELL\",")
            appendLine("  \"confidence\": 0.0-1.0,")
            appendLine("  \"upProbability\": 0-100,")
            appendLine("  \"downProbability\": 0-100,")
            appendLine("  \"reasoning\": \"시계열 분석 기반 상세 근거\",")
            appendLine("  \"keyFactors\": [\"주요 요인 1\", \"주요 요인 2\", \"주요 요인 3\"],")
            appendLine("  \"recommendation\": \"시계열 분석 기반 투자 권장사항\",")
            appendLine("  \"riskLevel\": \"LOW|MEDIUM|HIGH\"")
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine("**분석 시 고려사항:**")
            appendLine("1. 장기 추세와 단기 변동성을 구분하여 분석")
            appendLine("2. 지표 간 상관관계 변화가 시장 전환점을 암시할 수 있음")
            appendLine("3. 이상치 발생 패턴이 향후 방향성을 예측하는 데 도움")
            appendLine("4. 여러 지표의 방향성이 일치할 때 신뢰도 상향")
        }
    }

    private fun extractTrendSummary(reasoning: String): String {
        // AI 응답에서 추세 관련 핵심 문장 추출
        val lines = reasoning.split(".")
        val trendKeywords = listOf("추세", "상승", "하락", "횡보", "강세", "약세", "방향")
        return lines
            .filter { line -> trendKeywords.any { keyword -> line.contains(keyword) } }
            .take(2)
            .joinToString(". ")
            .ifEmpty { "추세 분석 결과를 참고하세요." }
    }

    // 엔티티 -> 데이터 포인트 변환 확장 함수
    private fun MarketIndex.toPoint() = MarketIndexPoint(
        closePrice = closePrice,
        openPrice = openPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        volume = volume,
        changeRate = changeRate
    )

    private fun FearGreedIndex.toPoint() = FearGreedPoint(
        fearGreedValue = fearGreedValue,
        rsi = rsi,
        momentum = momentum,
        putCallRatio = putCallRatio,
        volatility = volatility,
        spread = spread
    )

    private fun MarketOscillatorData.toPoint() = OscillatorPoint(
        indexValue = indexValue,
        oscillator = oscillator
    )

    private fun MarketDeposit.toPoint() = DepositPoint(
        depositAmount = depositAmount,
        depositChange = depositChange,
        creditAmount = creditAmount,
        creditChange = creditChange
    )

    private fun DailyEtfStatistics.toPoint() = EtfStatisticsPoint(
        newStockCount = newStockCount,
        newStockAmount = newStockAmount,
        removedStockCount = removedStockCount,
        removedStockAmount = removedStockAmount,
        increasedStockCount = increasedStockCount,
        decreasedStockCount = decreasedStockCount,
        cashDepositAmount = cashDepositAmount.toDouble(),
        cashDepositChangeRate = cashDepositChangeRate
    )
}

/**
 * 추세 분석 결과 (내부용)
 */
private data class TrendResult(
    val direction: TrendDirection,
    val strength: Double,
    val recentChange: Double,
    val description: String
)

/**
 * 5개 값 튜플 (병렬 수집용)
 */
private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * AI 시계열 해석 결과
 */
data class AITimeSeriesInterpretation(
    val market: String,
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

/**
 * 전체 시계열 분석 결과
 */
data class FullTimeSeriesAnalysisResult(
    val analysisResult: TimeSeriesAnalysisResult,
    val aiInterpretation: AITimeSeriesInterpretation?,
    val errorMessage: String?
)
