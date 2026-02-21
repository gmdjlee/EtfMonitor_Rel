package com.etfmonitor.core.analysis

import com.etfmonitor.core.network.blood.BloodIndicatorClient
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Blood Indicator Calculator — Pure Kotlin computation engine
 *
 * Replaces blood_indicator.py calculation logic:
 * - Weekly resampling (W-FRI: last value per week ending Friday)
 * - Forward fill for sparse FRED data
 * - BLOOD = US03MY / HighYieldSpread
 * - 100-week Simple Moving Average
 * - Signal generation: RISK_ON (above SMA) / RISK_OFF (below SMA)
 *
 * Design: Pure object (no DI, no coroutines), maximum testability.
 * Follows FearGreedCalculator / TechnicalAnalysisEngine precedent.
 */
object BloodIndicatorCalculator {

    const val SMA_LENGTH = 100

    /**
     * Weekly data point after resampling and calculation.
     */
    data class BloodWeeklyData(
        val date: LocalDate,
        val us03my: Double,
        val highYieldSpread: Double,
        val spyClose: Double?,
        val bloodValue: Double,
        val bloodSma: Double,
        val signalType: String,
        val signalColor: String
    )

    /**
     * Calculate Blood Indicator from raw daily data.
     *
     * @param irxDaily ^IRX daily close data
     * @param spreadDaily BAMLH0A0HYM2 daily data from FRED
     * @param spyDaily SPY daily close data (optional, for reference)
     * @param requestedStart User's requested start date (for filtering)
     * @param requestedEnd User's requested end date (for filtering)
     * @return List of weekly BloodWeeklyData within the requested date range
     */
    fun calculate(
        irxDaily: List<BloodIndicatorClient.DailyDataPoint>,
        spreadDaily: List<BloodIndicatorClient.DailyDataPoint>,
        spyDaily: List<BloodIndicatorClient.DailyDataPoint>?,
        requestedStart: LocalDate,
        requestedEnd: LocalDate
    ): List<BloodWeeklyData> {
        if (irxDaily.isEmpty() || spreadDaily.isEmpty()) return emptyList()

        // Step 1: Resample to weekly (W-FRI) — last value per week ending Friday
        val irxWeekly = resampleWeeklyFriday(irxDaily)
        val spreadWeekly = resampleWeeklyFriday(spreadDaily)
        val spyWeekly = spyDaily?.let { resampleWeeklyFriday(it) }

        if (irxWeekly.isEmpty()) return emptyList()

        // Step 2: Align all series to IRX weekly dates with forward fill
        val spreadMap = forwardFillToIndex(spreadWeekly, irxWeekly.map { it.first })
        val spyMap = spyWeekly?.let { forwardFillToIndex(it, irxWeekly.map { d -> d.first }) }

        // Step 3: Build combined weekly data
        val weeklyData = mutableListOf<Triple<LocalDate, Double, Double?>>()  // date, irx, spread
        val spyValues = mutableListOf<Double?>()

        for ((date, irxValue) in irxWeekly) {
            val spreadValue = spreadMap[date] ?: continue  // skip if no spread data available
            weeklyData.add(Triple(date, irxValue, spreadValue))
            spyValues.add(spyMap?.get(date))
        }

        if (weeklyData.isEmpty()) return emptyList()

        // Step 4: Calculate BLOOD = US03MY / HighYieldSpread
        val bloodValues = weeklyData.map { (_, us03my, spread) ->
            if (spread != null && kotlin.math.abs(spread) > 0.01) {
                us03my / spread
            } else {
                Double.NaN
            }
        }

        // Step 5: Calculate 100-week SMA (min_periods=1, matching Python)
        val bloodSmaValues = rollingMean(bloodValues, SMA_LENGTH, minPeriods = 1)

        // Step 6: Generate signals and build result
        val result = mutableListOf<BloodWeeklyData>()
        for (i in weeklyData.indices) {
            val (date, us03my, _) = weeklyData[i]
            val spread = weeklyData[i].third ?: continue
            val blood = bloodValues[i]
            val sma = bloodSmaValues[i]

            if (blood.isNaN()) continue

            val (signalType, signalColor) = calcSignal(blood, sma)

            // Step 7: Filter to requested date range
            if (date >= requestedStart && date <= requestedEnd) {
                result.add(
                    BloodWeeklyData(
                        date = date,
                        us03my = us03my,
                        highYieldSpread = spread,
                        spyClose = spyValues.getOrNull(i),
                        bloodValue = blood,
                        bloodSma = sma,
                        signalType = signalType,
                        signalColor = signalColor
                    )
                )
            }
        }

        return result
    }

