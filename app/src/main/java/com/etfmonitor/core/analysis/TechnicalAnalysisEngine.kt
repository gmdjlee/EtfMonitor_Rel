package com.etfmonitor.core.analysis

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.ln

/**
 * Technical Analysis Engine - Pure Kotlin Computations
 *
 * Ported from Python trend_signal.py (~130 lines)
 *
 * ## Capabilities
 * - EMA calculation (pandas ewm adjust=False compatible)
 * - OHLCV resampling (weekly, monthly)
 * - Chaikin Money Flow (CMF)
 * - Fear & Greed Index
 * - Buy/Sell signal generation
 * - Elder Impulse System
 * - DeMark TD Setup
 * - Rolling sum for investor trading data
 *
 * ## Design
 * - Pure object (no DI, no coroutines)
 * - Maximum testability and reusability
 * - No external dependencies
 */
object TechnicalAnalysisEngine {

    // ============================================================
    // EMA Calculation
    // ============================================================

    /**
     * Calculate EMA (Exponential Moving Average)
     *
     * Uses pandas ewm(span=n, adjust=False) formula:
     * - alpha = 2 / (period + 1)
     * - EMA[0] = value[0]
     * - EMA[t] = alpha * value[t] + (1 - alpha) * EMA[t-1]
     */
    fun calculateEMA(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()

        val alpha = 2.0 / (period + 1)
        val result = mutableListOf<Double>()

        var ema = values[0]
        result.add(ema)

        for (i in 1 until values.size) {
            ema = alpha * values[i] + (1 - alpha) * ema
            result.add(ema)
        }

        return result
    }

    // ============================================================
    // OHLCV Resampling
    // ============================================================

    data class OHLCVRow(
        val date: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )

