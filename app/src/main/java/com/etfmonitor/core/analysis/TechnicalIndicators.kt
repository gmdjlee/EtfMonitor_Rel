package com.etfmonitor.core.analysis

import kotlin.math.sqrt

/**
 * 공유 기술적 지표 계산 유틸리티
 *
 * EMA, SMA, MACD, RSI, CMF 등 기술적 분석에 필요한
 * 수학적 계산을 제공하는 순수 유틸리티 객체
 */
object TechnicalIndicators {

    /**
     * EMA (지수 이동 평균) - pandas ewm(adjust=False) 호환
     *
     * alpha = 2 / (period + 1)
     * EMA[0] = values[0]
     * EMA[t] = alpha * values[t] + (1 - alpha) * EMA[t-1]
     */
    fun calcEma(values: List<Double>, period: Int): List<Double> {
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

    /**
     * SMA (단순 이동 평균)
     *
     * 첫 period-1 개 구간은 가용 데이터로 평균 계산
     */
    fun calcSma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - period + 1)
            values.subList(start, index + 1).average()
        }
    }

    /**
     * Rolling Mean (일정 기간 이동 평균, 기간 미달 시 NaN)
     */
    fun rollingMean(values: List<Double>, period: Int): List<Double> {
        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - period + 1)
            val window = values.subList(start, index + 1)
            if (window.size >= period) window.average() else Double.NaN
        }
    }

    /**
     * MACD (Moving Average Convergence Divergence)
     *
     * @return Triple(macdLine, signalLine, histogram)
     */
    fun calcMacd(
        values: List<Double>,
        shortPeriod: Int = 12,
        longPeriod: Int = 26,
        signalPeriod: Int = 9
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val emaShort = calcEma(values, shortPeriod)
        val emaLong = calcEma(values, longPeriod)
        val macdLine = emaShort.zip(emaLong) { s, l -> s - l }
        val signalLine = calcEma(macdLine, signalPeriod)
        val histogram = macdLine.zip(signalLine) { m, s -> m - s }
        return Triple(macdLine, signalLine, histogram)
    }

    /**
     * RSI (Relative Strength Index)
     *
     * EMA 기반 RSI 계산 (Wilder 방식 근사)
     * 첫 번째 값은 변화가 없으므로 50.0
     */
    fun calcRsi(values: List<Double>, period: Int = 14): List<Double> {
        if (values.size < 2) return values.map { 50.0 }

        val changes = values.zipWithNext { a, b -> b - a }
        val gains = changes.map { maxOf(it, 0.0) }
        val losses = changes.map { maxOf(-it, 0.0) }

        val avgGains = calcEma(gains, period)
        val avgLosses = calcEma(losses, period)

        val rsi = mutableListOf(50.0) // 첫 값
        for (i in avgGains.indices) {
            val rs = if (avgLosses[i] > 0.0001) avgGains[i] / avgLosses[i] else 100.0
            rsi.add(100.0 - 100.0 / (1.0 + rs))
        }
        return rsi
    }

    /**
     * CMF (Chaikin Money Flow)
     *
     * MFM = ((Close - Low) - (High - Close)) / (High - Low)
     * MFV = MFM * Volume
     * CMF = Sum(MFV, period) / Sum(Volume, period)
     */
    fun calcCmf(
        high: List<Double>,
        low: List<Double>,
        close: List<Double>,
        volume: List<Long>,
        period: Int = 4
    ): List<Double> {
        val n = close.size
        val mfv = DoubleArray(n)

        for (i in 0 until n) {
            val hl = high[i] - low[i]
            val mfm = if (hl > 0) ((close[i] - low[i]) - (high[i] - close[i])) / hl else 0.0
            mfv[i] = mfm * volume[i]
        }

        return (0 until n).map { idx ->
            val start = maxOf(0, idx - period + 1)
            val mfvSum = mfv.slice(start..idx).sum()
            val volSum = volume.subList(start, idx + 1).sumOf { it }.toDouble()
            if (volSum > 0) mfvSum / volSum else 0.0
        }
    }

    /**
     * 주식 Fear & Greed Index (개별 종목용, -1 ~ +1)
     *
     * 모멘텀(45%) + 52주 포지션(45%) + 거래량 급등(5%) + 변동성(5%)
     * trend_signal.py _calc_fg() 포팅
     */
    fun calcStockFearGreed(
        close: List<Double>,
        volume: List<Long>,
        momPeriod: Int = 5,
        posPeriod: Int = 52
    ): List<Double> {
        val n = close.size
        val result = DoubleArray(n) { 0.0 }

        for (i in 0 until n) {
            // 1. Momentum (45%) - log return
            val mom = if (i >= momPeriod && close[i - momPeriod] > 0) {
                val logRet = kotlin.math.ln(close[i] / close[i - momPeriod])
                (logRet / 0.1).coerceIn(-1.0, 1.0)
            } else 0.0

            // 2. Position in range (45%) - 52주 고저 위치
            val rangeStart = maxOf(0, i - posPeriod + 1)
            val window = close.subList(rangeStart, i + 1)
            val pos = if (window.size >= 10) {
                val hi = window.max()
                val lo = window.min()
                val range = hi - lo
                if (range > 0) ((close[i] - lo) / range * 2) - 1 else 0.0
            } else 0.0

            // 3. Volume spike (5%)
            val volStart = maxOf(0, i - 19)
            val volWindow = volume.subList(volStart, i + 1)
            val volScore = if (volWindow.size >= 5) {
                val volMa = volWindow.average()
                if (volMa > 0) ((volume[i] / volMa) - 1).coerceIn(-1.0, 1.0) else 0.0
            } else 0.0

            // 4. Volatility (5%, inverted)
            val volSpike = if (i >= 5) {
                val recentReturns = (maxOf(1, i - 4)..i).map { j ->
                    if (close[j - 1] > 0) (close[j] - close[j - 1]) / close[j - 1] else 0.0
                }
                val avgReturns = (maxOf(1, i - 19)..i).map { j ->
                    if (close[j - 1] > 0) (close[j] - close[j - 1]) / close[j - 1] else 0.0
                }
                val recentStd = stdDev(recentReturns)
                val avgStd = stdDev(avgReturns)
                if (avgStd > 0) ((recentStd / avgStd - 1).coerceIn(-1.0, 1.0)) * -1 else 0.0
            } else 0.0

            result[i] = mom * 0.45 + pos * 0.45 + volScore * 0.05 + volSpike * 0.05
        }

        return result.toList()
    }

    /**
     * MinMax Scaling (0 ~ 1)
     */
    fun minMaxScale(values: List<Double>): List<Double> {
        val valid = values.filter { !it.isNaN() && !it.isInfinite() }
        if (valid.isEmpty()) return values.map { 0.5 }
        val min = valid.min()
        val max = valid.max()
        val range = max - min
        return values.map {
            if (range > 0 && !it.isNaN() && !it.isInfinite()) (it - min) / range
            else 0.5
        }
    }

    /**
     * Rolling Standard Deviation
     */
    fun rollingStd(values: List<Double>, period: Int, minPeriods: Int = 1): List<Double> {
        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - period + 1)
            val window = values.subList(start, index + 1)
            if (window.size >= minPeriods) stdDev(window) else Double.NaN
        }
    }

    /**
     * 표준편차 계산
     */
    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}
