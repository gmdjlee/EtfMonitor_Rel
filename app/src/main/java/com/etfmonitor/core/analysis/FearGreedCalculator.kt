package com.etfmonitor.core.analysis

/**
 * Fear & Greed Calculator - Pure Kotlin Computations
 *
 * Ports the Python fear & greed analysis functions from feargreed.py:
 * - `_calc_rsi(series, window=10)`
 * - `_calc_macd(series, short=12, long=26, sig=9)`
 * - `_calc_fg(df, idx_col)` including MinMax normalisation
 * - Rolling 5-day MA for option volumes
 *
 * ## Design
 * - Pure object (no DI, no coroutines)
 * - `Double.NaN` used where pandas produces NaN (insufficient data, division by zero)
 * - Maximum testability and reusability
 * - No external dependencies
 *
 * ## Python compatibility notes
 * - EMA uses alpha = 2/(period+1), EMA[0] = series[0] — matches pandas ewm(adjust=False)
 * - RSI gain/loss uses simple rolling mean, not EMA (matches pandas .rolling().mean())
 * - MinMax normalisation is applied only to rows where all 5 features are non-NaN
 * - Rows with any NaN feature keep NaN after normalisation (caller should filter these out)
 */
object FearGreedCalculator {

    // ============================================================
    // Input / Output data models
    // ============================================================

    /**
     * One day of merged input data required to calculate Fear & Greed.
     *
     * @param date        Trading date in YYYY-MM-DD format
     * @param indexValue  KOSPI or KOSDAQ closing value
     * @param call        Call option volume 5-day rolling MA
     * @param put         Put option volume 5-day rolling MA
     * @param vix         VKOSPI closing value
     * @param bond5y      5-year KTB yield
     * @param bond10y     10-year KTB yield
     */
    data class FearGreedDayData(
        val date: String,
        val indexValue: Double,
        val call: Double,
        val put: Double,
        val vix: Double,
        val bond5y: Double,
        val bond10y: Double
    )

    /**
     * Per-day Fear & Greed analysis result.
     *
     * All component values are already MinMax-normalised to [0, 1].
     * The oscillator is the MACD histogram of the FG series.
     *
     * @param date          Trading date in YYYY-MM-DD format
     * @param indexValue    KOSPI or KOSDAQ closing value
     * @param fearGreedValue  Composite FG score (0–1 range after normalisation)
     * @param oscillator    MACD oscillator of the FG series (can be negative)
     * @param rsi           RSI component (normalised to [0, 1])
     * @param momentum      Momentum component (normalised to [0, 1])
     * @param putCallRatio  PCR component (normalised to [0, 1])
     * @param volatility    Volatility (VIX) component (normalised to [0, 1])
     * @param spread        Yield-spread component (normalised to [0, 1])
     */
    data class FearGreedResult(
        val date: String,
        val indexValue: Double,
        val fearGreedValue: Double,
        val oscillator: Double,
        val rsi: Double,
        val momentum: Double,
        val putCallRatio: Double,
        val volatility: Double,
        val spread: Double
    )

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Calculate 5-day rolling mean for option volumes.
     *
     * Equivalent to pandas `Series.rolling(5).mean()`:
     * - Indices 0–3 produce `Double.NaN` (insufficient window).
     * - Index i >= 4 produces the simple mean of [i-4 .. i] inclusive.
     *
     * @param values Raw daily option volume counts (chronological order)
     * @return List of the same length; first 4 elements are `Double.NaN`
     */
    fun rollingMean5(values: List<Long>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val period = 5
        val result = mutableListOf<Double>()
        var runningSum = 0L
        for (i in values.indices) {
            runningSum += values[i]
            if (i < period - 1) {
                result.add(Double.NaN)
            } else {
                if (i >= period) {
                    runningSum -= values[i - period]
                }
                result.add(runningSum.toDouble() / period)
            }
        }
        return result
    }