    /**
     * Resample daily OHLCV to weekly
     *
     * Groups by ISO week (Monday-Sunday), aggregates:
     * - Open: first of week
     * - High: max of week
     * - Low: min of week
     * - Close: last of week
     * - Volume: sum of week
     */
    fun resampleWeekly(
        dates: List<String>,
        open: List<Double>,
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>
    ): List<OHLCVRow> {
        if (dates.isEmpty()) return emptyList()

        val rows = dates.indices.map { i ->
            OHLCVRow(dates[i], open[i], high[i], low[i], close[i], volume[i])
        }

        // Group by week (Monday = start of week)
        val grouped = rows.groupBy { row ->
            val date = LocalDate.parse(row.date, DateTimeFormatter.ofPattern("yyyyMMdd"))
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

        // Aggregate each week
        return grouped.map { (weekStart, weekRows) ->
            OHLCVRow(
                date = weekRows.last().date, // Use last date of week
                open = weekRows.first().open,
                high = weekRows.maxOf { it.high },
                low = weekRows.minOf { it.low },
                close = weekRows.last().close,
                volume = weekRows.sumOf { it.volume }
            )
        }.sortedBy { it.date }
    }

    /**
     * Resample daily OHLCV to monthly
     *
     * Groups by year-month, aggregates same as weekly
     */
    fun resampleMonthly(
        dates: List<String>,
        open: List<Double>,
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>
    ): List<OHLCVRow> {
        if (dates.isEmpty()) return emptyList()

        val rows = dates.indices.map { i ->
            OHLCVRow(dates[i], open[i], high[i], low[i], close[i], volume[i])
        }

        // Group by year-month
        val grouped = rows.groupBy { row ->
            val date = LocalDate.parse(row.date, DateTimeFormatter.ofPattern("yyyyMMdd"))
            "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
        }

        // Aggregate each month
        return grouped.map { (_, monthRows) ->
            OHLCVRow(
                date = monthRows.last().date, // Use last date of month
                open = monthRows.first().open,
                high = monthRows.maxOf { it.high },
                low = monthRows.minOf { it.low },
                close = monthRows.last().close,
                volume = monthRows.sumOf { it.volume }
            )
        }.sortedBy { it.date }
    }

    // ============================================================
    // Chaikin Money Flow (CMF)
    // ============================================================

    /**
     * Calculate Chaikin Money Flow
     *
     * Formula:
     * MFM = ((Close - Low) - (High - Close)) / (High - Low)
     * MFV = MFM * Volume
     * CMF = rolling_sum(MFV, period) / rolling_sum(Volume, period)
     */
    fun calculateCMF(
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>,
        period: Int = 4
    ): List<Double> {
        if (high.size < period) return List(high.size) { 0.0 }

        val mfv = high.indices.map { i ->
            val hl = high[i] - low[i]
            if (hl == 0.0) {
                0.0
            } else {
                val mfm = ((close[i] - low[i]) - (high[i] - close[i])) / hl
                mfm * volume[i]
            }
        }

        val cmf = mutableListOf<Double>()
        for (i in high.indices) {
            if (i < period - 1) {
                cmf.add(0.0)
            } else {
                val sumMfv = mfv.subList(i - period + 1, i + 1).sum()
                val sumVol = volume.subList(i - period + 1, i + 1).sum().toDouble()
                cmf.add(if (sumVol > 0) sumMfv / sumVol else 0.0)
            }
        }

        return cmf
    }

    // ============================================================
    // Fear & Greed Index
    // ============================================================

    /**
     * Calculate Fear & Greed Index (-1 to +1)
     *
     * Weighted formula:
     * - Momentum (45%): 5-day log return / 0.1 clipped to [-1, 1]
     * - Position (45%): (close - 52w_low) / (52w_high - 52w_low) scaled to [-1, 1]
     * - Volume spike (5%): (volume / 20d_avg - 1) clipped to [-1, 1]
     * - Volatility (5%, inverted): (5d_std / 20d_std - 1) clipped and inverted
     */
    fun calculateFearGreed(
        close: List<Double>,
        volume: List<Long>,
        momPeriod: Int = 5,
        posPeriod: Int = 52
    ): List<Double> {
        if (close.size < posPeriod) return List(close.size) { 0.0 }

        val result = mutableListOf<Double>()

        for (i in close.indices) {
            // Momentum (45%)
            val mom = if (i >= momPeriod) {
                val logRet = ln(close[i] / close[i - momPeriod])
                (logRet / 0.1).coerceIn(-1.0, 1.0)
            } else 0.0

            // Position in 52-week range (45%)
            val pos = if (i >= 10) { // min_periods=10
                val start = maxOf(0, i - posPeriod + 1)
                val rangeClose = close.subList(start, i + 1)
                val hi = rangeClose.maxOrNull() ?: close[i]
                val lo = rangeClose.minOrNull() ?: close[i]
                val rng = hi - lo
                if (rng > 0) {
                    ((close[i] - lo) / rng * 2) - 1
                } else 0.0
            } else 0.0

            // Volume spike (5%)
            val volScore = if (i >= 20 && i >= 5) {
                val start20 = maxOf(0, i - 19)
                val volMa = volume.subList(start20, i + 1).average()
                ((volume[i] / volMa) - 1).coerceIn(-1.0, 1.0)
            } else 0.0

            // Volatility (5%, inverted)
            val volSpike = if (i >= 20) {
                val returns = mutableListOf<Double>()
                for (j in maxOf(1, i - 19)..i) {
                    returns.add((close[j] - close[j - 1]) / close[j - 1])
                }
                val recent5 = returns.takeLast(5)
                val volRecent = stdDev(recent5)
                val volAvg = stdDev(returns)
                val spike = if (volAvg > 0) ((volRecent / volAvg) - 1).coerceIn(-1.0, 1.0) else 0.0
                -spike // Inverted
            } else 0.0

            val fg = mom * 0.45 + pos * 0.45 + volScore * 0.05 + volSpike * 0.05
            result.add(fg)
        }

        return result
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    // ============================================================
    // Signal Generation
    // ============================================================

    data class SignalResult(
        val dates: List<String>,
        val ma: List<Double>,
        val cmf: List<Double>,
        val fearGreed: List<Double>,
        val buySignal: List<Int>,
        val auxBuySignal: List<Int>,
        val sellSignal: List<Int>,
        val auxSellSignal: List<Int>
    )

    /**
     * Generate buy/sell signals
     *
     * Buy conditions (all 3 required for strong buy):
     * - High breakout (H > prev_H)
     * - Above MA (C > MA)
     * - Money inflow (CMF > 0)
     *
     * Sell conditions (all 3 required for strong sell):
     * - Low breakdown (L < prev_L)
     * - Below MA (C < MA)
     * - Money outflow (CMF < 0)
     *
     * Aux signals: 2 of 3 conditions + MA condition
     */
    fun generateSignals(
        dates: List<String>,
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>,
        maPeriod: Int = 20,
        cmfPeriod: Int = 4
    ): SignalResult {
        if (dates.isEmpty()) {
            return SignalResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        // Calculate MA
        val ma = mutableListOf<Double>()
        for (i in close.indices) {
            if (i < maPeriod - 1) {
                ma.add(0.0)
            } else {
                ma.add(close.subList(i - maPeriod + 1, i + 1).average())
            }
        }

        // Calculate CMF and Fear & Greed
        val cmf = calculateCMF(high, low, close, volume, cmfPeriod)
        val fg = calculateFearGreed(close, volume)

        // Generate signals
        val buySignal = mutableListOf<Int>()
        val auxBuySignal = mutableListOf<Int>()
        val sellSignal = mutableListOf<Int>()
        val auxSellSignal = mutableListOf<Int>()

        for (i in dates.indices) {
            val prevHigh = if (i > 0) high[i - 1] else high[i]
            val prevLow = if (i > 0) low[i - 1] else low[i]

            // Buy conditions
            val b1 = high[i] > prevHigh
            val b2 = close[i] > ma[i]
            val b3 = cmf[i] > 0
            val bCnt = listOf(b1, b2, b3).count { it }

            buySignal.add(if (bCnt == 3) 1 else 0)
            auxBuySignal.add(if (bCnt == 2 && b2) 1 else 0)

            // Sell conditions
            val s1 = low[i] < prevLow
            val s2 = close[i] < ma[i]
            val s3 = cmf[i] < 0
            val sCnt = listOf(s1, s2, s3).count { it }

            sellSignal.add(if (sCnt == 3) 1 else 0)
            auxSellSignal.add(if (sCnt == 2 && s2) 1 else 0)
        }

        return SignalResult(dates, ma, cmf, fg, buySignal, auxBuySignal, sellSignal, auxSellSignal)
    }

    // ============================================================
    // Elder Impulse System
    // ============================================================

    data class ElderImpulseResult(
        val ema: List<Double>,
        val macd: List<Double>,
        val macdSignal: List<Double>,
        val macdHist: List<Double>,
        val impulse: List<Int>  // 1=bull, 0=neutral, -1=bear
    )

    /**
     * Calculate Elder Impulse System
     *
     * Uses EMA13 slope + MACD histogram slope:
     * - bull (1): both slopes positive
     * - bear (-1): both slopes negative
     * - neutral (0): mixed slopes
     */
    fun calculateElderImpulse(
        close: List<Double>,
        emaPeriod: Int = 13
    ): ElderImpulseResult {
        if (close.isEmpty()) {
            return ElderImpulseResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        // EMA13
        val ema = calculateEMA(close, emaPeriod)

        // MACD (12-26-9)
        val ema12 = calculateEMA(close, 12)
        val ema26 = calculateEMA(close, 26)
        val macd = ema12.zip(ema26) { e12, e26 -> e12 - e26 }
        val macdSignal = calculateEMA(macd, 9)
        val macdHist = macd.zip(macdSignal) { m, s -> m - s }

        // Slopes
        val emaSlope = ema.mapIndexed { i, e ->
            if (i == 0) 0.0 else e - ema[i - 1]
        }
        val histSlope = macdHist.mapIndexed { i, h ->
            if (i == 0) 0.0 else h - macdHist[i - 1]
        }

        // Impulse: 1=bull, 0=neutral, -1=bear
        val impulse = emaSlope.zip(histSlope) { es, hs ->
            when {
                es > 0 && hs > 0 -> 1
                es < 0 && hs < 0 -> -1
                else -> 0
            }
        }

        return ElderImpulseResult(ema, macd, macdSignal, macdHist, impulse)
    }

    // ============================================================
    // DeMark TD Setup
    // ============================================================

    data class DemarkTDResult(
        val tdSell: List<Int>,  // Sell setup count (uptrend exhaustion)
        val tdBuy: List<Int>    // Buy setup count (downtrend exhaustion)
    )

    /**
     * Calculate DeMark TD Setup
     *
     * Counts consecutive closes vs. close 4 periods ago:
     * - Sell setup: Close[t] > Close[t-4] consecutive count
     * - Buy setup: Close[t] < Close[t-4] consecutive count
     */
    fun calculateDemarkTD(close: List<Double>): DemarkTDResult {
        if (close.size < 5) {
            return DemarkTDResult(List(close.size) { 0 }, List(close.size) { 0 })
        }

        val tdSell = mutableListOf<Int>()
        val tdBuy = mutableListOf<Int>()

        // First 4 periods: 0
        repeat(4) {
            tdSell.add(0)
            tdBuy.add(0)
        }

        // Calculate from index 4 onwards
        for (i in 4 until close.size) {
            val prevSell = tdSell[i - 1]
            val prevBuy = tdBuy[i - 1]

            if (close[i] > close[i - 4]) {
                tdSell.add(prevSell + 1)
                tdBuy.add(0)
            } else if (close[i] < close[i - 4]) {
                tdSell.add(0)
                tdBuy.add(prevBuy + 1)
            } else {
                tdSell.add(0)
                tdBuy.add(0)
            }
        }

        return DemarkTDResult(tdSell, tdBuy)
    }

    // ============================================================
    // Rolling Sum (for investor trading data)
    // ============================================================

    /**
     * Calculate 5-day rolling sum
     *
     * Used for foreign/institution 5-day cumulative net buy volumes
     */
    fun rollingSum(values: List<Long>, period: Int = 5): List<Long> {
        if (values.size < period) return List(values.size) { 0L }

        val result = mutableListOf<Long>()

        for (i in values.indices) {
            if (i < period - 1) {
                result.add(0L)
            } else {
                val sum = values.subList(i - period + 1, i + 1).sum()
                result.add(sum)
            }
        }

        return result
    }
}
