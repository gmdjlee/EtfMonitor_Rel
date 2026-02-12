package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.entities.BloodIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Blood Indicator 네이티브 Kotlin 클라이언트
 *
 * blood_indicator.py (Python)를 대체하는 네이티브 구현
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread)
 * - 100주 SMA 위 (RISK_ON): Green - 시장이 건강하고 위험 자산 선호
 * - 100주 SMA 아래 (RISK_OFF): Red - 시장 스트레스, 안전 자산 선호
 *
 * Data Sources:
 * - US03MY: Yahoo Finance (^IRX)
 * - BAMLH0A0HYM2: FRED API (free API key required)
 */
@Singleton
class BloodIndicatorClient @Inject constructor() {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorClient")
        private const val TIMEOUT_MS = 90_000L
        private const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"
        private const val FRED_API_URL = "https://api.stlouisfed.org/fred/series/observations"
        private const val TICKER_IRX = "^IRX"
        private const val FRED_HIGH_YIELD_SPREAD = "BAMLH0A0HYM2"
        private const val SMA_LENGTH = 100
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var fredApiKey: String? = null

    /**
     * Set FRED API key
     */
    suspend fun setFredApiKey(apiKey: String) {
        fredApiKey = apiKey
        logger.d("FRED API key set successfully")
    }

    /**
     * Fetch Blood Indicator data for date range.
     *
     * @param startDate Start date (yyyyMMdd)
     * @param endDate End date (yyyyMMdd)
     * @return List of BloodIndicator entities
     */
    suspend fun fetchBloodIndicator(
        startDate: String,
        endDate: String
    ): List<BloodIndicator> = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching Blood Indicator: $startDate ~ $endDate")

            // Resolve FRED API key
            val apiKey = fredApiKey
            if (apiKey.isNullOrEmpty()) {
                logger.e("FRED API key not set")
                return@withContext emptyList()
            }

            withTimeout(TIMEOUT_MS) {
                val startDt = parseDate(startDate)
                val endDt = parseDate(endDate)

                // Extend start for 100-week SMA calculation
                val extendedStart = startDt.minusWeeks((SMA_LENGTH + 10).toLong())

                // Fetch IRX from Yahoo Finance
                logger.d("Downloading IRX (3M T-Bill) from Yahoo Finance...")
                val irxData = fetchYahooChart(
                    TICKER_IRX,
                    extendedStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
                    endDt.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
                )

                // Fetch High Yield Spread from FRED
                logger.d("Downloading BAMLH0A0HYM2 (High Yield Spread) from FRED...")
                val spreadData = fetchFredSeries(
                    FRED_HIGH_YIELD_SPREAD,
                    apiKey,
                    extendedStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    endDt.format(DateTimeFormatter.ISO_LOCAL_DATE)
                )

                if (irxData.isEmpty()) {
                    logger.e("Failed to fetch IRX data")
                    return@withTimeout emptyList()
                }

                if (spreadData.isEmpty()) {
                    logger.e("Failed to fetch High Yield Spread data")
                    return@withTimeout emptyList()
                }

                // Resample to weekly (W-FRI)
                val irxWeekly = resampleWeeklyFriday(irxData)
                val spreadWeekly = resampleWeeklyFriday(spreadData)

                // Merge datasets by week
                val allWeeks = (irxWeekly.keys + spreadWeekly.keys).sorted().distinct()
                val mergedData = mutableListOf<WeeklyRecord>()

                var lastIrx: Double? = null
                var lastSpread: Double? = null

                for (week in allWeeks) {
                    val irxVal = irxWeekly[week] ?: lastIrx
                    val spreadVal = spreadWeekly[week] ?: lastSpread

                    if (irxVal != null) lastIrx = irxVal
                    if (spreadVal != null) lastSpread = spreadVal

                    if (irxVal != null && spreadVal != null && abs(spreadVal) > 0.01) {
                        mergedData.add(WeeklyRecord(week, irxVal, spreadVal))
                    }
                }

                // Calculate Blood indicator and 100-week SMA
                val bloodValues = mergedData.map { it.irx / it.spread }
                val bloodSma = calculateSma(bloodValues, SMA_LENGTH)

                // Build results
                val results = mutableListOf<BloodIndicator>()
                for (i in mergedData.indices) {
                    val weekDate = mergedData[i].date
                    if (weekDate < startDt || weekDate > endDt) continue

                    val bloodVal = bloodValues[i]
                    val smaVal = bloodSma[i]
                    val signal = if (smaVal.isNaN()) "NEUTRAL"
                    else if (bloodVal > smaVal) "RISK_ON"
                    else "RISK_OFF"
                    val color = when (signal) {
                        "RISK_ON" -> "green"
                        "RISK_OFF" -> "red"
                        else -> "gray"
                    }

                    val dateStr = weekDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    results.add(
                        BloodIndicator(
                            id = BloodIndicator.createId(dateStr),
                            date = dateStr,
                            bloodValue = bloodVal,
                            bloodSma = if (smaVal.isNaN()) 0.0 else smaVal,
                            us03my = mergedData[i].irx,
                            highYieldSpread = mergedData[i].spread,
                            spyClose = null,
                            signalType = signal,
                            signalColor = color,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }

                logger.d("Calculated ${results.size} Blood Indicator records")
                results
            }
        } catch (e: Exception) {
            logger.e("Error fetching Blood Indicator", e)
            emptyList()
        }
    }

