package com.etfmonitor.core.analysis

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

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
 * - Multi-period MA calculation (MA5, MA10, MA20, MA60)
 * - MA Signal (daily 3-MA alignment, weekly reference 3-condition)
 * - Trend determination (2-of-3 voting)
 *
 * ## Design
 * - Pure object (no DI, no coroutines)
 * - Maximum testability and reusability
 * - No external dependencies
 * - Data ordering: oldest-first throughout (index 0 = oldest bar)
 */
object TechnicalAnalysisEngine {

    // ============================================================
    // Fear/Greed calculation constants (matching TrendCalculator reference)
    // ============================================================

    private const val FG_MOMENTUM_SMOOTHING_PERIOD = 7
    private const val FG_MOMENTUM_DIVISOR = 10
    private const val FG_VOLUME_SMOOTHING_PERIOD = 10
    private const val FG_POSITION_SMOOTHING_PERIOD = 7
    private const val FG_MOMENTUM_LOOKBACK = 5
    private const val FG_POSITION_LOOKBACK = 52
    private const val FG_VOLUME_LOOKBACK = 20
    private const val FG_MIN_CALC_PERIOD = 10

    // Fear/Greed component weights
    private const val FG_WEIGHT_MOMENTUM = 0.45
    private const val FG_WEIGHT_POSITION = 0.45
    private const val FG_WEIGHT_VOLUME_SURGE = 0.05
    private const val FG_WEIGHT_VOLUME_SPIKE = 0.05

    // Fear/Greed clipping bounds
    private const val FG_MOMENTUM_MIN = -1.0
    private const val FG_MOMENTUM_MAX = 1.5
    private const val FG_POSITION_MIN = -1.0
    private const val FG_POSITION_MAX = 1.5
    private const val FG_VOLUME_MIN = -0.5
    private const val FG_VOLUME_MAX = 1.2

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
     *
     * Data ordering: oldest-first
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
     *
     * Data ordering: oldest-first
     *
     * Default period changed from 4 to 20 (matches reference TrendCalculator daily default).
     * Weekly callers should pass period = 4 explicitly.
     */
    fun calculateCMF(
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>,
        period: Int = 20
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
        var runningSumMfv = 0.0
        var runningSumVol = 0L

        for (i in high.indices) {
            runningSumMfv += mfv[i]
            runningSumVol += volume[i]
            if (i < period - 1) {
                cmf.add(0.0)
            } else {
                if (i >= period) {
                    runningSumMfv -= mfv[i - period]
                    runningSumVol -= volume[i - period]
                }
                cmf.add(if (runningSumVol > 0) runningSumMfv / runningSumVol.toDouble() else 0.0)
            }
        }

        return cmf
    }

    // ============================================================
    // Fear & Greed Index
    // ============================================================