    /**
     * Calculate the RSI (Relative Strength Index).
     *
     * Ports `_calc_rsi(series, window=10)` from feargreed.py.
     *
     * Algorithm (matches pandas rolling mean, NOT Wilder EMA):
     * 1. `delta[i] = series[i] - series[i-1]`; `delta[0] = NaN`
     * 2. `gain[i]  = delta[i]` if `delta[i] > 0`, else `0.0`
     * 3. `loss[i]  = |delta[i]|` if `delta[i] < 0`, else `0.0`
     * 4. `avgGain` = simple rolling mean of gain over `window`
     * 5. `avgLoss` = simple rolling mean of loss over `window`
     * 6. RS = avgGain / avgLoss; avgLoss == 0 → RS = NaN
     * 7. RSI = 100 - 100 / (1 + RS)
     *
     * The first `window` elements (indices 0 to window-1) are `Double.NaN`
     * because the rolling window is not yet full (matches pandas min_periods default).
     *
     * @param series  Chronological price series
     * @param window  RSI look-back window (default 10, matches Python default)
     * @return RSI series of the same length; first `window` values are `Double.NaN`
     */
    fun calcRsi(series: List<Double>, window: Int = 10): List<Double> {
        if (series.size < 2) return List(series.size) { Double.NaN }

        val n = series.size

        // delta[0] = NaN, delta[i] = series[i] - series[i-1]
        val gain = DoubleArray(n) { Double.NaN }
        val loss = DoubleArray(n) { Double.NaN }
        for (i in 1 until n) {
            val d = series[i] - series[i - 1]
            gain[i] = if (d > 0.0) d else 0.0
            loss[i] = if (d < 0.0) -d else 0.0   // abs value
        }

        // Rolling mean of gain and loss over `window`, min_periods = window
        val result = mutableListOf<Double>()
        for (i in 0 until n) {
            // Window: [i - window + 1 .. i] — need all to be non-NaN (i.e. index >= 1)
            // So first valid index = window (index 0 of delta is NaN → first full window ends at i=window)
            if (i < window) {
                result.add(Double.NaN)
                continue
            }
            // Slice [i-window+1 .. i] — all indices >= 1, so no NaN in gain/loss arrays
            val startIdx = i - window + 1
            var sumGain = 0.0
            var sumLoss = 0.0
            for (j in startIdx..i) {
                sumGain += gain[j]
                sumLoss += loss[j]
            }
            val avgGain = sumGain / window
            val avgLoss = sumLoss / window

            val rsi = if (avgLoss == 0.0) {
                // loss denominator is 0 → RS = NaN → RSI = NaN (matches pandas replace(0, NaN))
                Double.NaN
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
            result.add(rsi)
        }

        return result
    }

    /**
     * Calculate MACD oscillator (histogram).
     *
     * Ports `_calc_macd(series, short=12, long=26, sig=9)` from feargreed.py.
     *
     * Formula:
     * - `emaShort = EMA(series, short)`
     * - `emaLong  = EMA(series, long)`
     * - `macd     = emaShort - emaLong`
     * - `signal   = EMA(macd, sig)`
     * - returns `macd - signal` (the histogram / oscillator)
     *
     * EMA is pandas ewm(span=n, adjust=False): alpha = 2/(n+1), EMA[0] = series[0].
     *
     * @param series  Input price (or FG) series — must not contain NaN values
     * @param short   Fast EMA period (default 12)
     * @param long    Slow EMA period (default 26)
     * @param sig     Signal EMA period (default 9)
     * @return MACD oscillator series of the same length
     */
    fun calcMacd(
        series: List<Double>,
        short: Int = 12,
        long: Int = 26,
        sig: Int = 9
    ): List<Double> {
        if (series.isEmpty()) return emptyList()

        val emaShort = calculateEma(series, short)
        val emaLong = calculateEma(series, long)
        val macd = emaShort.zip(emaLong) { s, l -> s - l }
        val signal = calculateEma(macd, sig)
        return macd.zip(signal) { m, s -> m - s }
    }

    /**
     * Calculate the Fear & Greed index for a merged daily dataset.
     *
     * Ports `_calc_fg(df, idx_col)` from feargreed.py.
     *
     * Pipeline:
     * 1. Adaptive MA period: `min(125, max(10, floor(n * 0.9)))`
     * 2. Momentum  = `(index - MA) / MA * 100`; MA == 0 → NaN
     * 3. PCR       = `Put / Call`; Call == 0 or Call NaN → NaN
     * 4. Vol       = VIX (direct copy)
     * 5. Spread    = bond10y - bond5y
     * 6. RSI       = calcRsi(index values, window=10)
     * 7. Identify `valid` rows: all 5 features (Mom, PCR, Vol, Spread, RSI) are finite
     * 8. MinMax-normalise each feature to [0, 1] **using only valid rows** as the range
     *    (rows with any NaN feature are left as NaN after normalisation)
     * 9. FG  = Mom*0.2 + (1-PCR)*0.2 + (1-Vol)*0.2 + Spread*0.2 + RSI*0.2
     * 10. Osc = calcMacd(FG series, dropping NaN rows for MACD input, then aligned back)
     *
     * Rows where any required field is NaN produce a result with NaN oscillator/components.
     * The caller (FearGreedRepositoryImpl) should filter these with `isFinite()` checks.
     *
     * @param data  Chronologically sorted list of merged daily data (ascending by date)
     * @return      List of the same length as input; invalid rows carry NaN in all Double fields
     */
    fun calcFearGreed(data: List<FearGreedDayData>): List<FearGreedResult> {
        val n = data.size
        if (n == 0) return emptyList()

        // Step 1: Adaptive MA period
        val maPeriod = minOf(125, maxOf(10, (n * 0.9).toInt()))

        // Step 2: Rolling simple MA of index values
        val indexValues = data.map { it.indexValue }
        val ma = rollingSimpleMean(indexValues, maPeriod)

        // Step 3: Compute raw feature arrays (NaN where not computable)
        val rsiSeries = calcRsi(indexValues, window = 10)

        val mom = DoubleArray(n)
        val pcr = DoubleArray(n)
        val vol = DoubleArray(n)
        val spread = DoubleArray(n)
        val rsi = DoubleArray(n)

        for (i in 0 until n) {
            val maVal = ma[i]
            mom[i] = if (maVal.isFinite() && maVal != 0.0) {
                (indexValues[i] - maVal) / maVal * 100.0
            } else Double.NaN

            val callVal = data[i].call
            val putVal = data[i].put
            pcr[i] = if (callVal.isFinite() && callVal != 0.0 && putVal.isFinite()) {
                putVal / callVal
            } else Double.NaN

            vol[i] = data[i].vix    // direct copy; NaN if vix was NaN in input

            spread[i] = data[i].bond10y - data[i].bond5y  // both finite guaranteed by caller

            rsi[i] = rsiSeries[i]   // Double.NaN for first `window` indices
        }

        // Step 4: Identify valid rows — all 5 features must be finite
        val valid = BooleanArray(n) { i ->
            mom[i].isFinite() && pcr[i].isFinite() && vol[i].isFinite() &&
                    spread[i].isFinite() && rsi[i].isFinite()
        }

        // Step 5: MinMax normalise each feature using the range of valid rows only
        // Working copies so we don't mutate original arrays; NaN rows remain NaN
        val normMom = minMaxNormalize(mom, valid)
        val normPcr = minMaxNormalize(pcr, valid)
        val normVol = minMaxNormalize(vol, valid)
        val normSpread = minMaxNormalize(spread, valid)
        val normRsi = minMaxNormalize(rsi, valid)

        // Step 6: Compute FG score for each row
        // FG = Mom*0.2 + (1-PCR)*0.2 + (1-Vol)*0.2 + Spread*0.2 + RSI*0.2
        val fg = DoubleArray(n) { i ->
            if (!valid[i]) Double.NaN
            else normMom[i] * 0.2 + (1.0 - normPcr[i]) * 0.2 +
                    (1.0 - normVol[i]) * 0.2 + normSpread[i] * 0.2 + normRsi[i] * 0.2
        }

        // Step 7: Compute MACD oscillator on the FG series.
        // In Python: _calc_macd(df["FG"]) is called on the full column including NaN rows.
        // pandas ewm(adjust=False) propagates NaN — effectively the EMA is only computed
        // on non-NaN values but NaN rows break the chain.
        // Closest equivalent: pass the full fg array; NaN inputs will produce NaN EMA outputs
        // because our EMA formula initialises EMA[0] = series[0] and propagates forward.
        // We replicate pandas NaN-propagation: once NaN enters the EMA it stays NaN until
        // the next non-NaN value would reset it. pandas ewm does NOT skip NaN — it propagates.
        val osc = calcMacdNullAware(fg.toList())

        // Step 8: Assemble results
        return data.indices.map { i ->
            FearGreedResult(
                date = data[i].date,
                indexValue = data[i].indexValue,
                fearGreedValue = fg[i],
                oscillator = osc[i],
                rsi = normRsi[i],
                momentum = normMom[i],
                putCallRatio = normPcr[i],
                volatility = normVol[i],
                spread = normSpread[i]
            )
        }
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    /**
     * EMA with pandas ewm(span=period, adjust=False) semantics.
     *
     * alpha = 2 / (period + 1)
     * EMA[0] = series[0]
     * EMA[t] = alpha * series[t] + (1 - alpha) * EMA[t-1]
     *
     * NaN inputs: if series[0] is NaN, EMA starts as NaN and stays NaN until
     * the first finite value, at which point it is reset (mirrors pandas behaviour).
     *
     * @param values Input series (may not contain NaN for typical MACD usage)
     * @param period EMA period
     */
    internal fun calculateEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = values[0]
        result.add(ema)
        for (i in 1 until values.size) {
            ema = alpha * values[i] + (1.0 - alpha) * ema
            result.add(ema)
        }
        return result
    }

    /**
     * MACD oscillator that handles NaN values in the input series.
     *
     * Used internally for computing `Osc = _calc_macd(df["FG"])` where the FG series
     * contains NaN for rows that had missing feature data.
     *
     * NaN propagation rule (mirrors pandas ewm behaviour):
     * - If the current input value is NaN, the EMA state is left unchanged and NaN
     *   is recorded for that position (pandas propagates NaN through ewm).
     * - When a finite value is encountered after a NaN run, the EMA resumes from
     *   its last known finite state.
     *
     * Note: pandas ewm with NaN inputs actually produces NaN for those positions and
     * then re-initialises on the next finite value only if `min_periods` is met.
     * For the FG series the leading NaN block is from the MA warm-up; subsequent
     * values are all finite, so in practice NaN propagation only affects the leading rows.
     *
     * @param series Input series (may contain Double.NaN)
     */
    private fun calcMacdNullAware(
        series: List<Double>,
        short: Int = 12,
        long: Int = 26,
        sig: Int = 9
    ): List<Double> {
        val n = series.size
        if (n == 0) return emptyList()

        val emaShort = emaWithNan(series, short)
        val emaLong = emaWithNan(series, long)
        val macd = DoubleArray(n) { i ->
            if (emaShort[i].isNaN() || emaLong[i].isNaN()) Double.NaN
            else emaShort[i] - emaLong[i]
        }
        val signal = emaWithNan(macd.toList(), sig)
        return List(n) { i ->
            if (macd[i].isNaN() || signal[i].isNaN()) Double.NaN
            else macd[i] - signal[i]
        }
    }

    /**
     * EMA that propagates NaN: a NaN input emits NaN and freezes the EMA state.
     *
     * Initialisation is deferred to the first finite value encountered.
     */
    private fun emaWithNan(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema: Double? = null   // null = not yet initialised
        for (v in values) {
            if (!v.isFinite()) {
                result.add(Double.NaN)
                // do NOT update ema state; when next finite value arrives,
                // EMA continues from last known state (pandas ewm NaN behaviour)
            } else {
                if (ema == null) {
                    ema = v   // first finite value initialises the EMA
                } else {
                    ema = alpha * v + (1.0 - alpha) * ema
                }
                result.add(ema)
            }
        }
        return result
    }

    /**
     * Simple rolling mean (not EMA) over a fixed window.
     *
     * Equivalent to `pandas Series.rolling(period).mean()` with default `min_periods=period`.
     * First `period - 1` positions return `Double.NaN`.
     *
     * @param values  Input series
     * @param period  Window size
     */
    private fun rollingSimpleMean(values: List<Double>, period: Int): List<Double> {
        val n = values.size
        if (n == 0) return emptyList()
        val result = mutableListOf<Double>()
        var runningSum = 0.0
        for (i in 0 until n) {
            runningSum += values[i]
            if (i < period - 1) {
                result.add(Double.NaN)
            } else {
                if (i >= period) {
                    runningSum -= values[i - period]
                }
                result.add(runningSum / period)
            }
        }
        return result
    }

    /**
     * MinMax-normalise an array to [0, 1] using only the rows indicated by `valid`.
     *
     * Mirrors the pandas MinMaxScaler applied to `df.loc[valid, feat]`:
     * - Compute `col_min` and `col_max` across valid rows only.
     * - If `col_max - col_min > 0`: normalise valid rows to `(x - min) / (max - min)`.
     * - If `col_max == col_min`: set valid rows to `0.0`.
     * - Rows where `valid[i] == false` keep their original value (NaN) unchanged.
     *
     * @param values  Raw feature array (NaN at invalid positions)
     * @param valid   Boolean mask; `true` means the row should be normalised
     * @return New DoubleArray of same length; invalid rows contain `Double.NaN`
     */
    private fun minMaxNormalize(values: DoubleArray, valid: BooleanArray): DoubleArray {
        val n = values.size
        if (n == 0) return DoubleArray(0)

        // Compute min/max over valid rows
        var colMin = Double.MAX_VALUE
        var colMax = -Double.MAX_VALUE
        for (i in 0 until n) {
            if (valid[i]) {
                if (values[i] < colMin) colMin = values[i]
                if (values[i] > colMax) colMax = values[i]
            }
        }

        val hasValidRows = colMin != Double.MAX_VALUE
        val result = DoubleArray(n) { Double.NaN }
        val range = colMax - colMin

        for (i in 0 until n) {
            if (!valid[i]) {
                result[i] = Double.NaN
                continue
            }
            result[i] = if (hasValidRows && range > 0.0) {
                (values[i] - colMin) / range
            } else {
                0.0   // col_max == col_min → all valid rows become 0.0
            }
        }
        return result
    }
}