    /**
     * Get the latest Blood Indicator value.
     */
    suspend fun getLatestBloodValue(): BloodIndicator? = withContext(Dispatchers.IO) {
        try {
            val end = LocalDate.now()
            val start = end.minusDays(60)
            val results = fetchBloodIndicator(
                start.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            )
            results.lastOrNull()
        } catch (e: Exception) {
            logger.e("Error fetching latest Blood Indicator", e)
            null
        }
    }

    /**
     * Get Blood Indicator summary statistics.
     */
    suspend fun getBloodSummary(): String? = withContext(Dispatchers.IO) {
        try {
            val end = LocalDate.now()
            val start = end.minusWeeks((SMA_LENGTH + 52).toLong())
            val results = fetchBloodIndicator(
                start.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            )

            if (results.isEmpty()) return@withContext null

            val latest = results.last()
            val riskOnCount = results.count { it.signalType == "RISK_ON" }
            val riskOffCount = results.count { it.signalType == "RISK_OFF" }
            val values = results.map { it.bloodValue }

            val summary = mapOf(
                "period" to mapOf(
                    "start" to results.first().date,
                    "end" to results.last().date,
                    "weeks" to results.size
                ),
                "latest" to mapOf(
                    "date" to latest.date,
                    "bloodValue" to latest.bloodValue,
                    "bloodSma" to latest.bloodSma,
                    "signal" to latest.signalType,
                    "signalColor" to latest.signalColor
                ),
                "statistics" to mapOf(
                    "mean" to values.average(),
                    "min" to (values.minOrNull() ?: 0.0),
                    "max" to (values.maxOrNull() ?: 0.0)
                ),
                "signals" to mapOf(
                    "riskOnWeeks" to riskOnCount,
                    "riskOffWeeks" to riskOffCount,
                    "riskOnPct" to if (results.isNotEmpty()) riskOnCount * 100.0 / results.size else 0.0
                )
            )

            Json.encodeToString(kotlinx.serialization.serializer<Map<String, Any>>(), summary)
        } catch (e: Exception) {
            logger.e("Error fetching Blood Indicator summary", e)
            null
        }
    }

    // ======== Private helpers ========

    private fun parseDate(dateStr: String): LocalDate {
        val cleaned = dateStr.replace("-", "")
        return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyyMMdd"))
    }

    private fun fetchYahooChart(symbol: String, startTs: Long, endTs: Long): List<DailyValue> {
        try {
            val url = "${YAHOO_CHART_URL}${symbol}?period1=${startTs}&period2=${endTs}&interval=1d&events=history"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val chart = jsonObj["chart"]?.jsonObject ?: return emptyList()
            val resultArray = chart["result"]?.jsonArray ?: return emptyList()
            if (resultArray.isEmpty()) return emptyList()

            val result = resultArray[0].jsonObject
            val timestamps = result["timestamp"]?.jsonArray ?: return emptyList()
            val quote = result["indicators"]?.jsonObject
                ?.get("quote")?.jsonArray
                ?.getOrNull(0)?.jsonObject ?: return emptyList()
            val closes = quote["close"]?.jsonArray ?: return emptyList()

            val data = mutableListOf<DailyValue>()
            for (i in timestamps.indices) {
                val ts = timestamps[i].jsonPrimitive.longOrNull ?: continue
                val close = closes[i].jsonPrimitive.doubleOrNull ?: continue
                val date = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate()
                data.add(DailyValue(date, close))
            }

            return data
        } catch (e: Exception) {
            logger.e("Error fetching Yahoo chart for $symbol", e)
            return emptyList()
        }
    }

    private fun fetchFredSeries(
        seriesId: String,
        apiKey: String,
        startDate: String,
        endDate: String
    ): List<DailyValue> {
        try {
            val url = "$FRED_API_URL?series_id=$seriesId&api_key=$apiKey" +
                    "&file_type=json&observation_start=$startDate&observation_end=$endDate&sort_order=asc"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val observations = jsonObj["observations"]?.jsonArray ?: return emptyList()

            val data = mutableListOf<DailyValue>()
            for (obs in observations) {
                val obj = obs.jsonObject
                val dateStr = obj["date"]?.jsonPrimitive?.contentOrNull ?: continue
                val valueStr = obj["value"]?.jsonPrimitive?.contentOrNull ?: continue
                if (valueStr == ".") continue

                val value = valueStr.toDoubleOrNull() ?: continue
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                data.add(DailyValue(date, value))
            }

            logger.d("Fetched ${data.size} records from FRED for $seriesId")
            return data
        } catch (e: Exception) {
            logger.e("Error fetching FRED series $seriesId", e)
            return emptyList()
        }
    }

    /**
     * Resample daily data to weekly (W-FRI) by taking last value in each week
     */
    private fun resampleWeeklyFriday(data: List<DailyValue>): Map<LocalDate, Double> {
        val weekly = mutableMapOf<LocalDate, Double>()
        for (item in data) {
            // Get the Friday of this item's week
            val friday = item.date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            weekly[friday] = item.value // Last value wins (data is sorted ascending)
        }
        return weekly
    }

    /**
     * Calculate Simple Moving Average
     */
    private fun calculateSma(values: List<Double>, period: Int): List<Double> {
        return values.mapIndexed { index, _ ->
            val start = maxOf(0, index - period + 1)
            val window = values.subList(start, index + 1)
            window.average()
        }
    }

    private data class DailyValue(val date: LocalDate, val value: Double)
    private data class WeeklyRecord(val date: LocalDate, val irx: Double, val spread: Double)
}
