package com.etfmonitor.repository

import com.etfmonitor.core.network.ai.AIApiClientFactory
import com.etfmonitor.core.analysis.*
import com.etfmonitor.core.database.*
import com.etfmonitor.core.database.entities.*
import com.etfmonitor.oscillator.model.StockOhlcvData
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
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
    private val aiApiClientFactory: AIApiClientFactory,
    private val oscillatorPyClient: OscillatorPyClient,
    private val etfDao: EtfDao,
    private val stockIndicatorAIResultDao: StockIndicatorAIResultDao
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

    /**
     * 선행/후행 상관관계 계산 (Cross-correlation)
     * 지표 시계열과 주가 시계열 간의 시차 상관관계를 계산하여
     * 최적의 시차(lag)를 찾음
     *
     * @param indicatorValues 지표 시계열 값
     * @param stockValues 주가 시계열 값
     * @param maxLag 최대 검사할 시차 (일)
     * @return LeadLagResult (최적 시차, 해당 상관계수, 동시 상관계수)
     */
    private fun calculateLeadLagCorrelation(
        indicatorValues: List<Double>,
        stockValues: List<Double>,
        maxLag: Int = 5
    ): LeadLagResult {
        if (indicatorValues.size != stockValues.size || indicatorValues.size < maxLag * 2 + 10) {
            return LeadLagResult(0, 0.0, 0.0)
        }

        // 동시 상관관계 계산
        val simultaneousCorr = calculatePearsonCorrelation(indicatorValues, stockValues)

        var bestLag = 0
        var bestCorrelation = simultaneousCorr
        var bestAbsCorrelation = abs(simultaneousCorr)

        // 양수 lag: 지표가 선행 (지표 변화 → 주가 변화)
        // 음수 lag: 지표가 후행 (주가 변화 → 지표 변화)
        for (lag in -maxLag..maxLag) {
            if (lag == 0) continue

            val (shiftedIndicator, shiftedStock) = if (lag > 0) {
                // 지표 선행: 과거 지표값과 현재 주가 비교
                val indicator = indicatorValues.dropLast(lag)
                val stock = stockValues.drop(lag)
                Pair(indicator, stock)
            } else {
                // 지표 후행: 현재 지표값과 과거 주가 비교
                val indicator = indicatorValues.drop(-lag)
                val stock = stockValues.dropLast(-lag)
                Pair(indicator, stock)
            }

            if (shiftedIndicator.size >= 10 && shiftedStock.size >= 10) {
                val minSize = minOf(shiftedIndicator.size, shiftedStock.size)
                val corr = calculatePearsonCorrelation(
                    shiftedIndicator.takeLast(minSize),
                    shiftedStock.takeLast(minSize)
                )

                if (!corr.isNaN() && abs(corr) > bestAbsCorrelation) {
                    bestAbsCorrelation = abs(corr)
                    bestCorrelation = corr
                    bestLag = lag
                }
            }
        }

        return LeadLagResult(bestLag, bestCorrelation, simultaneousCorr)
    }

    /**
     * 선행/후행 분석 결과
     */
    private data class LeadLagResult(
        val optimalLag: Int,      // 최적 시차 (양수: 지표 선행, 음수: 지표 후행)
        val optimalCorrelation: Double,  // 최적 시차의 상관계수
        val simultaneousCorrelation: Double  // 동시 상관계수
    )

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

    // ========== 종목 주가 시계열 분석 ==========

    /**
     * 종목 검색
     */
    suspend fun searchStock(query: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        oscillatorPyClient.searchStock(query)
    }

    /**
     * 전체 종목 리스트 가져오기
     */
    suspend fun getAllStocksList(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        oscillatorPyClient.getAllStocksList()
    }

    /**
     * 종목 OHLCV 시계열 데이터 수집
     */
    suspend fun collectStockTimeSeriesData(
        ticker: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<StockTimeSeriesData> = withContext(Dispatchers.IO) {
        try {
            logger.d("Collecting stock time series data for $ticker, $periodDays days")

            val ohlcvData = oscillatorPyClient.getStockOhlcv(ticker, periodDays, "d")

            if (ohlcvData == null || ohlcvData.dates.isEmpty()) {
                return@withContext Result.failure(Exception("종목 데이터를 가져올 수 없습니다: $ticker"))
            }

            // OHLCV 데이터를 시계열 포인트로 변환
            val dataPoints = ohlcvData.dates.mapIndexed { index, date ->
                val changeRate = if (index == 0) 0.0 else {
                    val prevClose = ohlcvData.close[index - 1]
                    if (prevClose != 0.0) {
                        ((ohlcvData.close[index] - prevClose) / prevClose) * 100
                    } else 0.0
                }

                StockTimeSeriesPoint(
                    date = date,
                    open = ohlcvData.open[index],
                    high = ohlcvData.high[index],
                    low = ohlcvData.low[index],
                    close = ohlcvData.close[index],
                    volume = ohlcvData.volume[index],
                    changeRate = changeRate
                )
            }

            val stockTimeSeriesData = StockTimeSeriesData(
                ticker = ohlcvData.ticker,
                name = ohlcvData.name,
                startDate = ohlcvData.dates.firstOrNull() ?: "",
                endDate = ohlcvData.dates.lastOrNull() ?: "",
                dataPoints = dataPoints
            )

            logger.d("Collected ${dataPoints.size} stock data points for ${ohlcvData.name}")
            Result.success(stockTimeSeriesData)

        } catch (e: Exception) {
            logger.e("Failed to collect stock time series data", e)
            Result.failure(e)
        }
    }

    /**
     * 종목 시계열 데이터 분석
     */
    suspend fun analyzeStockTimeSeries(
        stockData: StockTimeSeriesData
    ): Result<StockTimeSeriesAnalysisResult> = withContext(Dispatchers.Default) {
        try {
            logger.d("Analyzing stock time series data for ${stockData.name}")

            val closePrices = stockData.getClosePrices()
            val volumes = stockData.getVolumes()
            val changeRates = stockData.getChangeRates()

            // 가격 추세 분석
            val priceTrend = calculateTrend(closePrices)

            // 거래량 추세 분석
            val volumeTrend = calculateTrend(volumes.map { it.toDouble() })

            // 변동성 계산 (표준편차)
            val volatility = calculateStdDev(changeRates)

            // 평균 거래량
            val avgVolume = if (volumes.isNotEmpty()) volumes.average().toLong() else 0L

            // 가격 범위
            val priceRange = Pair(
                closePrices.minOrNull() ?: 0.0,
                closePrices.maxOrNull() ?: 0.0
            )

            // 이상치 탐지 (등락률 기준)
            val anomalies = detectStockAnomalies(stockData)

            // 요약 생성
            val summary = generateStockSummary(stockData, priceTrend, volumeTrend, volatility)

            Result.success(
                StockTimeSeriesAnalysisResult(
                    stockData = stockData,
                    priceTrend = TrendAnalysis(
                        indicator = "주가",
                        direction = priceTrend.direction,
                        strength = priceTrend.strength,
                        recentChange = priceTrend.recentChange,
                        description = priceTrend.description
                    ),
                    volumeTrend = TrendAnalysis(
                        indicator = "거래량",
                        direction = volumeTrend.direction,
                        strength = volumeTrend.strength,
                        recentChange = volumeTrend.recentChange,
                        description = volumeTrend.description
                    ),
                    volatility = volatility,
                    avgVolume = avgVolume,
                    priceRange = priceRange,
                    anomalies = anomalies,
                    summary = summary
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to analyze stock time series", e)
            Result.failure(e)
        }
    }

    /**
     * 종목 시계열 AI 해석
     */
    suspend fun interpretStockWithAI(
        analysisResult: StockTimeSeriesAnalysisResult
    ): Result<AIStockTimeSeriesInterpretation> = withContext(Dispatchers.IO) {
        try {
            logger.d("Interpreting stock time series with AI for ${analysisResult.stockData.name}")

            val client = aiApiClientFactory.getClient()
            val prompt = createStockTimeSeriesPrompt(analysisResult)

            val signalResult = client.analyzeMarket(prompt, temperature = 0.5)

            if (signalResult.isFailure) {
                return@withContext Result.failure(
                    signalResult.exceptionOrNull() ?: Exception("AI 분석 실패")
                )
            }

            val signal = signalResult.getOrThrow()

            val interpretation = AIStockTimeSeriesInterpretation(
                ticker = analysisResult.stockData.ticker,
                name = analysisResult.stockData.name,
                period = "${analysisResult.stockData.startDate} ~ ${analysisResult.stockData.endDate}",
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
            logger.e("Failed to interpret stock with AI", e)
            Result.failure(e)
        }
    }

    /**
     * 전체 종목 시계열 분석 실행 (데이터 수집 + 분석 + AI 해석)
     */
    suspend fun runFullStockTimeSeriesAnalysis(
        ticker: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<FullStockTimeSeriesResult> = withContext(Dispatchers.IO) {
        try {
            // 1. 데이터 수집
            val dataResult = collectStockTimeSeriesData(ticker, periodDays)
            if (dataResult.isFailure) {
                return@withContext Result.failure(
                    dataResult.exceptionOrNull() ?: Exception("데이터 수집 실패")
                )
            }
            val stockData = dataResult.getOrThrow()

            // 2. 분석 수행
            val analysisResult = analyzeStockTimeSeries(stockData)
            if (analysisResult.isFailure) {
                return@withContext Result.failure(
                    analysisResult.exceptionOrNull() ?: Exception("분석 실패")
                )
            }
            val analysis = analysisResult.getOrThrow()

            // 3. AI 해석
            val aiResult = interpretStockWithAI(analysis)

            Result.success(
                FullStockTimeSeriesResult(
                    analysisResult = analysis,
                    aiInterpretation = aiResult.getOrNull(),
                    errorMessage = aiResult.exceptionOrNull()?.message
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to run full stock time series analysis", e)
            Result.failure(e)
        }
    }

    /**
     * 종목 이상치 탐지
     */
    private fun detectStockAnomalies(data: StockTimeSeriesData): List<AnomalyPoint> {
        val anomalies = mutableListOf<AnomalyPoint>()
        val changeRates = data.getChangeRates()

        if (changeRates.size < 10) return anomalies

        val mean = changeRates.average()
        val stdDev = calculateStdDev(changeRates)

        data.dataPoints.forEachIndexed { index, point ->
            val zScore = if (stdDev != 0.0) abs(point.changeRate - mean) / stdDev else 0.0

            if (zScore > 2.0) {
                val severity = when {
                    zScore > 3.0 -> AnomalySeverity.HIGH
                    zScore > 2.5 -> AnomalySeverity.MEDIUM
                    else -> AnomalySeverity.LOW
                }

                anomalies.add(
                    AnomalyPoint(
                        date = point.date,
                        indicator = "등락률",
                        value = point.changeRate,
                        expectedRange = Pair(mean - 2 * stdDev, mean + 2 * stdDev),
                        severity = severity
                    )
                )
            }
        }

        return anomalies.sortedByDescending { it.severity.ordinal }
    }

    /**
     * 종목 분석 요약 생성
     */
    private fun generateStockSummary(
        data: StockTimeSeriesData,
        priceTrend: TrendResult,
        volumeTrend: TrendResult,
        volatility: Double
    ): String {
        val sb = StringBuilder()

        sb.appendLine("## ${data.name} (${data.ticker}) 시계열 분석 요약")
        sb.appendLine("분석 기간: ${data.startDate} ~ ${data.endDate} (${data.totalDays}일)")
        sb.appendLine()

        sb.appendLine("### 가격 추세")
        sb.appendLine("- 추세: ${priceTrend.description}")
        sb.appendLine("- 최근 변화율: ${String.format("%+.1f", priceTrend.recentChange)}%")
        sb.appendLine()

        sb.appendLine("### 거래량 추세")
        sb.appendLine("- 추세: ${volumeTrend.description}")
        sb.appendLine("- 최근 변화율: ${String.format("%+.1f", volumeTrend.recentChange)}%")
        sb.appendLine()

        sb.appendLine("### 변동성")
        val volatilityLevel = when {
            volatility > 3.0 -> "높음"
            volatility > 1.5 -> "보통"
            else -> "낮음"
        }
        sb.appendLine("- 일별 변동성: ${String.format("%.2f", volatility)}% ($volatilityLevel)")

        return sb.toString()
    }

    /**
     * 종목 시계열 AI 프롬프트 생성
     */
    private fun createStockTimeSeriesPrompt(result: StockTimeSeriesAnalysisResult): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 전문 애널리스트입니다.")
            appendLine("다음 종목의 시계열 데이터를 분석하여 투자 전망을 제공해주세요.")
            appendLine()
            appendLine("## 종목 정보")
            appendLine("- 종목명: ${result.stockData.name}")
            appendLine("- 종목코드: ${result.stockData.ticker}")
            appendLine("- 분석 기간: ${result.stockData.startDate} ~ ${result.stockData.endDate}")
            appendLine("- 총 데이터: ${result.stockData.totalDays}일")
            appendLine()

            appendLine("## 가격 분석")
            appendLine("- 가격 추세: ${result.priceTrend.description}")
            appendLine("- 추세 강도: ${String.format("%.2f", result.priceTrend.strength)}")
            appendLine("- 최근 변화율: ${String.format("%+.1f", result.priceTrend.recentChange)}%")
            appendLine("- 가격 범위: ${String.format("%.0f", result.priceRange.first)} ~ ${String.format("%.0f", result.priceRange.second)}원")
            appendLine()

            appendLine("## 거래량 분석")
            appendLine("- 거래량 추세: ${result.volumeTrend.description}")
            appendLine("- 평균 거래량: ${String.format("%,d", result.avgVolume)}주")
            appendLine()

            appendLine("## 변동성")
            appendLine("- 일별 변동성: ${String.format("%.2f", result.volatility)}%")
            appendLine()

            // 최근 데이터 포인트
            val recentPoints = result.stockData.dataPoints.takeLast(10)
            appendLine("## 최근 10일 데이터")
            recentPoints.forEach { point ->
                appendLine("- ${point.date}: ${String.format("%.0f", point.close)}원 (${String.format("%+.2f", point.changeRate)}%), 거래량: ${String.format("%,d", point.volume)}")
            }
            appendLine()

            // 이상치 정보
            val highAnomalies = result.anomalies.filter { it.severity == AnomalySeverity.HIGH }
            if (highAnomalies.isNotEmpty()) {
                appendLine("## 주요 이상치")
                highAnomalies.take(3).forEach { anomaly ->
                    appendLine("- ${anomaly.date}: 등락률 ${String.format("%+.2f", anomaly.value)}%")
                }
                appendLine()
            }

            appendLine("## 분석 요청")
            appendLine("위 시계열 데이터를 종합적으로 분석하여 다음 JSON 형식으로 투자 신호를 제공해주세요:")
            appendLine()
            appendLine("```json")
            appendLine("{")
            appendLine("  \"signal\": \"STRONG_BUY|BUY|NEUTRAL|SELL|STRONG_SELL\",")
            appendLine("  \"confidence\": 0.0-1.0,")
            appendLine("  \"upProbability\": 0-100,")
            appendLine("  \"downProbability\": 0-100,")
            appendLine("  \"reasoning\": \"시계열 분석 기반 상세 근거\",")
            appendLine("  \"keyFactors\": [\"주요 요인 1\", \"주요 요인 2\", \"주요 요인 3\"],")
            appendLine("  \"recommendation\": \"투자 권장사항\",")
            appendLine("  \"riskLevel\": \"LOW|MEDIUM|HIGH\"")
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine("**분석 시 고려사항:**")
            appendLine("1. 가격 추세와 거래량 추세의 일치/괴리 분석")
            appendLine("2. 변동성 수준에 따른 리스크 평가")
            appendLine("3. 최근 가격 움직임의 지속 가능성 판단")
            appendLine("4. 이상치 발생 패턴의 의미 해석")
        }
    }

    // ========== 종목-지표 상관관계 분석 ==========

    /**
     * 종목의 ETF 보유 시계열 데이터 수집
     */
    suspend fun collectStockEtfHoldingTimeSeries(
        ticker: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<StockEtfHoldingTimeSeries> = withContext(Dispatchers.IO) {
        try {
            logger.d("Collecting stock ETF holding time series for $ticker, $periodDays days")

            val stockName = etfDao.getStockName(ticker) ?: ticker
            val trendData = etfDao.getStockAggregatedTrend(ticker)

            if (trendData.isEmpty()) {
                return@withContext Result.failure(Exception("해당 종목의 ETF 보유 데이터가 없습니다: $ticker"))
            }

            // 최근 periodDays 일간 데이터만 필터링
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(periodDays.toLong())
            val startDateStr = startDate.format(dateFormatter)

            val filteredData = trendData.filter { it.date >= startDateStr }
                .sortedBy { it.date }

            val dataPoints = filteredData.map { point ->
                StockEtfHoldingPoint(
                    date = point.date,
                    totalAmount = point.totalAmount.toDouble(),
                    etfCount = point.etfCount,
                    maxWeight = point.maxWeight.toDouble(),
                    avgWeight = point.avgWeight.toDouble()
                )
            }

            Result.success(
                StockEtfHoldingTimeSeries(
                    ticker = ticker,
                    name = stockName,
                    dataPoints = dataPoints
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to collect stock ETF holding time series", e)
            Result.failure(e)
        }
    }

    /**
     * 종목-시장지표 상관관계 전체 분석
     */
    suspend fun analyzeStockIndicatorCorrelations(
        request: StockIndicatorCorrelationRequest
    ): Result<StockIndicatorCorrelationResult> = withContext(Dispatchers.IO) {
        try {
            logger.d("Analyzing stock-indicator correlations for ${request.ticker}")

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(request.periodDays.toLong())
            val startDateStr = startDate.format(dateFormatter)
            val endDateStr = endDate.format(dateFormatter)

            // 1. 종목 주가 데이터 수집
            val stockData = oscillatorPyClient.getStockOhlcv(request.ticker, request.periodDays, "d")
            if (stockData == null || stockData.dates.isEmpty()) {
                return@withContext Result.failure(Exception("종목 주가 데이터를 가져올 수 없습니다: ${request.ticker}"))
            }

            // 2. 종목의 ETF 보유 데이터 수집
            val etfHoldingResult = collectStockEtfHoldingTimeSeries(request.ticker, request.periodDays)
            val etfHoldingData = etfHoldingResult.getOrNull()

            // 3. 시장 지표 데이터 수집 (병렬)
            val (fearGreedData, oscillatorData, depositData, etfStatsData) = coroutineScope {
                val fearGreedDeferred = async {
                    fearGreedDao.getByMarketAndDateRange(request.market, startDateStr, endDateStr).first()
                }
                val oscillatorDeferred = async {
                    marketOscillatorDao.getDataByDateRange(request.market, startDateStr, endDateStr).first()
                }
                val depositDeferred = async {
                    marketDepositDao.getAllDeposits().first()
                        .filter { it.date in startDateStr..endDateStr }
                }
                val etfStatsDeferred = async {
                    dailyEtfStatisticsDao.getByDateRangeSuspend(startDateStr, endDateStr)
                }

                Tuple4(
                    fearGreedDeferred.await(),
                    oscillatorDeferred.await(),
                    depositDeferred.await(),
                    etfStatsDeferred.await()
                )
            }

            // 4. 날짜 기준 데이터 정렬
            val stockPrices = stockData.dates.zip(stockData.close).toMap()
            val stockChanges = stockData.dates.mapIndexed { index, date ->
                val change = if (index == 0) 0.0 else {
                    val prev = stockData.close[index - 1]
                    if (prev != 0.0) ((stockData.close[index] - prev) / prev) * 100 else 0.0
                }
                date to change
            }.toMap()

            val etfAmounts = etfHoldingData?.dataPoints?.associate { it.date to it.totalAmount } ?: emptyMap()

            // 5. Fear & Greed 상관관계 계산
            val fearGreedCorrelations = calculateFearGreedCorrelations(
                fearGreedData, stockPrices, stockChanges, etfAmounts
            )

            // 6. Oscillator 상관관계 계산
            val oscillatorCorrelations = calculateOscillatorCorrelations(
                oscillatorData, stockPrices, stockChanges, etfAmounts
            )

            // 7. 예탁금/신용 상관관계 계산
            val depositCorrelations = calculateDepositCorrelations(
                depositData, stockPrices, stockChanges, etfAmounts
            )

            // 8. ETF 통계 상관관계 계산
            val etfCorrelations = calculateEtfStatsCorrelations(
                etfStatsData, stockPrices, stockChanges, etfAmounts
            )

            // 9. Top 상관관계 추출
            val allCorrelations = fearGreedCorrelations + oscillatorCorrelations +
                    depositCorrelations + etfCorrelations

            val topPositive = allCorrelations
                .filter { it.correlation > 0 && it.dataPoints >= 10 }
                .sortedByDescending { it.correlation }
                .take(5)

            val topNegative = allCorrelations
                .filter { it.correlation < 0 && it.dataPoints >= 10 }
                .sortedBy { it.correlation }
                .take(5)

            // 10. 요약 생성
            val summary = generateCorrelationSummary(
                request.name, fearGreedCorrelations, oscillatorCorrelations,
                depositCorrelations, etfCorrelations
            )

            Result.success(
                StockIndicatorCorrelationResult(
                    ticker = request.ticker,
                    stockName = request.name,
                    market = request.market,
                    startDate = stockData.dates.firstOrNull() ?: startDateStr,
                    endDate = stockData.dates.lastOrNull() ?: endDateStr,
                    totalDataPoints = stockData.dates.size,
                    fearGreedCorrelations = fearGreedCorrelations,
                    oscillatorCorrelations = oscillatorCorrelations,
                    depositCorrelations = depositCorrelations,
                    etfCorrelations = etfCorrelations,
                    topPositiveCorrelations = topPositive,
                    topNegativeCorrelations = topNegative,
                    summary = summary
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to analyze stock-indicator correlations", e)
            Result.failure(e)
        }
    }

    /**
     * AI를 활용한 종목-지표 상관관계 해석
     */
    suspend fun interpretStockIndicatorCorrelationsWithAI(
        correlationResult: StockIndicatorCorrelationResult
    ): Result<AIStockIndicatorInterpretation> = withContext(Dispatchers.IO) {
        try {
            logger.d("Interpreting stock-indicator correlations with AI for ${correlationResult.stockName}")

            val client = aiApiClientFactory.getClient()
            val prompt = createStockIndicatorCorrelationPrompt(correlationResult)

            val signalResult = client.analyzeMarket(prompt, temperature = 0.5)

            if (signalResult.isFailure) {
                return@withContext Result.failure(
                    signalResult.exceptionOrNull() ?: Exception("AI 분석 실패")
                )
            }

            val signal = signalResult.getOrThrow()

            val keyCorrelations = extractKeyCorrelations(correlationResult)
            val marketSentimentImpact = extractSentimentImpact(signal.reasoning)
            val fundFlowImpact = extractFundFlowImpact(signal.reasoning)
            val etfFlowImpact = extractEtfFlowImpact(signal.reasoning)
            val period = "${correlationResult.startDate} ~ ${correlationResult.endDate}"

            val interpretation = AIStockIndicatorInterpretation(
                ticker = correlationResult.ticker,
                name = correlationResult.stockName,
                period = period,
                signal = signal.signal.name,
                confidence = signal.confidence,
                upProbability = signal.upProbability,
                downProbability = signal.downProbability,
                riskLevel = signal.riskLevel.name,
                keyCorrelations = keyCorrelations,
                marketSentimentImpact = marketSentimentImpact,
                fundFlowImpact = fundFlowImpact,
                etfFlowImpact = etfFlowImpact,
                recommendation = signal.recommendation,
                reasoning = signal.reasoning
            )

            // 분석 결과를 데이터베이스에 저장
            try {
                val aiProvider = aiApiClientFactory.getSelectedProviderName()
                val aiModel = aiApiClientFactory.getSelectedModelId()
                val periodDays = correlationResult.totalDataPoints

                val dbResult = StockIndicatorAIResult(
                    id = UUID.randomUUID().toString(),
                    ticker = correlationResult.ticker,
                    stockName = correlationResult.stockName,
                    market = correlationResult.market,
                    analysisDate = correlationResult.endDate,
                    period = period,
                    periodDays = periodDays,
                    aiProvider = aiProvider,
                    aiModel = aiModel,
                    signal = signal.signal.name,
                    confidence = signal.confidence,
                    upProbability = signal.upProbability,
                    downProbability = signal.downProbability,
                    riskLevel = signal.riskLevel.name,
                    keyCorrelations = json.encodeToString(keyCorrelations),
                    marketSentimentImpact = marketSentimentImpact,
                    fundFlowImpact = fundFlowImpact,
                    etfFlowImpact = etfFlowImpact,
                    reasoning = signal.reasoning,
                    recommendation = signal.recommendation
                )

                stockIndicatorAIResultDao.insert(dbResult)
                logger.d("Saved stock-indicator AI analysis result: ${dbResult.id}")
            } catch (e: Exception) {
                logger.e("Failed to save stock-indicator AI analysis result", e)
                // 저장 실패해도 분석 결과는 반환
            }

            Result.success(interpretation)
        } catch (e: Exception) {
            logger.e("Failed to interpret stock-indicator correlations with AI", e)
            Result.failure(e)
        }
    }

    /**
     * 전체 종목-지표 상관관계 분석 실행 (데이터 수집 + 분석 + AI 해석)
     */
    suspend fun runFullStockIndicatorCorrelationAnalysis(
        ticker: String,
        name: String,
        market: String = "KOSPI",
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<FullStockIndicatorCorrelationResult> = withContext(Dispatchers.IO) {
        try {
            val request = StockIndicatorCorrelationRequest(
                ticker = ticker,
                name = name,
                market = market,
                periodDays = periodDays
            )

            // 1. 상관관계 분석 수행
            val correlationResult = analyzeStockIndicatorCorrelations(request)
            if (correlationResult.isFailure) {
                return@withContext Result.failure(
                    correlationResult.exceptionOrNull() ?: Exception("상관관계 분석 실패")
                )
            }
            val correlation = correlationResult.getOrThrow()

            // 2. AI 해석
            val aiResult = interpretStockIndicatorCorrelationsWithAI(correlation)

            Result.success(
                FullStockIndicatorCorrelationResult(
                    correlationResult = correlation,
                    aiInterpretation = aiResult.getOrNull(),
                    errorMessage = aiResult.exceptionOrNull()?.message
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to run full stock-indicator correlation analysis", e)
            Result.failure(e)
        }
    }

    // ========== AI 분석 히스토리 조회 ==========

    /**
     * 특정 종목의 AI 분석 히스토리 조회 (Flow)
     */
    fun getStockIndicatorAIHistory(ticker: String, limit: Int = 10) =
        stockIndicatorAIResultDao.getRecentByTicker(ticker, limit)

    /**
     * 모든 AI 분석 히스토리 조회 (Flow)
     */
    fun getAllStockIndicatorAIHistory(limit: Int = 20) =
        stockIndicatorAIResultDao.getRecent(limit)

    /**
     * 특정 종목의 최신 AI 분석 결과 조회
     */
    suspend fun getLatestStockIndicatorAIResult(ticker: String): StockIndicatorAIResult? =
        withContext(Dispatchers.IO) {
            stockIndicatorAIResultDao.getLatestByTicker(ticker)
        }

    /**
     * AI 분석 히스토리 삭제
     */
    suspend fun deleteStockIndicatorAIHistory(id: String) = withContext(Dispatchers.IO) {
        stockIndicatorAIResultDao.deleteById(id)
    }

    // ========== Fear & Greed 상관관계 계산 ==========

    private fun calculateFearGreedCorrelations(
        fearGreedData: List<FearGreedIndex>,
        stockPrices: Map<String, Double>,
        stockChanges: Map<String, Double>,
        etfAmounts: Map<String, Double>
    ): List<IndicatorStockCorrelation> {
        val correlations = mutableListOf<IndicatorStockCorrelation>()
        val fearGreedMap = fearGreedData.associateBy { it.date }

        // Fear & Greed vs 종가
        val commonDates = stockPrices.keys.filter { fearGreedMap.containsKey(it) }.sorted()
        if (commonDates.size >= 10) {
            val fgValues = commonDates.map { fearGreedMap[it]!!.fearGreedValue }
            val priceValues = commonDates.map { stockPrices[it]!! }

            // 선행/후행 상관관계 계산
            val leadLag = calculateLeadLagCorrelation(fgValues, priceValues)
            val corr = if (abs(leadLag.optimalCorrelation) > abs(leadLag.simultaneousCorrelation))
                leadLag.optimalCorrelation else leadLag.simultaneousCorrelation
            val optimalLag = if (abs(leadLag.optimalCorrelation) > abs(leadLag.simultaneousCorrelation))
                leadLag.optimalLag else 0

            if (!corr.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.FEAR_GREED.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corr,
                        significance = calculateSignificance(corr, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = optimalLag,
                        description = describeCorrelation(corr, "Fear & Greed", "주가", optimalLag)
                    )
                )
            }

            // Fear & Greed vs 등락률
            val changeValues = commonDates.map { stockChanges[it] ?: 0.0 }
            val leadLagChange = calculateLeadLagCorrelation(fgValues, changeValues)
            val corrChange = if (abs(leadLagChange.optimalCorrelation) > abs(leadLagChange.simultaneousCorrelation))
                leadLagChange.optimalCorrelation else leadLagChange.simultaneousCorrelation
            val lagChange = if (abs(leadLagChange.optimalCorrelation) > abs(leadLagChange.simultaneousCorrelation))
                leadLagChange.optimalLag else 0

            if (!corrChange.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.FEAR_GREED.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrChange,
                        significance = calculateSignificance(corrChange, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagChange,
                        description = describeCorrelation(corrChange, "Fear & Greed", "등락률", lagChange)
                    )
                )
            }

            // Fear & Greed vs ETF 보유금액
            if (etfAmounts.isNotEmpty()) {
                val etfCommonDates = commonDates.filter { etfAmounts.containsKey(it) }
                if (etfCommonDates.size >= 10) {
                    val fgValuesEtf = etfCommonDates.map { fearGreedMap[it]!!.fearGreedValue }
                    val etfValues = etfCommonDates.map { etfAmounts[it]!! }
                    val leadLagEtf = calculateLeadLagCorrelation(fgValuesEtf, etfValues)
                    val corrEtf = if (abs(leadLagEtf.optimalCorrelation) > abs(leadLagEtf.simultaneousCorrelation))
                        leadLagEtf.optimalCorrelation else leadLagEtf.simultaneousCorrelation
                    val lagEtf = if (abs(leadLagEtf.optimalCorrelation) > abs(leadLagEtf.simultaneousCorrelation))
                        leadLagEtf.optimalLag else 0

                    if (!corrEtf.isNaN()) {
                        correlations.add(
                            IndicatorStockCorrelation(
                                indicatorType = MarketIndicatorType.FEAR_GREED.name,
                                stockMetricType = StockMetricType.MARKET_CAP.name,
                                correlation = corrEtf,
                                significance = calculateSignificance(corrEtf, etfCommonDates.size),
                                dataPoints = etfCommonDates.size,
                                leadLagDays = lagEtf,
                                description = describeCorrelation(corrEtf, "Fear & Greed", "ETF 보유금액", lagEtf)
                            )
                        )
                    }
                }
            }

            // RSI vs 주가
            val rsiValues = commonDates.map { fearGreedMap[it]!!.rsi }
            val leadLagRsi = calculateLeadLagCorrelation(rsiValues, priceValues)
            val corrRsi = if (abs(leadLagRsi.optimalCorrelation) > abs(leadLagRsi.simultaneousCorrelation))
                leadLagRsi.optimalCorrelation else leadLagRsi.simultaneousCorrelation
            val lagRsi = if (abs(leadLagRsi.optimalCorrelation) > abs(leadLagRsi.simultaneousCorrelation))
                leadLagRsi.optimalLag else 0

            if (!corrRsi.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.FEAR_GREED_RSI.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrRsi,
                        significance = calculateSignificance(corrRsi, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagRsi,
                        description = describeCorrelation(corrRsi, "RSI", "주가", lagRsi)
                    )
                )
            }

            // 모멘텀 vs 주가
            val momentumValues = commonDates.map { fearGreedMap[it]!!.momentum }
            val leadLagMomentum = calculateLeadLagCorrelation(momentumValues, priceValues)
            val corrMomentum = if (abs(leadLagMomentum.optimalCorrelation) > abs(leadLagMomentum.simultaneousCorrelation))
                leadLagMomentum.optimalCorrelation else leadLagMomentum.simultaneousCorrelation
            val lagMomentum = if (abs(leadLagMomentum.optimalCorrelation) > abs(leadLagMomentum.simultaneousCorrelation))
                leadLagMomentum.optimalLag else 0

            if (!corrMomentum.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.FEAR_GREED_MOMENTUM.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrMomentum,
                        significance = calculateSignificance(corrMomentum, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagMomentum,
                        description = describeCorrelation(corrMomentum, "모멘텀", "주가", lagMomentum)
                    )
                )
            }
        }

        return correlations
    }

    // ========== Oscillator 상관관계 계산 ==========

    private fun calculateOscillatorCorrelations(
        oscillatorData: List<MarketOscillatorData>,
        stockPrices: Map<String, Double>,
        stockChanges: Map<String, Double>,
        etfAmounts: Map<String, Double>
    ): List<IndicatorStockCorrelation> {
        val correlations = mutableListOf<IndicatorStockCorrelation>()
        val oscillatorMap = oscillatorData.associateBy { it.date }

        val commonDates = stockPrices.keys.filter { oscillatorMap.containsKey(it) }.sorted()
        if (commonDates.size >= 10) {
            val oscValues = commonDates.map { oscillatorMap[it]!!.oscillator }
            val priceValues = commonDates.map { stockPrices[it]!! }

            // Oscillator vs 종가 (선행/후행 분석)
            val leadLag = calculateLeadLagCorrelation(oscValues, priceValues)
            val corr = if (abs(leadLag.optimalCorrelation) > abs(leadLag.simultaneousCorrelation))
                leadLag.optimalCorrelation else leadLag.simultaneousCorrelation
            val optimalLag = if (abs(leadLag.optimalCorrelation) > abs(leadLag.simultaneousCorrelation))
                leadLag.optimalLag else 0

            if (!corr.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.OSCILLATOR.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corr,
                        significance = calculateSignificance(corr, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = optimalLag,
                        description = describeCorrelation(corr, "시장 과매수/과매도", "주가", optimalLag)
                    )
                )
            }

            // Oscillator vs 등락률
            val changeValues = commonDates.map { stockChanges[it] ?: 0.0 }
            val leadLagChange = calculateLeadLagCorrelation(oscValues, changeValues)
            val corrChange = if (abs(leadLagChange.optimalCorrelation) > abs(leadLagChange.simultaneousCorrelation))
                leadLagChange.optimalCorrelation else leadLagChange.simultaneousCorrelation
            val lagChange = if (abs(leadLagChange.optimalCorrelation) > abs(leadLagChange.simultaneousCorrelation))
                leadLagChange.optimalLag else 0

            if (!corrChange.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.OSCILLATOR.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrChange,
                        significance = calculateSignificance(corrChange, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagChange,
                        description = describeCorrelation(corrChange, "시장 과매수/과매도", "등락률", lagChange)
                    )
                )
            }

            // Oscillator vs ETF 보유금액
            if (etfAmounts.isNotEmpty()) {
                val etfCommonDates = commonDates.filter { etfAmounts.containsKey(it) }
                if (etfCommonDates.size >= 10) {
                    val oscValuesEtf = etfCommonDates.map { oscillatorMap[it]!!.oscillator }
                    val etfValues = etfCommonDates.map { etfAmounts[it]!! }
                    val leadLagEtf = calculateLeadLagCorrelation(oscValuesEtf, etfValues)
                    val corrEtf = if (abs(leadLagEtf.optimalCorrelation) > abs(leadLagEtf.simultaneousCorrelation))
                        leadLagEtf.optimalCorrelation else leadLagEtf.simultaneousCorrelation
                    val lagEtf = if (abs(leadLagEtf.optimalCorrelation) > abs(leadLagEtf.simultaneousCorrelation))
                        leadLagEtf.optimalLag else 0

                    if (!corrEtf.isNaN()) {
                        correlations.add(
                            IndicatorStockCorrelation(
                                indicatorType = MarketIndicatorType.OSCILLATOR.name,
                                stockMetricType = StockMetricType.MARKET_CAP.name,
                                correlation = corrEtf,
                                significance = calculateSignificance(corrEtf, etfCommonDates.size),
                                dataPoints = etfCommonDates.size,
                                leadLagDays = lagEtf,
                                description = describeCorrelation(corrEtf, "시장 과매수/과매도", "ETF 보유금액", lagEtf)
                            )
                        )
                    }
                }
            }
        }

        return correlations
    }

    // ========== 예탁금/신용 상관관계 계산 ==========

    private fun calculateDepositCorrelations(
        depositData: List<MarketDeposit>,
        stockPrices: Map<String, Double>,
        stockChanges: Map<String, Double>,
        etfAmounts: Map<String, Double>
    ): List<IndicatorStockCorrelation> {
        val correlations = mutableListOf<IndicatorStockCorrelation>()
        val depositMap = depositData.associateBy { it.date }

        val commonDates = stockPrices.keys.filter { depositMap.containsKey(it) }.sorted()
        if (commonDates.size >= 10) {
            val priceValues = commonDates.map { stockPrices[it]!! }
            val changeValues = commonDates.map { stockChanges[it] ?: 0.0 }

            // 예탁금 vs 종가
            val depositAmounts = commonDates.map { depositMap[it]!!.depositAmount }
            val leadLagDeposit = calculateLeadLagCorrelation(depositAmounts, priceValues)
            val corrDepositPrice = if (abs(leadLagDeposit.optimalCorrelation) > abs(leadLagDeposit.simultaneousCorrelation))
                leadLagDeposit.optimalCorrelation else leadLagDeposit.simultaneousCorrelation
            val lagDeposit = if (abs(leadLagDeposit.optimalCorrelation) > abs(leadLagDeposit.simultaneousCorrelation))
                leadLagDeposit.optimalLag else 0

            if (!corrDepositPrice.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.DEPOSIT_AMOUNT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrDepositPrice,
                        significance = calculateSignificance(corrDepositPrice, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagDeposit,
                        description = describeCorrelation(corrDepositPrice, "고객예탁금", "주가", lagDeposit)
                    )
                )
            }

            // 예탁금 변화 vs 등락률
            val depositChanges = commonDates.map { depositMap[it]!!.depositChange }
            val leadLagDepositChange = calculateLeadLagCorrelation(depositChanges, changeValues)
            val corrDepositChange = if (abs(leadLagDepositChange.optimalCorrelation) > abs(leadLagDepositChange.simultaneousCorrelation))
                leadLagDepositChange.optimalCorrelation else leadLagDepositChange.simultaneousCorrelation
            val lagDepositChange = if (abs(leadLagDepositChange.optimalCorrelation) > abs(leadLagDepositChange.simultaneousCorrelation))
                leadLagDepositChange.optimalLag else 0

            if (!corrDepositChange.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.DEPOSIT_CHANGE.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrDepositChange,
                        significance = calculateSignificance(corrDepositChange, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagDepositChange,
                        description = describeCorrelation(corrDepositChange, "예탁금 변화", "등락률", lagDepositChange)
                    )
                )
            }

            // 신용잔고 vs 종가
            val creditAmounts = commonDates.map { depositMap[it]!!.creditAmount }
            val leadLagCredit = calculateLeadLagCorrelation(creditAmounts, priceValues)
            val corrCreditPrice = if (abs(leadLagCredit.optimalCorrelation) > abs(leadLagCredit.simultaneousCorrelation))
                leadLagCredit.optimalCorrelation else leadLagCredit.simultaneousCorrelation
            val lagCredit = if (abs(leadLagCredit.optimalCorrelation) > abs(leadLagCredit.simultaneousCorrelation))
                leadLagCredit.optimalLag else 0

            if (!corrCreditPrice.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.CREDIT_AMOUNT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrCreditPrice,
                        significance = calculateSignificance(corrCreditPrice, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagCredit,
                        description = describeCorrelation(corrCreditPrice, "신용잔고", "주가", lagCredit)
                    )
                )
            }

            // 신용 변화 vs 등락률
            val creditChanges = commonDates.map { depositMap[it]!!.creditChange }
            val leadLagCreditChange = calculateLeadLagCorrelation(creditChanges, changeValues)
            val corrCreditChange = if (abs(leadLagCreditChange.optimalCorrelation) > abs(leadLagCreditChange.simultaneousCorrelation))
                leadLagCreditChange.optimalCorrelation else leadLagCreditChange.simultaneousCorrelation
            val lagCreditChange = if (abs(leadLagCreditChange.optimalCorrelation) > abs(leadLagCreditChange.simultaneousCorrelation))
                leadLagCreditChange.optimalLag else 0

            if (!corrCreditChange.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.CREDIT_CHANGE.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrCreditChange,
                        significance = calculateSignificance(corrCreditChange, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagCreditChange,
                        description = describeCorrelation(corrCreditChange, "신용 변화", "등락률", lagCreditChange)
                    )
                )
            }

            // ETF 보유금액과의 상관관계
            if (etfAmounts.isNotEmpty()) {
                val etfCommonDates = commonDates.filter { etfAmounts.containsKey(it) }
                if (etfCommonDates.size >= 10) {
                    val depositAmountsEtf = etfCommonDates.map { depositMap[it]!!.depositAmount }
                    val etfValues = etfCommonDates.map { etfAmounts[it]!! }
                    val leadLagDepositEtf = calculateLeadLagCorrelation(depositAmountsEtf, etfValues)
                    val corrDepositEtf = if (abs(leadLagDepositEtf.optimalCorrelation) > abs(leadLagDepositEtf.simultaneousCorrelation))
                        leadLagDepositEtf.optimalCorrelation else leadLagDepositEtf.simultaneousCorrelation
                    val lagDepositEtf = if (abs(leadLagDepositEtf.optimalCorrelation) > abs(leadLagDepositEtf.simultaneousCorrelation))
                        leadLagDepositEtf.optimalLag else 0

                    if (!corrDepositEtf.isNaN()) {
                        correlations.add(
                            IndicatorStockCorrelation(
                                indicatorType = MarketIndicatorType.DEPOSIT_AMOUNT.name,
                                stockMetricType = StockMetricType.MARKET_CAP.name,
                                correlation = corrDepositEtf,
                                significance = calculateSignificance(corrDepositEtf, etfCommonDates.size),
                                dataPoints = etfCommonDates.size,
                                leadLagDays = lagDepositEtf,
                                description = describeCorrelation(corrDepositEtf, "고객예탁금", "ETF 보유금액", lagDepositEtf)
                            )
                        )
                    }
                }
            }
        }

        return correlations
    }

    // ========== ETF 통계 상관관계 계산 ==========

    private fun calculateEtfStatsCorrelations(
        etfStatsData: List<DailyEtfStatistics>,
        stockPrices: Map<String, Double>,
        stockChanges: Map<String, Double>,
        etfAmounts: Map<String, Double>
    ): List<IndicatorStockCorrelation> {
        val correlations = mutableListOf<IndicatorStockCorrelation>()
        val statsMap = etfStatsData.associateBy { it.date }

        val commonDates = stockPrices.keys.filter { statsMap.containsKey(it) }.sorted()
        if (commonDates.size >= 10) {
            val priceValues = commonDates.map { stockPrices[it]!! }
            val changeValues = commonDates.map { stockChanges[it] ?: 0.0 }

            // 신규편입 수 vs 종가
            val newStockCounts = commonDates.map { statsMap[it]!!.newStockCount.toDouble() }
            val leadLagNewCount = calculateLeadLagCorrelation(newStockCounts, priceValues)
            val corrNewCount = if (abs(leadLagNewCount.optimalCorrelation) > abs(leadLagNewCount.simultaneousCorrelation))
                leadLagNewCount.optimalCorrelation else leadLagNewCount.simultaneousCorrelation
            val lagNewCount = if (abs(leadLagNewCount.optimalCorrelation) > abs(leadLagNewCount.simultaneousCorrelation))
                leadLagNewCount.optimalLag else 0

            if (!corrNewCount.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_NEW_STOCK_COUNT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrNewCount,
                        significance = calculateSignificance(corrNewCount, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagNewCount,
                        description = describeCorrelation(corrNewCount, "ETF 신규편입 수", "주가", lagNewCount)
                    )
                )
            }

            // 신규편입 금액 vs 종가
            val newStockAmounts = commonDates.map { statsMap[it]!!.newStockAmount.toDouble() }
            val leadLagNewAmount = calculateLeadLagCorrelation(newStockAmounts, priceValues)
            val corrNewAmount = if (abs(leadLagNewAmount.optimalCorrelation) > abs(leadLagNewAmount.simultaneousCorrelation))
                leadLagNewAmount.optimalCorrelation else leadLagNewAmount.simultaneousCorrelation
            val lagNewAmount = if (abs(leadLagNewAmount.optimalCorrelation) > abs(leadLagNewAmount.simultaneousCorrelation))
                leadLagNewAmount.optimalLag else 0

            if (!corrNewAmount.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_NEW_STOCK_AMOUNT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrNewAmount,
                        significance = calculateSignificance(corrNewAmount, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagNewAmount,
                        description = describeCorrelation(corrNewAmount, "ETF 신규편입 금액", "주가", lagNewAmount)
                    )
                )
            }

            // 편출 수 vs 등락률
            val removedCounts = commonDates.map { statsMap[it]!!.removedStockCount.toDouble() }
            val leadLagRemoved = calculateLeadLagCorrelation(removedCounts, changeValues)
            val corrRemoved = if (abs(leadLagRemoved.optimalCorrelation) > abs(leadLagRemoved.simultaneousCorrelation))
                leadLagRemoved.optimalCorrelation else leadLagRemoved.simultaneousCorrelation
            val lagRemoved = if (abs(leadLagRemoved.optimalCorrelation) > abs(leadLagRemoved.simultaneousCorrelation))
                leadLagRemoved.optimalLag else 0

            if (!corrRemoved.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_REMOVED_STOCK_COUNT.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrRemoved,
                        significance = calculateSignificance(corrRemoved, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagRemoved,
                        description = describeCorrelation(corrRemoved, "ETF 편출 수", "등락률", lagRemoved)
                    )
                )
            }

            // 비중증가 수 vs 종가
            val increasedCounts = commonDates.map { statsMap[it]!!.increasedStockCount.toDouble() }
            val leadLagIncreased = calculateLeadLagCorrelation(increasedCounts, priceValues)
            val corrIncreased = if (abs(leadLagIncreased.optimalCorrelation) > abs(leadLagIncreased.simultaneousCorrelation))
                leadLagIncreased.optimalCorrelation else leadLagIncreased.simultaneousCorrelation
            val lagIncreased = if (abs(leadLagIncreased.optimalCorrelation) > abs(leadLagIncreased.simultaneousCorrelation))
                leadLagIncreased.optimalLag else 0

            if (!corrIncreased.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_INCREASED_COUNT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrIncreased,
                        significance = calculateSignificance(corrIncreased, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagIncreased,
                        description = describeCorrelation(corrIncreased, "ETF 비중증가 수", "주가", lagIncreased)
                    )
                )
            }

            // 비중감소 수 vs 등락률
            val decreasedCounts = commonDates.map { statsMap[it]!!.decreasedStockCount.toDouble() }
            val leadLagDecreased = calculateLeadLagCorrelation(decreasedCounts, changeValues)
            val corrDecreased = if (abs(leadLagDecreased.optimalCorrelation) > abs(leadLagDecreased.simultaneousCorrelation))
                leadLagDecreased.optimalCorrelation else leadLagDecreased.simultaneousCorrelation
            val lagDecreased = if (abs(leadLagDecreased.optimalCorrelation) > abs(leadLagDecreased.simultaneousCorrelation))
                leadLagDecreased.optimalLag else 0

            if (!corrDecreased.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_DECREASED_COUNT.name,
                        stockMetricType = StockMetricType.CHANGE_RATE.name,
                        correlation = corrDecreased,
                        significance = calculateSignificance(corrDecreased, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagDecreased,
                        description = describeCorrelation(corrDecreased, "ETF 비중감소 수", "등락률", lagDecreased)
                    )
                )
            }

            // 순편입 (신규 - 편출) vs 종가
            val netFlows = commonDates.map {
                (statsMap[it]!!.newStockCount - statsMap[it]!!.removedStockCount).toDouble()
            }
            val leadLagNetFlow = calculateLeadLagCorrelation(netFlows, priceValues)
            val corrNetFlow = if (abs(leadLagNetFlow.optimalCorrelation) > abs(leadLagNetFlow.simultaneousCorrelation))
                leadLagNetFlow.optimalCorrelation else leadLagNetFlow.simultaneousCorrelation
            val lagNetFlow = if (abs(leadLagNetFlow.optimalCorrelation) > abs(leadLagNetFlow.simultaneousCorrelation))
                leadLagNetFlow.optimalLag else 0

            if (!corrNetFlow.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_NET_FLOW.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrNetFlow,
                        significance = calculateSignificance(corrNetFlow, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagNetFlow,
                        description = describeCorrelation(corrNetFlow, "ETF 순편입", "주가", lagNetFlow)
                    )
                )
            }

            // ETF 원화예금 vs 종가
            val cashDeposits = commonDates.map { statsMap[it]!!.cashDepositAmount.toDouble() }
            val leadLagCash = calculateLeadLagCorrelation(cashDeposits, priceValues)
            val corrCash = if (abs(leadLagCash.optimalCorrelation) > abs(leadLagCash.simultaneousCorrelation))
                leadLagCash.optimalCorrelation else leadLagCash.simultaneousCorrelation
            val lagCash = if (abs(leadLagCash.optimalCorrelation) > abs(leadLagCash.simultaneousCorrelation))
                leadLagCash.optimalLag else 0

            if (!corrCash.isNaN()) {
                correlations.add(
                    IndicatorStockCorrelation(
                        indicatorType = MarketIndicatorType.ETF_CASH_DEPOSIT.name,
                        stockMetricType = StockMetricType.CLOSE_PRICE.name,
                        correlation = corrCash,
                        significance = calculateSignificance(corrCash, commonDates.size),
                        dataPoints = commonDates.size,
                        leadLagDays = lagCash,
                        description = describeCorrelation(corrCash, "ETF 원화예금", "주가", lagCash)
                    )
                )
            }

            // ETF 보유금액과의 상관관계
            if (etfAmounts.isNotEmpty()) {
                val etfCommonDates = commonDates.filter { etfAmounts.containsKey(it) }
                if (etfCommonDates.size >= 10) {
                    // 순편입 vs ETF 보유금액
                    val netFlowsEtf = etfCommonDates.map {
                        (statsMap[it]!!.newStockCount - statsMap[it]!!.removedStockCount).toDouble()
                    }
                    val etfValues = etfCommonDates.map { etfAmounts[it]!! }
                    val leadLagNetFlowEtf = calculateLeadLagCorrelation(netFlowsEtf, etfValues)
                    val corrNetFlowEtf = if (abs(leadLagNetFlowEtf.optimalCorrelation) > abs(leadLagNetFlowEtf.simultaneousCorrelation))
                        leadLagNetFlowEtf.optimalCorrelation else leadLagNetFlowEtf.simultaneousCorrelation
                    val lagNetFlowEtf = if (abs(leadLagNetFlowEtf.optimalCorrelation) > abs(leadLagNetFlowEtf.simultaneousCorrelation))
                        leadLagNetFlowEtf.optimalLag else 0

                    if (!corrNetFlowEtf.isNaN()) {
                        correlations.add(
                            IndicatorStockCorrelation(
                                indicatorType = MarketIndicatorType.ETF_NET_FLOW.name,
                                stockMetricType = StockMetricType.MARKET_CAP.name,
                                correlation = corrNetFlowEtf,
                                significance = calculateSignificance(corrNetFlowEtf, etfCommonDates.size),
                                dataPoints = etfCommonDates.size,
                                leadLagDays = lagNetFlowEtf,
                                description = describeCorrelation(corrNetFlowEtf, "ETF 순편입", "ETF 보유금액", lagNetFlowEtf)
                            )
                        )
                    }
                }
            }
        }

        return correlations
    }

    // ========== 상관관계 설명 생성 ==========

    /**
     * 상관관계 설명 생성 (선행/후행 정보 포함)
     */
    private fun describeCorrelation(
        correlation: Double,
        indicator: String,
        metric: String,
        leadLagDays: Int = 0
    ): String {
        val strength = when {
            abs(correlation) >= 0.7 -> "강한"
            abs(correlation) >= 0.4 -> "중간"
            abs(correlation) >= 0.2 -> "약한"
            else -> "거의 없는"
        }
        val direction = if (correlation >= 0) "양의" else "음의"

        // 선행/후행 정보
        val leadLagInfo = when {
            leadLagDays > 0 -> " [지표 ${leadLagDays}일 선행]"
            leadLagDays < 0 -> " [지표 ${-leadLagDays}일 후행]"
            else -> ""
        }

        return when {
            abs(correlation) < 0.2 -> "$indicator 와 $metric 간에 유의미한 상관관계가 없습니다."
            correlation >= 0.4 -> "$indicator 이(가) 상승하면 $metric 도 함께 상승하는 $strength $direction 상관관계 (${String.format("%.2f", correlation)})$leadLagInfo"
            correlation <= -0.4 -> "$indicator 이(가) 상승하면 $metric 이(가) 하락하는 $strength $direction 상관관계 (${String.format("%.2f", correlation)})$leadLagInfo"
            correlation > 0 -> "$indicator 와 $metric 간 $strength $direction 상관관계 (${String.format("%.2f", correlation)})$leadLagInfo"
            else -> "$indicator 와 $metric 간 $strength $direction 상관관계 (${String.format("%.2f", correlation)})$leadLagInfo"
        }
    }

    private fun generateCorrelationSummary(
        stockName: String,
        fearGreedCorrelations: List<IndicatorStockCorrelation>,
        oscillatorCorrelations: List<IndicatorStockCorrelation>,
        depositCorrelations: List<IndicatorStockCorrelation>,
        etfCorrelations: List<IndicatorStockCorrelation>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("## $stockName 종목-지표 상관관계 분석 요약\n")

        // 심리 지표 요약
        val significantFg = fearGreedCorrelations.filter { abs(it.correlation) >= 0.3 }
        if (significantFg.isNotEmpty()) {
            sb.appendLine("### 시장 심리 지표 (Fear & Greed)")
            significantFg.forEach { sb.appendLine("- ${it.description}") }
            sb.appendLine()
        }

        // 기술 지표 요약
        val significantOsc = oscillatorCorrelations.filter { abs(it.correlation) >= 0.3 }
        if (significantOsc.isNotEmpty()) {
            sb.appendLine("### 기술 지표 (과매수/과매도)")
            significantOsc.forEach { sb.appendLine("- ${it.description}") }
            sb.appendLine()
        }

        // 자금 동향 요약
        val significantDeposit = depositCorrelations.filter { abs(it.correlation) >= 0.3 }
        if (significantDeposit.isNotEmpty()) {
            sb.appendLine("### 자금 동향 (예탁금/신용)")
            significantDeposit.forEach { sb.appendLine("- ${it.description}") }
            sb.appendLine()
        }

        // ETF 수급 요약
        val significantEtf = etfCorrelations.filter { abs(it.correlation) >= 0.3 }
        if (significantEtf.isNotEmpty()) {
            sb.appendLine("### ETF 수급 동향")
            significantEtf.forEach { sb.appendLine("- ${it.description}") }
            sb.appendLine()
        }

        if (significantFg.isEmpty() && significantOsc.isEmpty() &&
            significantDeposit.isEmpty() && significantEtf.isEmpty()) {
            sb.appendLine("분석 기간 내 유의미한 상관관계가 발견되지 않았습니다.")
        }

        return sb.toString()
    }

    // ========== AI 프롬프트 생성 ==========

    private fun createStockIndicatorCorrelationPrompt(result: StockIndicatorCorrelationResult): String {
        return buildString {
            appendLine("당신은 한국 주식 시장 전문 애널리스트입니다.")
            appendLine("다음 종목의 시장지표 상관관계 분석 결과를 해석하여 투자 전망을 제공해주세요.")
            appendLine()
            appendLine("## 종목 정보")
            appendLine("- 종목명: ${result.stockName}")
            appendLine("- 종목코드: ${result.ticker}")
            appendLine("- 분석 시장: ${result.market}")
            appendLine("- 분석 기간: ${result.startDate} ~ ${result.endDate}")
            appendLine("- 데이터 포인트: ${result.totalDataPoints}일")
            appendLine()

            // Fear & Greed 상관관계
            if (result.fearGreedCorrelations.isNotEmpty()) {
                appendLine("## 시장 심리 지표 상관관계 (Fear & Greed)")
                result.fearGreedCorrelations.forEach { corr ->
                    appendLine("- ${MarketIndicatorType.valueOf(corr.indicatorType).displayName} vs ${StockMetricType.valueOf(corr.stockMetricType).displayName}: ${String.format("%.3f", corr.correlation)}")
                }
                appendLine()
            }

            // Oscillator 상관관계
            if (result.oscillatorCorrelations.isNotEmpty()) {
                appendLine("## 시장 과매수/과매도 상관관계")
                result.oscillatorCorrelations.forEach { corr ->
                    appendLine("- ${MarketIndicatorType.valueOf(corr.indicatorType).displayName} vs ${StockMetricType.valueOf(corr.stockMetricType).displayName}: ${String.format("%.3f", corr.correlation)}")
                }
                appendLine()
            }

            // 예탁금/신용 상관관계
            if (result.depositCorrelations.isNotEmpty()) {
                appendLine("## 자금 동향 상관관계 (예탁금/신용)")
                result.depositCorrelations.forEach { corr ->
                    appendLine("- ${MarketIndicatorType.valueOf(corr.indicatorType).displayName} vs ${StockMetricType.valueOf(corr.stockMetricType).displayName}: ${String.format("%.3f", corr.correlation)}")
                }
                appendLine()
            }

            // ETF 통계 상관관계
            if (result.etfCorrelations.isNotEmpty()) {
                appendLine("## ETF 수급 상관관계")
                result.etfCorrelations.forEach { corr ->
                    appendLine("- ${MarketIndicatorType.valueOf(corr.indicatorType).displayName} vs ${StockMetricType.valueOf(corr.stockMetricType).displayName}: ${String.format("%.3f", corr.correlation)}")
                }
                appendLine()
            }

            // Top 상관관계
            if (result.topPositiveCorrelations.isNotEmpty()) {
                appendLine("## 가장 강한 양의 상관관계 Top 5")
                result.topPositiveCorrelations.forEach { corr ->
                    appendLine("- ${corr.description}")
                }
                appendLine()
            }

            if (result.topNegativeCorrelations.isNotEmpty()) {
                appendLine("## 가장 강한 음의 상관관계 Top 5")
                result.topNegativeCorrelations.forEach { corr ->
                    appendLine("- ${corr.description}")
                }
                appendLine()
            }

            appendLine("## 분석 요청")
            appendLine("위 상관관계 분석 결과를 종합하여 다음 JSON 형식으로 투자 신호를 제공해주세요:")
            appendLine()
            appendLine("```json")
            appendLine("{")
            appendLine("  \"signal\": \"STRONG_BUY|BUY|NEUTRAL|SELL|STRONG_SELL\",")
            appendLine("  \"confidence\": 0.0-1.0,")
            appendLine("  \"upProbability\": 0-100,")
            appendLine("  \"downProbability\": 0-100,")
            appendLine("  \"reasoning\": \"상관관계 분석 기반 상세 근거 (시장 심리, 자금 흐름, ETF 수급 각각 분석)\",")
            appendLine("  \"keyFactors\": [\"핵심 상관관계 1\", \"핵심 상관관계 2\", \"핵심 상관관계 3\"],")
            appendLine("  \"recommendation\": \"상관관계 기반 투자 권장사항\",")
            appendLine("  \"riskLevel\": \"LOW|MEDIUM|HIGH\"")
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine("**분석 시 고려사항:**")
            appendLine("1. 시장 심리(Fear & Greed)와 종목 간 상관관계가 높으면 시장 전체 흐름에 민감한 종목")
            appendLine("2. 예탁금/신용잔고와의 상관관계는 개인투자자 자금 흐름과의 연관성 시사")
            appendLine("3. ETF 수급과의 상관관계는 기관/패시브 자금 흐름과의 연관성 시사")
            appendLine("4. 양/음의 상관관계 방향과 강도를 종합하여 투자 타이밍 판단")
            appendLine("5. 상관관계가 낮은 경우 해당 종목은 시장 지표와 독립적으로 움직일 수 있음")
        }
    }

    // ========== Helper 메서드 ==========

    private fun extractKeyCorrelations(result: StockIndicatorCorrelationResult): List<String> {
        val allCorrelations = result.topPositiveCorrelations + result.topNegativeCorrelations
        return allCorrelations
            .filter { abs(it.correlation) >= 0.3 }
            .sortedByDescending { abs(it.correlation) }
            .take(5)
            .map { it.description }
    }

    private fun extractSentimentImpact(reasoning: String): String {
        val keywords = listOf("심리", "Fear", "Greed", "RSI", "모멘텀", "감정", "공포", "탐욕")
        val sentences = reasoning.split(".")
        return sentences
            .filter { sentence -> keywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) } }
            .take(2)
            .joinToString(". ")
            .ifEmpty { "시장 심리 지표와의 상관관계를 참고하세요." }
    }

    private fun extractFundFlowImpact(reasoning: String): String {
        val keywords = listOf("예탁금", "신용", "자금", "유동성", "개인투자자", "자금흐름")
        val sentences = reasoning.split(".")
        return sentences
            .filter { sentence -> keywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) } }
            .take(2)
            .joinToString(". ")
            .ifEmpty { "자금 동향 지표와의 상관관계를 참고하세요." }
    }

    private fun extractEtfFlowImpact(reasoning: String): String {
        val keywords = listOf("ETF", "편입", "편출", "비중", "패시브", "기관")
        val sentences = reasoning.split(".")
        return sentences
            .filter { sentence -> keywords.any { keyword -> sentence.contains(keyword, ignoreCase = true) } }
            .take(2)
            .joinToString(". ")
            .ifEmpty { "ETF 수급 지표와의 상관관계를 참고하세요." }
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
 * 4개 값 튜플 (병렬 수집용)
 */
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
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
