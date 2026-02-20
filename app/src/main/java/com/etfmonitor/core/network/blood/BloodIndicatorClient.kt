package com.etfmonitor.core.network.blood

import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blood Indicator Kotlin Client — replaces blood_indicator.py + core.py
 *
 * Fetches data from:
 * - Yahoo Finance: ^IRX (3M T-Bill) and SPY (reference)
 * - FRED API: BAMLH0A0HYM2 (ICE BofA US High Yield Spread)
 *
 * All HTTP operations use OkHttp with retry logic.
 */
@Singleton
class BloodIndicatorClient @Inject constructor() {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorClient")

        private const val YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/"
        private const val FRED_API_URL = "https://api.stlouisfed.org/fred/series/observations"

        private const val TICKER_IRX = "^IRX"
        private const val TICKER_SPY = "SPY"
        private const val FRED_HIGH_YIELD_SPREAD = "BAMLH0A0HYM2"

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val TIMEOUT_SECONDS = 30L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val REQUEST_DELAY_MS = 300L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Raw daily data point from external APIs.
     */
    data class DailyDataPoint(
        val date: LocalDate,
        val value: Double
    )

    /**
     * Fetch ^IRX (3M T-Bill) daily close data from Yahoo Finance.
     */
    suspend fun fetchIrxData(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyDataPoint>> = withContext(Dispatchers.IO) {
        fetchYahooChart(TICKER_IRX, startDate, endDate)
    }

    /**
     * Fetch SPY daily close data from Yahoo Finance (reference).
     */
    suspend fun fetchSpyData(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyDataPoint>> = withContext(Dispatchers.IO) {
        fetchYahooChart(TICKER_SPY, startDate, endDate)
    }

    /**
     * Fetch BAMLH0A0HYM2 (High Yield Spread) from FRED API.
     */
    suspend fun fetchHighYieldSpread(
        fredApiKey: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyDataPoint>> = withContext(Dispatchers.IO) {
        fetchFredSeries(fredApiKey, FRED_HIGH_YIELD_SPREAD, startDate, endDate)
    }

    private suspend fun fetchYahooChart(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyDataPoint>> {
        val startTs = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val endTs = endDate.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)

        val url = "${YAHOO_CHART_URL}${symbol}?period1=$startTs&period2=$endTs&interval=1d&events=history"

        val responseBody = httpGetWithRetry(url) ?: return Result.failure(
            Exception("Failed to fetch $symbol from Yahoo Finance after $MAX_RETRIES retries")
        )

        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val chart = root["chart"]?.jsonObject ?: return Result.failure(
                Exception("Invalid Yahoo response format for $symbol")
            )
            val resultArray = chart["result"]?.jsonArray
            if (resultArray.isNullOrEmpty()) return Result.failure(
                Exception("No data returned for $symbol")
            )

            val result = resultArray[0].jsonObject
            val timestamps = result["timestamp"]?.jsonArray ?: return Result.failure(
                Exception("Missing timestamps for $symbol")
            )
            val quote = result["indicators"]?.jsonObject
                ?.get("quote")?.jsonArray
                ?.getOrNull(0)?.jsonObject ?: return Result.failure(
                Exception("Missing quote data for $symbol")
            )
            val closes = quote["close"]?.jsonArray ?: return Result.failure(
                Exception("Missing close data for $symbol")
            )

            val dataPoints = mutableListOf<DailyDataPoint>()
            for (i in timestamps.indices) {
                val ts = timestamps[i].jsonPrimitive.longOrNull ?: continue
                val close = closes[i].jsonPrimitive.doubleOrNull ?: continue

                val date = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate()
                dataPoints.add(DailyDataPoint(date, close))
            }

            if (dataPoints.isEmpty()) {
                Result.failure(Exception("No valid data parsed for $symbol"))
            } else {
                logger.d("Fetched ${dataPoints.size} records for $symbol")
                Result.success(dataPoints)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error parsing Yahoo response for $symbol", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchFredSeries(
        apiKey: String,
        seriesId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyDataPoint>> {
        if (apiKey.isBlank() || apiKey == "YOUR_FRED_API_KEY") {
            return Result.failure(Exception("FRED API key not configured"))
        }

        val startStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val url = "$FRED_API_URL?series_id=$seriesId&api_key=$apiKey" +
                "&file_type=json&observation_start=$startStr&observation_end=$endStr&sort_order=asc"

        val responseBody = httpGetWithRetry(url) ?: return Result.failure(
            Exception("Failed to fetch $seriesId from FRED API after $MAX_RETRIES retries")
        )

        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val observations = root["observations"]?.jsonArray ?: return Result.failure(
                Exception("Invalid FRED response format for $seriesId")
            )

            val dataPoints = mutableListOf<DailyDataPoint>()
            for (obs in observations) {
                val obj = obs.jsonObject
                val dateStr = obj["date"]?.jsonPrimitive?.content ?: continue
                val valueStr = obj["value"]?.jsonPrimitive?.content ?: continue

                // FRED uses "." for missing values
                if (valueStr == "." || valueStr.isBlank()) continue

                val value = valueStr.toDoubleOrNull() ?: continue
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                dataPoints.add(DailyDataPoint(date, value))
            }

            if (dataPoints.isEmpty()) {
                Result.failure(Exception("No valid data parsed from FRED for $seriesId"))
            } else {
                logger.d("Fetched ${dataPoints.size} records from FRED for $seriesId")
                Result.success(dataPoints)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error parsing FRED response for $seriesId", e)
            Result.failure(e)
        }
    }

    private suspend fun httpGetWithRetry(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        for (attempt in 1..MAX_RETRIES) {
            try {
                return httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        // HTTP error — log redacted URL and stop retrying
                        logger.e("HTTP ${response.code} for ${redactUrl(url)}")
                        null
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                logger.w("Timeout (attempt $attempt/$MAX_RETRIES) for ${redactUrl(url)}")
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: java.io.IOException) {
                logger.w("IO error (attempt $attempt/$MAX_RETRIES) for ${redactUrl(url)}: ${e.message}")
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            }
        }
        return null
    }

    private fun redactUrl(url: String): String {
        return url.replace(Regex("[?&](api_key|apikey|appid)=[^&]*")) { match ->
            val key = match.value.substringBefore("=")
            "$key=***REDACTED***"
        }
    }
}
