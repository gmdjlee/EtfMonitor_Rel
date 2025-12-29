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
 * Python response model for Blood Indicator data
 */
@Serializable
private data class BloodIndicatorResponse(
    val id: String,
    val date: String,
    val bloodValue: Double,
    val irx: Double,
    val hygYield: Double,
    val tenYearYield: Double,
    val spreadValue: Double,
    val spyClose: Double? = null,
    val signalType: String
)

/**
 * Blood Indicator Python Client
 * US Treasury 기반 시장 건강도 지표 데이터 수집
 *
 * BLOOD = IRX (3M T-Bill) / (HYG Yield - 10Y Treasury)
 * - 상승 추세 (RISK_ON): 시장이 건강하고 위험 자산 선호
 * - 하락 추세 (RISK_OFF): 시장 스트레스, 안전 자산 선호
 */
@Singleton
class BloodIndicatorPyClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorPyClient")
        private const val TIMEOUT_MS = 60_000L  // 60 seconds for US market data
        private const val MODULE_NAME = "blood_indicator"
    }

    private val python = Python.getInstance()
    private val module by lazy { python.getModule(MODULE_NAME) }
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
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
                    irx = response.irx,
                    hygYield = response.hygYield,
                    tenYearYield = response.tenYearYield,
                    spreadValue = response.spreadValue,
                    spyClose = response.spyClose,
                    signalType = response.signalType,
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
                val signal: String,
                val irx: Double,
                val hygYield: Double,
                val tenYearYield: Double,
                val spreadValue: Double
            )

            val response = json.decodeFromString<LatestResponse>(result)

            BloodIndicator(
                id = BloodIndicator.createId(response.date),
                date = response.date,
                bloodValue = response.bloodValue,
                irx = response.irx,
                hygYield = response.hygYield,
                tenYearYield = response.tenYearYield,
                spreadValue = response.spreadValue,
                spyClose = null,
                signalType = response.signal,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.e("Error fetching latest Blood Indicator", e)
            null
        }
    }
}