    /**
     * Resample daily data to weekly (W-FRI).
     * Groups by ISO week ending Friday, takes last value in each week.
     * Matches pandas resample("W-FRI").last() behavior.
     */
    internal fun resampleWeeklyFriday(
        dailyData: List<BloodIndicatorClient.DailyDataPoint>
    ): List<Pair<LocalDate, Double>> {
        if (dailyData.isEmpty()) return emptyList()

        // Group by week-ending-Friday bucket
        val weekBuckets = mutableMapOf<LocalDate, BloodIndicatorClient.DailyDataPoint>()

        for (point in dailyData) {
            val friday = getWeekEndingFriday(point.date)
            // Keep the latest data point within each week (last value)
            val existing = weekBuckets[friday]
            if (existing == null || point.date >= existing.date) {
                weekBuckets[friday] = point
            }
        }

        return weekBuckets.entries
            .sortedBy { it.key }
            .map { (friday, point) -> Pair(friday, point.value) }
    }

    /**
     * Get the Friday that ends the week containing the given date.
     * Matches pandas W-FRI behavior: Saturday → next Friday, other days → current/next Friday.
     */
    internal fun getWeekEndingFriday(date: LocalDate): LocalDate {
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.plusDays(6)  // Saturday → next Friday
            else -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        }
    }

    /**
     * Forward fill values to match target dates.
     * Matches pandas reindex(method="ffill") behavior.
     *
     * O(n+m) two-pointer approach: requires targetDates to be sorted ascending.
     * Both sorted data and sorted targetDates are walked with a single pointer each.
     */
    internal fun forwardFillToIndex(
        data: List<Pair<LocalDate, Double>>,
        targetDates: List<LocalDate>
    ): Map<LocalDate, Double> {
        if (data.isEmpty() || targetDates.isEmpty()) return emptyMap()
        val result = mutableMapOf<LocalDate, Double>()
        val sorted = data.sortedBy { it.first }
        val sortedTargets = targetDates.sorted()
        var dataIdx = 0
        for (targetDate in sortedTargets) {
            // Advance data pointer while the next data point is still <= targetDate
            while (dataIdx < sorted.size - 1 && sorted[dataIdx + 1].first <= targetDate) {
                dataIdx++
            }
            if (sorted[dataIdx].first <= targetDate) {
                result[targetDate] = sorted[dataIdx].second
            }
        }
        return result
    }

    /**
     * Rolling mean with configurable window and minimum periods.
     * Matches pandas rolling(window, min_periods).mean() behavior.
     */
    internal fun rollingMean(
        values: List<Double>,
        window: Int,
        minPeriods: Int = 1
    ): List<Double> {
        return values.indices.map { i ->
            val start = maxOf(0, i - window + 1)
            val windowValues = values.subList(start, i + 1).filter { !it.isNaN() }
            if (windowValues.size >= minPeriods) {
                windowValues.sum() / windowValues.size
            } else {
                Double.NaN
            }
        }
    }

    /**
     * Calculate signal based on Blood Indicator vs 100-week SMA.
     * Matches Pine Script logic: above SMA = RISK_ON (green), below = RISK_OFF (red).
     */
    internal fun calcSignal(bloodValue: Double, smaValue: Double): Pair<String, String> {
        if (smaValue.isNaN()) return Pair("NEUTRAL", "gray")

        return if (bloodValue > smaValue) {
            Pair("RISK_ON", "green")
        } else {
            Pair("RISK_OFF", "red")
        }
    }
}