    /**
     * Calculate Fear & Greed Index
     *
     * Rewritten to match reference TrendCalculator.calcFearGreed exactly.
     *
     * Components and weights:
     * - Momentum (45%): log return over 5 periods * 100, rolling(7).mean() / 10, clip [-1, 1.5]
     * - Position (45%): 52-period range [0,1], rolling(7).mean(), 2*avg-1, clip [-1, 1.5]
     * - VolSurge (5%): 5d-avg/20d-avg, clip [0,3], rolling(10).mean()-1, clip [-0.5, 1.2]
     * - VolSpike (5%): 5d-std/20d-std, clip [0,3], -(rolling(10).mean()-1), clip [-0.5, 1.2]
     *
     * Data ordering: oldest-first (index 0 = oldest bar).
     * No reversal needed — data is already in chronological order.
     *
     * @param close Close prices (oldest-first)
     * @param volume Volumes (oldest-first)
     */
    fun calculateFearGreed(
        close: List<Double>,
        volume: List<Long>
    ): List<Double> {
        val n = close.size
        if (n < FG_POSITION_LOOKBACK) {
            return List(n) { 0.0 }
        }

        // All arrays are in chronological order (oldest index 0)
        val momentum5 = DoubleArray(n) { 0.0 }
        val pos52 = DoubleArray(n) { 0.0 }
        val volSurge = DoubleArray(n) { 1.0 }
        val volSpike = DoubleArray(n) { 1.0 }
        val returns = DoubleArray(n) { 0.0 }

        for (i in 0 until n) {
            // Momentum5: log return over 5 periods * 100
            if (i >= FG_MOMENTUM_LOOKBACK && close[i] > 0 && close[i - FG_MOMENTUM_LOOKBACK] > 0) {
                momentum5[i] = (ln(close[i]) - ln(close[i - FG_MOMENTUM_LOOKBACK])) * 100
            }

            // Pos52: position within 52-period range [0, 1]
            if (i >= FG_POSITION_LOOKBACK - 1) {
                val window = close.subList(max(0, i - FG_POSITION_LOOKBACK + 1), i + 1)
                val low52 = window.minOrNull() ?: 0.0
                val high52 = window.maxOrNull() ?: 0.0
                pos52[i] = if (high52 > low52) {
                    (close[i] - low52) / (high52 - low52)
                } else {
                    0.5
                }
            } else {
                val window = close.subList(0, i + 1)
                if (window.isNotEmpty()) {
                    val lowVal = window.minOrNull() ?: 0.0
                    val highVal = window.maxOrNull() ?: 0.0
                    pos52[i] = if (highVal > lowVal) {
                        (close[i] - lowVal) / (highVal - lowVal)
                    } else {
                        0.5
                    }
                }
            }

            // Daily returns for volatility
            if (i >= 1 && close[i - 1] > 0) {
                returns[i] = (close[i] - close[i - 1]) / close[i - 1]
            }
        }

        // VolSurge: recent 5-day avg volume / past 20-day avg volume, clip [0, 3]
        for (i in 0 until n) {
            if (i >= FG_VOLUME_LOOKBACK) {
                val recentVol = volume.subList(i - 4, i + 1).average()
                val pastVol = volume.subList(i - 19, i + 1).average()
                if (pastVol > 0) {
                    volSurge[i] = max(0.0, min(3.0, recentVol / pastVol))
                }
            } else if (i >= 5) {
                volSurge[i] = 1.0
            }
        }

        // VolSpike: recent 5-day std / past 20-day std, clip [0, 3]
        for (i in 0 until n) {
            if (i >= FG_VOLUME_LOOKBACK) {
                val recentReturns = returns.slice(i - 4..i)
                val pastReturns = returns.slice(i - 19..i)
                val recentStd = populationStd(recentReturns)
                val pastStd = populationStd(pastReturns)
                if (pastStd > 0) {
                    volSpike[i] = max(0.0, min(3.0, recentStd / pastStd))
                }
            } else if (i >= 5) {
                volSpike[i] = 1.0
            }
        }

        // Compute FG with smoothing
        val fg = DoubleArray(n) { 0.0 }
        val momentumWindowOffset = FG_MOMENTUM_SMOOTHING_PERIOD - 1
        val volumeWindowOffset = FG_VOLUME_SMOOTHING_PERIOD - 1

        for (i in 0 until n) {
            if (i < FG_MIN_CALC_PERIOD) {
                fg[i] = 0.0
                continue
            }

            // m = (Momentum5.rolling(7).mean() / 10).clip(-1, 1.5)
            val mWindowStart = max(0, i - momentumWindowOffset)
            val mWindow = momentum5.slice(mWindowStart..i)
            val m = (mWindow.average() / FG_MOMENTUM_DIVISOR).coerceIn(FG_MOMENTUM_MIN, FG_MOMENTUM_MAX)

            // p = (2 * Pos52.rolling(7).mean() - 1).clip(-1, 1.5)
            val pWindow = pos52.slice(mWindowStart..i)
            val p = (2 * pWindow.average() - 1).coerceIn(FG_POSITION_MIN, FG_POSITION_MAX)

            // v = (VolSurge.rolling(10).mean() - 1).clip(-0.5, 1.2)
            val vWindowStart = max(0, i - volumeWindowOffset)
            val vWindow = volSurge.slice(vWindowStart..i)
            val v = (vWindow.average() - 1).coerceIn(FG_VOLUME_MIN, FG_VOLUME_MAX)

            // vs = -(VolSpike.rolling(10).mean() - 1), clip [-0.5, 1.2]
            val vsWindow = volSpike.slice(vWindowStart..i)
            val vs = (-(vsWindow.average() - 1)).coerceIn(FG_VOLUME_MIN, FG_VOLUME_MAX)

            fg[i] = FG_WEIGHT_MOMENTUM * m +
                FG_WEIGHT_POSITION * p +
                FG_WEIGHT_VOLUME_SURGE * v +
                FG_WEIGHT_VOLUME_SPIKE * vs
        }

        return fg.toList()
    }

