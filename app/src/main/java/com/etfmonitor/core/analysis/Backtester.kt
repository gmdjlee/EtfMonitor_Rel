package com.etfmonitor.core.analysis

import com.etfmonitor.core.network.ai.BacktestResult
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.network.ai.SignalRecord
import com.etfmonitor.core.network.ai.SignalType
import com.etfmonitor.core.database.MarketIndexDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 신호 백테스터
 * AI가 생성한 매매 신호의 과거 정확도를 검증
 *
 * 거래비용 기본값:
 * - 수수료: 0.015% (증권사 평균 온라인 수수료)
 * - 슬리피지: 0.05% (시장가 주문 시 예상 슬리피지)
 * - 왕복 거래비용 = (수수료 + 슬리피지) × 2 = 0.13%
 */
@Singleton
class Backtester @Inject constructor(
    private val marketIndexDao: MarketIndexDao
) {
    companion object {
        private val logger = AppLogger.getLogger("Backtester")

        // 거래비용 기본값 (%)
        const val DEFAULT_COMMISSION_RATE = 0.015  // 증권사 수수료 0.015%
        const val DEFAULT_SLIPPAGE_RATE = 0.05     // 슬리피지 0.05%
    }

    /**
     * 신호 기록을 백테스트하여 성과 분석
     *
     * @param market 시장 (KOSPI/KOSDAQ)
     * @param signals 신호 기록 리스트
     * @param holdingPeriod 보유 기간 (일)
     * @param commissionRate 수수료율 (%, 기본값 0.015%)
     * @param slippageRate 슬리피지율 (%, 기본값 0.05%)
     */
    suspend fun backtest(
        market: String,
        signals: List<SignalRecord>,
        holdingPeriod: Int = 5, // 보유 기간 (일)
        commissionRate: Double = DEFAULT_COMMISSION_RATE,
        slippageRate: Double = DEFAULT_SLIPPAGE_RATE
    ): Result<BacktestResult> = withContext(Dispatchers.IO) {
        try {
            if (signals.isEmpty()) {
                return@withContext Result.failure(Exception("백테스트할 신호가 없습니다"))
            }

            logger.d("Backtesting ${signals.size} signals for $market with $holdingPeriod days holding period")
            logger.d("Transaction costs: commission=$commissionRate%, slippage=$slippageRate%")

            // 신호별 미래 수익률 계산
            val enrichedSignals = calculateFutureReturns(market, signals, holdingPeriod)

            if (enrichedSignals.isEmpty()) {
                return@withContext Result.failure(Exception("미래 데이터가 충분하지 않습니다"))
            }

            // 성과 지표 계산
            val totalSignals = enrichedSignals.size
            val correctSignals = enrichedSignals.count { it.wasCorrect == true }
            val accuracy = (correctSignals.toDouble() / totalSignals) * 100

            val returns = enrichedSignals.mapNotNull {
                when (holdingPeriod) {
                    1 -> it.actualReturn1Day
                    5 -> it.actualReturn5Days
                    10 -> it.actualReturn10Days
                    else -> it.actualReturn5Days
                }
            }

            // 거래비용 계산: 왕복 (매수 + 매도) 거래비용
            val roundTripCost = (commissionRate + slippageRate) * 2
            val totalTransactionCost = roundTripCost * returns.size

            // 거래비용 차감 후 순수익률
            val netReturns = returns.map { it - roundTripCost }

            val averageReturn = returns.average()
            val netReturn = netReturns.average()
            val winningReturns = netReturns.filter { it > 0 }
            val winRate = (winningReturns.size.toDouble() / netReturns.size) * 100

            // 최대 낙폭 계산 (거래비용 반영)
            val maxDrawdown = calculateMaxDrawdown(netReturns)

            // 샤프 비율 계산 (거래비용 차감 전/후)
            val sharpeRatio = calculateSharpeRatio(returns)
            val netSharpeRatio = calculateSharpeRatio(netReturns)

            val period = "${signals.first().date} ~ ${signals.last().date}"

            val result = BacktestResult(
                totalSignals = totalSignals,
                correctSignals = correctSignals,
                accuracy = accuracy,
                averageReturn = averageReturn,
                netReturn = netReturn,
                winRate = winRate,
                maxDrawdown = maxDrawdown,
                sharpeRatio = sharpeRatio,
                netSharpeRatio = netSharpeRatio,
                period = period,
                totalTransactionCost = totalTransactionCost,
                commissionRate = commissionRate,
                slippageRate = slippageRate
            )

            logger.d("Backtest completed: accuracy=$accuracy%, grossReturn=$averageReturn%, netReturn=$netReturn%, winRate=$winRate%")

            Result.success(result)
        } catch (e: Exception) {
            logger.e("Backtest error", e)
            Result.failure(e)
        }
    }

    /**
     * 신호의 미래 수익률 계산
     */
    private suspend fun calculateFutureReturns(
        market: String,
        signals: List<SignalRecord>,
        holdingPeriod: Int
    ): List<SignalRecord> = withContext(Dispatchers.IO) {
        val allDates = signals.map { it.date }.sorted()
        val minDate = allDates.firstOrNull() ?: return@withContext emptyList()
        val maxDate = allDates.lastOrNull() ?: return@withContext emptyList()

        // 전체 기간의 지수 데이터 조회
        val indices = marketIndexDao.getByMarketAndDateRangeSuspend(market, minDate, maxDate)
            .associateBy { it.date }

        signals.mapNotNull { signal ->
            try {
                val currentIndex = signal.indexAtSignal
                if (currentIndex == 0.0) return@mapNotNull null

                // 미래 날짜 찾기 (거래일 기준)
                val futureDate1 = findNthTradingDay(allDates, signal.date, 1)
                val futureDate5 = findNthTradingDay(allDates, signal.date, 5)
                val futureDate10 = findNthTradingDay(allDates, signal.date, 10)

                // 미래 지수 값
                val futureIndex1 = futureDate1?.let { indices[it]?.closePrice }
                val futureIndex5 = futureDate5?.let { indices[it]?.closePrice }
                val futureIndex10 = futureDate10?.let { indices[it]?.closePrice }

                // 수익률 계산
                val return1Day = futureIndex1?.let { (it - currentIndex) / currentIndex * 100 }
                val return5Days = futureIndex5?.let { (it - currentIndex) / currentIndex * 100 }
                val return10Days = futureIndex10?.let { (it - currentIndex) / currentIndex * 100 }

                // 신호 정확도 판단 (주로 사용하는 보유기간 기준)
                val actualReturn = when (holdingPeriod) {
                    1 -> return1Day
                    5 -> return5Days
                    10 -> return10Days
                    else -> return5Days
                } ?: return@mapNotNull null

                val wasCorrect = isSignalCorrect(signal.signal, actualReturn)

                signal.copy(
                    indexAfter1Day = futureIndex1,
                    indexAfter5Days = futureIndex5,
                    indexAfter10Days = futureIndex10,
                    actualReturn1Day = return1Day,
                    actualReturn5Days = return5Days,
                    actualReturn10Days = return10Days,
                    wasCorrect = wasCorrect
                )
            } catch (e: Exception) {
                logger.w("Failed to calculate returns for ${signal.date}", e)
                null
            }
        }
    }

    /**
     * N번째 거래일 찾기
     */
    private fun findNthTradingDay(allDates: List<String>, currentDate: String, n: Int): String? {
        val currentIndex = allDates.indexOf(currentDate)
        if (currentIndex == -1) return null

        val futureIndex = currentIndex + n
        return if (futureIndex < allDates.size) allDates[futureIndex] else null
    }

    /**
     * 신호가 정확했는지 판단
     */
    private fun isSignalCorrect(signal: SignalType, actualReturn: Double): Boolean {
        return when (signal) {
            SignalType.STRONG_BUY, SignalType.BUY -> actualReturn > 0
            SignalType.STRONG_SELL, SignalType.SELL -> actualReturn < 0
            SignalType.NEUTRAL -> abs(actualReturn) < 1.0 // ±1% 이내
        }
    }

    /**
     * 최대 낙폭 계산 (Maximum Drawdown)
     */
    private fun calculateMaxDrawdown(returns: List<Double>): Double {
        if (returns.isEmpty()) return 0.0

        var peak = 100.0 // 초기 자본 100
        var maxDrawdown = 0.0

        var currentCapital = 100.0
        for (returnValue in returns) {
            currentCapital *= (1 + returnValue / 100)
            if (currentCapital > peak) {
                peak = currentCapital
            }
            val drawdown = (peak - currentCapital) / peak * 100
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
            }
        }

        return maxDrawdown
    }

    /**
     * 샤프 비율 계산
     * (평균 수익률 - 무위험 수익률) / 수익률 표준편차
     * 무위험 수익률은 0으로 가정
     */
    private fun calculateSharpeRatio(returns: List<Double>): Double? {
        if (returns.size < 2) return null

        val avgReturn = returns.average()
        val variance = returns.map { (it - avgReturn).pow(2) }.average()
        val stdDev = sqrt(variance)

        return if (stdDev > 0) avgReturn / stdDev else null
    }

    /**
     * 신호 타입별 성과 분석
     */
    suspend fun analyzeBySignalType(
        signals: List<SignalRecord>
    ): Map<SignalType, SignalPerformance> = withContext(Dispatchers.IO) {
        SignalType.values().associateWith { signalType ->
            val filtered = signals.filter { it.signal == signalType && it.wasCorrect != null }

            if (filtered.isEmpty()) {
                SignalPerformance(
                    signalType = signalType,
                    count = 0,
                    accuracy = 0.0,
                    averageReturn = 0.0
                )
            } else {
                val correct = filtered.count { it.wasCorrect == true }
                val accuracy = (correct.toDouble() / filtered.size) * 100
                val avgReturn = filtered.mapNotNull { it.actualReturn5Days }.average()

                SignalPerformance(
                    signalType = signalType,
                    count = filtered.size,
                    accuracy = accuracy,
                    averageReturn = avgReturn
                )
            }
        }
    }

    /**
     * 신뢰도별 성과 분석
     */
    suspend fun analyzeByConfidence(
        signals: List<SignalRecord>
    ): List<ConfidencePerformance> = withContext(Dispatchers.IO) {
        val confidenceBins = listOf(
            0.0 to 0.3,
            0.3 to 0.5,
            0.5 to 0.7,
            0.7 to 0.9,
            0.9 to 1.0
        )

        confidenceBins.map { (minConf, maxConf) ->
            val filtered = signals.filter {
                it.confidence >= minConf && it.confidence < maxConf && it.wasCorrect != null
            }

            if (filtered.isEmpty()) {
                ConfidencePerformance(
                    confidenceRange = "$minConf ~ $maxConf",
                    count = 0,
                    accuracy = 0.0,
                    averageReturn = 0.0
                )
            } else {
                val correct = filtered.count { it.wasCorrect == true }
                val accuracy = (correct.toDouble() / filtered.size) * 100
                val avgReturn = filtered.mapNotNull { it.actualReturn5Days }.average()

                ConfidencePerformance(
                    confidenceRange = "${String.format("%.1f", minConf * 100)}% ~ ${String.format("%.1f", maxConf * 100)}%",
                    count = filtered.size,
                    accuracy = accuracy,
                    averageReturn = avgReturn
                )
            }
        }
    }
}

/**
 * 신호 타입별 성과
 */
data class SignalPerformance(
    val signalType: SignalType,
    val count: Int,
    val accuracy: Double, // %
    val averageReturn: Double // %
)

/**
 * 신뢰도별 성과
 */
data class ConfidencePerformance(
    val confidenceRange: String,
    val count: Int,
    val accuracy: Double, // %
    val averageReturn: Double // %
)
