package com.etfmonitor.analysis

import com.etfmonitor.database.DailyEtfStatisticsDao
import com.etfmonitor.utils.AppLogger
import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.MarketOscillatorDao
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import com.etfmonitor.database.entities.DailyEtfStatistics
import com.etfmonitor.database.entities.FearGreedIndex
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.database.entities.MarketIndex
import com.etfmonitor.database.entities.MarketOscillatorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 상관관계 분석기
 * 각 지표와 시장 지수 간의 상관관계를 로컬에서 계산
 */
@Singleton
class CorrelationAnalyzer @Inject constructor(
    private val marketIndexDao: MarketIndexDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao,
    private val fearGreedDao: FearGreedDao,
    private val marketOscillatorDao: MarketOscillatorDao,
    private val marketDepositDao: MarketDepositDao
) {
    companion object {
        private val logger = AppLogger.getLogger("CorrelationAnalyzer")
        private const val DEFAULT_PERIOD_DAYS = 30
        private const val MIN_DATA_POINTS = 10
    }

    private val json = Json { prettyPrint = true }

    /**
     * 특정 시장의 상관관계 분석 수행
     * @param market 시장 (KOSPI/KOSDAQ)
     * @param endDate 분석 종료 날짜 (기준일)
     * @param periodDays 분석 기간 (일)
     * @return 상관관계 분석 결과
     */
    suspend fun analyze(
        market: String,
        endDate: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Result<CorrelationAnalysisResult> = withContext(Dispatchers.Default) {
        try {
            logger.d("Starting correlation analysis for $market, endDate=$endDate, period=$periodDays days")

            // 1. 분석 기간 계산
            val startDate = calculateStartDate(endDate, periodDays)

            // 2. 데이터 수집
            val analysisData = collectAnalysisData(market, startDate, endDate)

            if (analysisData.marketIndices.size < MIN_DATA_POINTS) {
                return@withContext Result.failure(
                    Exception("분석에 필요한 최소 데이터가 부족합니다 (${analysisData.marketIndices.size}/${MIN_DATA_POINTS}일)")
                )
            }

            // 3. 상관관계 계산
            val correlations = calculateCorrelations(analysisData)

            // 4. 종합 점수 및 신호 생성
            val compositeResult = generateCompositeSignal(correlations, analysisData)

            // 5. 결과 생성
            val result = CorrelationAnalysisResult(
                id = "$market-$endDate",
                market = market,
                analysisDate = endDate,
                periodDays = periodDays,

                // ETF 상관관계
                etfNewStockCorrelation = correlations.etfNewStockCorrelation,
                etfRemovedStockCorrelation = correlations.etfRemovedStockCorrelation,
                etfIncreasedCorrelation = correlations.etfIncreasedCorrelation,
                etfDecreasedCorrelation = correlations.etfDecreasedCorrelation,
                etfNetFlowCorrelation = correlations.etfNetFlowCorrelation,
                cashDepositCorrelation = correlations.cashDepositCorrelation,

                // 자금 동향 상관관계
                marketDepositCorrelation = correlations.marketDepositCorrelation,
                creditBalanceCorrelation = correlations.creditBalanceCorrelation,

                // Fear & Greed 상관관계
                fearGreedCorrelation = correlations.fearGreedCorrelation,
                fearGreedLeadCorrelation = correlations.fearGreedLeadCorrelation,

                // Oscillator 상관관계
                oscillatorCorrelation = correlations.oscillatorCorrelation,
                oscillatorLeadCorrelation = correlations.oscillatorLeadCorrelation,

                // 종합 결과
                compositeScore = compositeResult.compositeScore,
                signal = compositeResult.signal.name,
                confidence = compositeResult.confidence,
                upProbability = compositeResult.upProbability,
                downProbability = compositeResult.downProbability,

                analysisContext = json.encodeToString(createAnalysisContext(analysisData, correlations))
            )

            logger.d("Correlation analysis completed: signal=${result.signal}, confidence=${result.confidence}")
            Result.success(result)

        } catch (e: Exception) {
            logger.e("Correlation analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * 분석 데이터 수집
     */
    private suspend fun collectAnalysisData(
        market: String,
        startDate: String,
        endDate: String
    ): AnalysisDataSet = withContext(Dispatchers.IO) {
        logger.d("Collecting data from $startDate to $endDate for $market")

        val marketIndices = marketIndexDao.getByMarketAndDateRangeSuspend(market, startDate, endDate)
        val etfStats = dailyEtfStatisticsDao.getByDateRangeSuspend(startDate, endDate)

        // Optional 데이터 - 예외 처리 (first() 사용하여 Flow에서 단일 값 추출)
        val fearGreedData = try {
            fearGreedDao.getByMarketAndDateRange(market, startDate, endDate).first()
        } catch (e: Exception) {
            logger.w("Fear & Greed data not available: ${e.message}")
            emptyList()
        }

        val oscillatorData = try {
            marketOscillatorDao.getDataByDateRange(market, startDate, endDate).first()
        } catch (e: Exception) {
            logger.w("Oscillator data not available: ${e.message}")
            emptyList()
        }

        val marketDeposits = try {
            marketDepositDao.getAllDeposits().first().filter { it.date in startDate..endDate }
        } catch (e: Exception) {
            logger.w("Market deposit data not available: ${e.message}")
            emptyList()
        }

        logger.d("Collected: indices=${marketIndices.size}, etfStats=${etfStats.size}, " +
                "fearGreed=${fearGreedData.size}, oscillator=${oscillatorData.size}, deposits=${marketDeposits.size}")

        AnalysisDataSet(
            market = market,
            startDate = startDate,
            endDate = endDate,
            marketIndices = marketIndices,
            etfStatistics = etfStats,
            fearGreedData = fearGreedData,
            oscillatorData = oscillatorData,
            marketDeposits = marketDeposits
        )
    }

    /**
     * 상관관계 계산
     */
    private fun calculateCorrelations(data: AnalysisDataSet): CorrelationResults {
        // 날짜를 기준으로 데이터 정렬 및 매칭
        val dateToIndex = data.marketIndices.associateBy { it.date }
        val dateToEtf = data.etfStatistics.associateBy { it.date }
        val dateToFearGreed = data.fearGreedData.associateBy { it.date }
        val dateToOscillator = data.oscillatorData.associateBy { it.date }
        val dateToDeposit = data.marketDeposits.associateBy { it.date }

        // 공통 날짜 추출
        val commonDates = data.marketIndices.map { it.date }
            .filter { dateToEtf.containsKey(it) }
            .sorted()

        // 지수 등락률 시리즈
        val indexReturns = mutableListOf<Double>()
        val prevDates = mutableListOf<String>()

        for (i in 1 until commonDates.size) {
            val currDate = commonDates[i]
            val prevDate = commonDates[i - 1]
            val currIndex = dateToIndex[currDate]
            val prevIndex = dateToIndex[prevDate]

            if (currIndex != null && prevIndex != null && prevIndex.closePrice > 0) {
                val returnRate = (currIndex.closePrice - prevIndex.closePrice) / prevIndex.closePrice * 100
                indexReturns.add(returnRate)
                prevDates.add(prevDate) // 전일 기준
            }
        }

        // ETF 통계 시리즈 (전일 기준으로 매칭 - 선행 지표)
        val etfNewStocks = prevDates.mapNotNull { dateToEtf[it]?.newStockCount?.toDouble() }
        val etfRemovedStocks = prevDates.mapNotNull { dateToEtf[it]?.removedStockCount?.toDouble() }
        val etfIncreased = prevDates.mapNotNull { dateToEtf[it]?.increasedStockCount?.toDouble() }
        val etfDecreased = prevDates.mapNotNull { dateToEtf[it]?.decreasedStockCount?.toDouble() }
        val etfNetFlow = prevDates.mapNotNull { date ->
            dateToEtf[date]?.let { (it.newStockCount - it.removedStockCount).toDouble() }
        }
        val cashDepositChange = prevDates.mapNotNull { dateToEtf[it]?.cashDepositChangeRate }

        // ETF 상관관계 계산
        val etfNewCorr = calculatePearsonCorrelation(etfNewStocks, indexReturns.take(etfNewStocks.size))
        val etfRemovedCorr = calculatePearsonCorrelation(etfRemovedStocks, indexReturns.take(etfRemovedStocks.size))
        val etfIncreasedCorr = calculatePearsonCorrelation(etfIncreased, indexReturns.take(etfIncreased.size))
        val etfDecreasedCorr = calculatePearsonCorrelation(etfDecreased, indexReturns.take(etfDecreased.size))
        val etfNetFlowCorr = calculatePearsonCorrelation(etfNetFlow, indexReturns.take(etfNetFlow.size))
        val cashDepositCorr = calculatePearsonCorrelation(cashDepositChange, indexReturns.take(cashDepositChange.size))

        // 자금 동향 상관관계
        val depositChanges = prevDates.mapNotNull { dateToDeposit[it]?.depositChange }
        val creditChanges = prevDates.mapNotNull { dateToDeposit[it]?.creditChange }
        val marketDepositCorr = if (depositChanges.size >= MIN_DATA_POINTS) {
            calculatePearsonCorrelation(depositChanges, indexReturns.take(depositChanges.size))
        } else null
        val creditCorr = if (creditChanges.size >= MIN_DATA_POINTS) {
            calculatePearsonCorrelation(creditChanges, indexReturns.take(creditChanges.size))
        } else null

        // Fear & Greed 상관관계
        val fearGreedValues = prevDates.mapNotNull { dateToFearGreed[it]?.fearGreedValue }
        val fearGreedCorr = if (fearGreedValues.size >= MIN_DATA_POINTS) {
            calculatePearsonCorrelation(fearGreedValues, indexReturns.take(fearGreedValues.size))
        } else null

        // Fear & Greed 선행 상관관계 (2일 전 기준)
        val fearGreedLead = if (commonDates.size >= 3) {
            val leadValues = mutableListOf<Double>()
            val leadReturns = mutableListOf<Double>()
            for (i in 2 until commonDates.size) {
                val leadDate = commonDates[i - 2]
                val fg = dateToFearGreed[leadDate]?.fearGreedValue
                if (fg != null && i - 1 < indexReturns.size) {
                    leadValues.add(fg)
                    leadReturns.add(indexReturns[i - 1])
                }
            }
            if (leadValues.size >= MIN_DATA_POINTS) {
                calculatePearsonCorrelation(leadValues, leadReturns)
            } else null
        } else null

        // Oscillator 상관관계
        val oscillatorValues = prevDates.mapNotNull { dateToOscillator[it]?.oscillator }
        val oscillatorCorr = if (oscillatorValues.size >= MIN_DATA_POINTS) {
            calculatePearsonCorrelation(oscillatorValues, indexReturns.take(oscillatorValues.size))
        } else null

        // Oscillator 선행 상관관계
        val oscillatorLead = if (commonDates.size >= 3) {
            val leadValues = mutableListOf<Double>()
            val leadReturns = mutableListOf<Double>()
            for (i in 2 until commonDates.size) {
                val leadDate = commonDates[i - 2]
                val osc = dateToOscillator[leadDate]?.oscillator
                if (osc != null && i - 1 < indexReturns.size) {
                    leadValues.add(osc)
                    leadReturns.add(indexReturns[i - 1])
                }
            }
            if (leadValues.size >= MIN_DATA_POINTS) {
                calculatePearsonCorrelation(leadValues, leadReturns)
            } else null
        } else null

        return CorrelationResults(
            etfNewStockCorrelation = etfNewCorr,
            etfRemovedStockCorrelation = etfRemovedCorr,
            etfIncreasedCorrelation = etfIncreasedCorr,
            etfDecreasedCorrelation = etfDecreasedCorr,
            etfNetFlowCorrelation = etfNetFlowCorr,
            cashDepositCorrelation = cashDepositCorr,
            marketDepositCorrelation = marketDepositCorr,
            creditBalanceCorrelation = creditCorr,
            fearGreedCorrelation = fearGreedCorr,
            fearGreedLeadCorrelation = fearGreedLead,
            oscillatorCorrelation = oscillatorCorr,
            oscillatorLeadCorrelation = oscillatorLead
        )
    }

    /**
     * Pearson 상관계수 계산
     */
    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0

        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }

        val denominator = sqrt(denomX) * sqrt(denomY)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /**
     * 종합 신호 생성
     */
    private fun generateCompositeSignal(
        correlations: CorrelationResults,
        data: AnalysisDataSet
    ): CompositeSignalResult {
        // 가중치 설정
        val weights = mapOf(
            "etfNetFlow" to 0.25,
            "cashDeposit" to 0.15,
            "marketDeposit" to 0.15,
            "fearGreed" to 0.20,
            "oscillator" to 0.25
        )

        // 최신 데이터 기반 신호 점수 계산
        val latestEtf = data.etfStatistics.maxByOrNull { it.date }
        val latestFearGreed = data.fearGreedData.maxByOrNull { it.date }
        val latestOscillator = data.oscillatorData.maxByOrNull { it.date }
        val latestDeposit = data.marketDeposits.maxByOrNull { it.date }

        var compositeScore = 0.0
        var totalWeight = 0.0

        // ETF 순유입 신호 (양의 상관관계 + 현재 순유입 양수 = 강세)
        if (latestEtf != null) {
            val netFlow = latestEtf.newStockCount - latestEtf.removedStockCount
            val netFlowSignal = when {
                netFlow > 10 -> 1.0
                netFlow > 5 -> 0.5
                netFlow > 0 -> 0.25
                netFlow > -5 -> -0.25
                netFlow > -10 -> -0.5
                else -> -1.0
            }
            // 상관관계가 양수이고 순유입도 양수면 상승 신호 강화
            val adjustedSignal = netFlowSignal * (1 + correlations.etfNetFlowCorrelation.coerceIn(-0.5, 0.5))
            weights["etfNetFlow"]?.let { weight ->
                compositeScore += adjustedSignal * weight
                totalWeight += weight
            }
        }

        // 원화예금 변화 신호 (예금 감소 = 매수 의욕 = 강세)
        if (latestEtf != null) {
            val cashSignal = when {
                latestEtf.cashDepositChangeRate < -2.0 -> 1.0  // 큰 감소 = 강세
                latestEtf.cashDepositChangeRate < -0.5 -> 0.5
                latestEtf.cashDepositChangeRate < 0.5 -> 0.0
                latestEtf.cashDepositChangeRate < 2.0 -> -0.5
                else -> -1.0  // 큰 증가 = 약세 (관망)
            }
            weights["cashDeposit"]?.let { weight ->
                compositeScore += cashSignal * weight
                totalWeight += weight
            }
        }

        // 고객예탁금 신호
        if (latestDeposit != null && correlations.marketDepositCorrelation != null) {
            val depositSignal = when {
                latestDeposit.depositChange > 1000 -> -0.5  // 예탁금 증가 = 관망
                latestDeposit.depositChange > 0 -> -0.25
                latestDeposit.depositChange > -1000 -> 0.25
                else -> 0.5  // 예탁금 감소 = 매수 의욕
            }
            weights["marketDeposit"]?.let { weight ->
                compositeScore += depositSignal * weight
                totalWeight += weight
            }
        }

        // Fear & Greed 신호 (극단값에서 반전 고려)
        if (latestFearGreed != null) {
            val fgValue = latestFearGreed.fearGreedValue
            val fgSignal = when {
                fgValue < 0.2 -> 1.0   // 극단적 공포 = 반등 기대
                fgValue < 0.35 -> 0.5  // 공포 = 매수 기회
                fgValue < 0.65 -> 0.0  // 중립
                fgValue < 0.8 -> -0.5  // 탐욕 = 주의
                else -> -1.0           // 극단적 탐욕 = 조정 기대
            }
            weights["fearGreed"]?.let { weight ->
                compositeScore += fgSignal * weight
                totalWeight += weight
            }
        }

        // Oscillator 신호 (과매수/과매도)
        if (latestOscillator != null) {
            val oscValue = latestOscillator.oscillator
            val oscSignal = when {
                oscValue < -70 -> 1.0   // 극단적 과매도 = 반등
                oscValue < -30 -> 0.5   // 과매도
                oscValue < 30 -> 0.0    // 중립
                oscValue < 70 -> -0.5   // 과매수
                else -> -1.0            // 극단적 과매수 = 조정
            }
            weights["oscillator"]?.let { weight ->
                compositeScore += oscSignal * weight
                totalWeight += weight
            }
        }

        // 정규화
        val normalizedScore = if (totalWeight > 0) compositeScore / totalWeight else 0.0

        // 신호 결정
        val signal = when {
            normalizedScore >= 0.6 -> SignalType.STRONG_BUY
            normalizedScore >= 0.25 -> SignalType.BUY
            normalizedScore >= -0.25 -> SignalType.NEUTRAL
            normalizedScore >= -0.6 -> SignalType.SELL
            else -> SignalType.STRONG_SELL
        }

        // 확률 계산 (종합 점수 기반)
        val upProbability = ((normalizedScore + 1) / 2 * 100).coerceIn(10.0, 90.0)
        val downProbability = 100 - upProbability

        // 신뢰도 계산 (데이터 충분성 + 상관관계 강도)
        val dataCompleteness = totalWeight / weights.values.sum()
        val correlationStrength = listOfNotNull(
            correlations.etfNetFlowCorrelation,
            correlations.fearGreedCorrelation,
            correlations.oscillatorCorrelation
        ).map { kotlin.math.abs(it) }.average().takeIf { !it.isNaN() } ?: 0.0

        val confidence = (dataCompleteness * 0.5 + correlationStrength * 0.5).coerceIn(0.0, 1.0)

        return CompositeSignalResult(
            compositeScore = normalizedScore,
            signal = signal,
            confidence = confidence,
            upProbability = upProbability,
            downProbability = downProbability
        )
    }

    /**
     * 분석 컨텍스트 생성 (AI에게 전달할 요약)
     */
    private fun createAnalysisContext(
        data: AnalysisDataSet,
        correlations: CorrelationResults
    ): AnalysisContext {
        val latestIndex = data.marketIndices.maxByOrNull { it.date }
        val latestEtf = data.etfStatistics.maxByOrNull { it.date }
        val latestFearGreed = data.fearGreedData.maxByOrNull { it.date }
        val latestOscillator = data.oscillatorData.maxByOrNull { it.date }
        val latestDeposit = data.marketDeposits.maxByOrNull { it.date }

        return AnalysisContext(
            market = data.market,
            analysisDate = data.endDate,
            periodDays = (data.marketIndices.size),
            currentIndex = latestIndex?.closePrice ?: 0.0,
            indexChangeRate = latestIndex?.changeRate ?: 0.0,

            etfSummary = latestEtf?.let {
                EtfSummary(
                    newStocks = it.newStockCount,
                    removedStocks = it.removedStockCount,
                    increasedStocks = it.increasedStockCount,
                    decreasedStocks = it.decreasedStockCount,
                    cashDepositChange = it.cashDepositChangeRate
                )
            },

            fearGreedValue = latestFearGreed?.fearGreedValue,
            oscillatorValue = latestOscillator?.oscillator,
            depositChange = latestDeposit?.depositChange,

            correlations = CorrelationSummary(
                etfNetFlow = correlations.etfNetFlowCorrelation,
                cashDeposit = correlations.cashDepositCorrelation,
                fearGreed = correlations.fearGreedCorrelation,
                oscillator = correlations.oscillatorCorrelation
            )
        )
    }

    /**
     * 시작 날짜 계산
     */
    private fun calculateStartDate(endDate: String, periodDays: Int): String {
        val parts = endDate.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()

        val calendar = java.util.Calendar.getInstance().apply {
            set(year, month - 1, day)
            add(java.util.Calendar.DAY_OF_YEAR, -periodDays)
        }

        return String.format(
            "%04d-%02d-%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}

// ========== 데이터 클래스 ==========

/**
 * 분석 데이터셋
 */
data class AnalysisDataSet(
    val market: String,
    val startDate: String,
    val endDate: String,
    val marketIndices: List<MarketIndex>,
    val etfStatistics: List<DailyEtfStatistics>,
    val fearGreedData: List<FearGreedIndex>,
    val oscillatorData: List<MarketOscillatorData>,
    val marketDeposits: List<MarketDeposit>
)

/**
 * 상관관계 계산 결과
 */
data class CorrelationResults(
    val etfNewStockCorrelation: Double,
    val etfRemovedStockCorrelation: Double,
    val etfIncreasedCorrelation: Double,
    val etfDecreasedCorrelation: Double,
    val etfNetFlowCorrelation: Double,
    val cashDepositCorrelation: Double,
    val marketDepositCorrelation: Double?,
    val creditBalanceCorrelation: Double?,
    val fearGreedCorrelation: Double?,
    val fearGreedLeadCorrelation: Double?,
    val oscillatorCorrelation: Double?,
    val oscillatorLeadCorrelation: Double?
)

/**
 * 종합 신호 결과
 */
data class CompositeSignalResult(
    val compositeScore: Double,
    val signal: SignalType,
    val confidence: Double,
    val upProbability: Double,
    val downProbability: Double
)

/**
 * 신호 타입
 */
enum class SignalType {
    STRONG_BUY,
    BUY,
    NEUTRAL,
    SELL,
    STRONG_SELL;

    fun toKorean(): String = when (this) {
        STRONG_BUY -> "강력 매수"
        BUY -> "매수"
        NEUTRAL -> "중립"
        SELL -> "매도"
        STRONG_SELL -> "강력 매도"
    }
}

// ========== 분석 컨텍스트 (JSON 직렬화용) ==========

@Serializable
data class AnalysisContext(
    val market: String,
    val analysisDate: String,
    val periodDays: Int,
    val currentIndex: Double,
    val indexChangeRate: Double,
    val etfSummary: EtfSummary?,
    val fearGreedValue: Double?,
    val oscillatorValue: Double?,
    val depositChange: Double?,
    val correlations: CorrelationSummary
)

@Serializable
data class EtfSummary(
    val newStocks: Int,
    val removedStocks: Int,
    val increasedStocks: Int,
    val decreasedStocks: Int,
    val cashDepositChange: Double
)

@Serializable
data class CorrelationSummary(
    val etfNetFlow: Double,
    val cashDeposit: Double,
    val fearGreed: Double?,
    val oscillator: Double?
)