    /**
     * Population standard deviation (matches reference MathUtil.std / Python ddof=0).
     */
    private fun populationStd(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    // ============================================================
    // Multi-Period Moving Average (MA5 / MA10 / MA20 / MA60)
    // ============================================================

    /**
     * Calculate Simple Moving Average for integer prices.
     *
     * Reference: TrendCalculator.calcMa (lines 139-149)
     *
     * Data ordering: oldest-first. Returns null for positions where
     * there is insufficient history (i.e., fewer than [period] bars
     * have been seen so far).
     *
     * @param prices Close prices (oldest-first)
     * @param period MA period
     * @return MA values; null where history < period
     */
    fun calculateMa(prices: List<Int>, period: Int): List<Int?> {
        if (prices.isEmpty() || period <= 0) return emptyList()

        return prices.indices.map { i ->
            if (i < period - 1) {
                null
            } else {
                prices.subList(i - period + 1, i + 1).sum() / period
            }
        }
    }

    /**
     * Calculate MA-based signal for daily data (3-MA alignment).
     *
     * Reference: TrendCalculator.calcMaSignal (lines 160-177)
     *
     * Signal logic (oldest-first data):
     * - 1 (bullish):  MA5 > MA20 > MA60
     * - -1 (bearish): MA5 < MA20 < MA60
     * - 0 (neutral):  otherwise or any MA is null
     *
     * @param ma5  MA5 list (oldest-first, nullable)
     * @param ma20 MA20 list (oldest-first, nullable)
     * @param ma60 MA60 list (oldest-first, nullable)
     */
    fun calcMaSignal(
        ma5: List<Int?>,
        ma20: List<Int?>,
        ma60: List<Int?>
    ): List<Int> {
        return ma5.indices.map { i ->
            val m5 = ma5.getOrNull(i)
            val m20 = ma20.getOrNull(i)
            val m60 = ma60.getOrNull(i)

            when {
                m5 == null || m20 == null || m60 == null -> 0
                m5 > m20 && m20 > m60 -> 1
                m5 < m20 && m20 < m60 -> -1
                else -> 0
            }
        }
    }

    /**
     * Calculate MA-based signal for weekly data (reference 3-condition logic).
     *
     * Reference: TrendCalculator.calcMaSignalWeeklyReference (lines 190-224)
     *
     * Signal logic (oldest-first data, so prev = index - 1):
     * - 1 (buy):   High > Prev_High AND Close > MA10 AND CMF > 0
     * - -1 (sell): Low < Prev_Low  AND Close < MA10 AND CMF < 0
     * - 0 (neutral): otherwise
     *
     * @param closes Close prices (oldest-first)
     * @param highs  High prices (oldest-first)
     * @param lows   Low prices (oldest-first)
     * @param ma10   MA10 values (oldest-first, nullable)
     * @param cmf    CMF values (oldest-first)
     */
    fun calcMaSignalWeeklyReference(
        closes: List<Int>,
        highs: List<Int>,
        lows: List<Int>,
        ma10: List<Int?>,
        cmf: List<Double>
    ): List<Int> {
        val n = closes.size
        val result = MutableList(n) { 0 }

        for (i in 0 until n) {
            // Need previous bar data (prev = i-1 in oldest-first ordering)
            if (i == 0 || ma10.getOrNull(i) == null) {
                result[i] = 0
                continue
            }

            val prevHigh = highs[i - 1]
            val prevLow = lows[i - 1]
            val currentMa10 = ma10[i] ?: continue

            // Buy signal: High > Prev_High AND Close > MA10 AND CMF > 0
            if (highs[i] > prevHigh && closes[i] > currentMa10 && cmf[i] > 0) {
                result[i] = 1
            }
            // Sell signal: Low < Prev_Low AND Close < MA10 AND CMF < 0
            else if (lows[i] < prevLow && closes[i] < currentMa10 && cmf[i] < 0) {
                result[i] = -1
            } else {
                result[i] = 0
            }
        }

        return result
    }

    /**
     * Determine overall trend using 2-of-3 voting.
     *
     * Reference: TrendCalculator.calcTrend (lines 385-422)
     *
     * Logic:
     * - "bullish": >= 2 bullish indicators (MA signal=1, CMF>0.05, FG>0.5)
     * - "bearish": >= 2 bearish indicators (MA signal=-1, CMF<-0.05, FG<-0.5)
     * - "neutral": mixed signals
     *
     * @param maSignal MA signal list (1/0/-1)
     * @param cmf      CMF list
     * @param fearGreed Fear/Greed list
     */
    fun calcTrend(
        maSignal: List<Int>,
        cmf: List<Double>,
        fearGreed: List<Double>
    ): List<String> {
        return maSignal.indices.map { i ->
            var bullCount = 0
            var bearCount = 0

            when (maSignal.getOrNull(i)) {
                1 -> bullCount++
                -1 -> bearCount++
            }

            val cmfValue = cmf.getOrNull(i) ?: 0.0
            when {
                cmfValue > 0.05 -> bullCount++
                cmfValue < -0.05 -> bearCount++
            }

            val fgValue = fearGreed.getOrNull(i) ?: 0.0
            when {
                fgValue > 0.5 -> bullCount++
                fgValue < -0.5 -> bearCount++
            }

            when {
                bullCount >= 2 -> "bullish"
                bearCount >= 2 -> "bearish"
                else -> "neutral"
            }
        }
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
        val auxSellSignal: List<Int>,
        // New fields added by TrendCalculator migration
        val ma5: List<Int?> = emptyList(),
        val ma10: List<Int?> = emptyList(),
        val ma20: List<Int?> = emptyList(),
        val ma60: List<Int?> = emptyList(),
        val maSignal: List<Int> = emptyList(),
        val trend: List<String> = emptyList()
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
     *
     * Also calculates:
     * - MA5, MA10, MA20, MA60
     * - maSignal: daily 3-MA alignment signal
     * - trend: 2-of-3 voting ("bullish"/"neutral"/"bearish")
     *
     * Data ordering: oldest-first
     *
     * @param cmfPeriod CMF period. Default 20 (daily). Pass 4 for weekly.
     */
    fun generateSignals(
        dates: List<String>,
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>,
        maPeriod: Int = 20,
        cmfPeriod: Int = 20
    ): SignalResult {
        if (dates.isEmpty()) {
            return SignalResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        // Calculate MA for charting (floating-point, warm-up = 0.0)
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

        // Multi-period MA (integer prices approximated from Double close)
        val closeAsInt = close.map { it.toInt() }
        val ma5List = calculateMa(closeAsInt, 5)
        val ma10List = calculateMa(closeAsInt, 10)
        val ma20List = calculateMa(closeAsInt, 20)
        val ma60List = calculateMa(closeAsInt, 60)

        // Daily MA signal (3-MA alignment: MA5 vs MA20 vs MA60)
        val maSignalList = calcMaSignal(ma5List, ma20List, ma60List)

        // Combined trend via 2-of-3 voting
        val trendList = calcTrend(maSignalList, cmf, fg)

        // Generate buy/sell signals
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

        return SignalResult(
            dates = dates,
            ma = ma,
            cmf = cmf,
            fearGreed = fg,
            buySignal = buySignal,
            auxBuySignal = auxBuySignal,
            sellSignal = sellSignal,
            auxSellSignal = auxSellSignal,
            ma5 = ma5List,
            ma10 = ma10List,
            ma20 = ma20List,
            ma60 = ma60List,
            maSignal = maSignalList,
            trend = trendList
        )
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
     * Data ordering: newest-first (index 0 = newest bar).
     * Slope uses forward difference: values[i] - values[i+1] (newer - older).
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

        // Slopes (forward difference: newest bar gets real slope, oldest bar gets 0.0)
        val emaSlope = ema.mapIndexed { i, e ->
            if (i + 1 >= ema.size) 0.0 else e - ema[i + 1]
        }
        val histSlope = macdHist.mapIndexed { i, h ->
            if (i + 1 >= macdHist.size) 0.0 else h - macdHist[i + 1]
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
        var runningSum = 0L

        for (i in values.indices) {
            runningSum += values[i]
            if (i < period - 1) {
                result.add(0L)
            } else {
                if (i >= period) {
                    runningSum -= values[i - period]
                }
                result.add(runningSum)
            }
        }

        return result
    }
}
