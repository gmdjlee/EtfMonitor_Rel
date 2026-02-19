package com.etfmonitor.core.network.python

import android.content.Context
import com.chaquo.python.Python
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.entities.BloodIndicator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python response model for Blood Indicator data (v2.0 - FRED API)
 */
@Serializable
private data class BloodIndicatorResponse(
    val id: String,
    val date: String,
    val bloodValue: Double,
    val bloodSma: Double,
    val us03my: Double,
    val highYieldSpread: Double,
    val spyClose: Double? = null,
    val signalType: String,
    val signalColor: String
)

/**
 * Blood Indicator Python Client (v2.0 - FRED API)
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
class BloodIndicatorPyClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val python: Python
) {
    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorPyClient")
        private const val TIMEOUT_MS = 90_000L  // 90 seconds for 100-week SMA calculation
        private const val MODULE_NAME = "blood_indicator"
    }
    private val module by lazy { python.getModule(MODULE_NAME) }
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Set FRED API key for data collection.
     * Required for fetching High Yield Spread data from FRED.
     *
     * Get free key from: https://fred.stlouisfed.org/docs/api/api_key.html
     *
     * @param apiKey FRED API key
     */
    suspend fun setFredApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        try {
            module.callAttr("set_fred_api_key", apiKey)
            logger.d("FRED API key set successfully")
        } catch (e: Exception) {
            logger.e("Error setting FRED API key", e)
        }
    }

    /**
     * Fetch Blood Indicator data for date range.
     *
     * @param startDate Start date (YYYYMMDD)
     * @param endDate End date (YYYYMMDD)
     * @return List of BloodIndicator entities or empty list on error
     */
    suspend fun fetchBloodIndicator(
        startDate: String,
        endDate: String
    ): List<BloodIndicator> = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching Blood Indicator: $startDate ~ $endDate")

            val result = withTimeout(TIMEOUT_MS) {
                module.callAttr("get_blood_indicator_json", startDate, endDate).toString()
            }

            // Check for error response
            if (result.contains("\"error\"")) {
                logger.e("Python returned error: $result")
                return@withContext emptyList()
            }

            val responses = json.decodeFromString<List<BloodIndicatorResponse>>(result)

            logger.d("Parsed ${responses.size} Blood Indicator records")

            responses.map { response ->
                BloodIndicator(
                    id = response.id,
                    date = response.date,
                    bloodValue = response.bloodValue,
                    bloodSma = response.bloodSma,
                    us03my = response.us03my,
                    highYieldSpread = response.highYieldSpread,
                    spyClose = response.spyClose,
                    signalType = response.signalType,
                    signalColor = response.signalColor,
                    lastUpdated = System.currentTimeMillis()
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.e("Blood Indicator fetch timeout", e)
            emptyList()
        } catch (e: Exception) {
            logger.e("Error fetching Blood Indicator", e)
            emptyList()
        }
    }

    /**
     * Get the latest Blood Indicator value.
     *
     * @return Latest BloodIndicator or null on error
     */
    suspend fun getLatestBloodValue(): BloodIndicator? = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching latest Blood Indicator value")

            val result = withTimeout(TIMEOUT_MS) {
                module.callAttr("get_latest_blood_value").toString()
            }

            if (result.contains("\"error\"")) {
                logger.e("Python returned error: $result")
                return@withContext null
            }

            @Serializable
            data class LatestResponse(
                val date: String,
                val bloodValue: Double,
                val bloodSma: Double,
                val signal: String,
                val signalColor: String,
                val us03my: Double,
                val highYieldSpread: Double
            )

            val response = json.decodeFromString<LatestResponse>(result)

            BloodIndicator(
                id = BloodIndicator.createId(response.date),
                date = response.date,
                bloodValue = response.bloodValue,
                bloodSma = response.bloodSma,
                us03my = response.us03my,
                highYieldSpread = response.highYieldSpread,
                spyClose = null,
                signalType = response.signal,
                signalColor = response.signalColor,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.e("Error fetching latest Blood Indicator", e)
            null
        }
    }

    /**
     * Get Blood Indicator summary statistics.
     *
     * @return Summary data as JSON string or null on error
     */
    suspend fun getBloodSummary(): String? = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching Blood Indicator summary")

            val result = withTimeout(TIMEOUT_MS) {
                module.callAttr("get_blood_summary").toString()
            }

            if (result.contains("\"error\"")) {
                logger.e("Python returned error: $result")
                return@withContext null
            }

            result
        } catch (e: Exception) {
            logger.e("Error fetching Blood Indicator summary", e)
            null
        }
    }
}
