package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.analysis.BloodIndicatorCalculator
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.BloodIndicatorDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.blood.BloodIndicatorClient
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toBloodDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import com.etfmonitor.core.database.entities.BloodIndicator as BloodIndicatorEntity

/**
 * Blood Indicator Repository Implementation (v3.0 - Native Kotlin)
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread from FRED)
 * - 100주 SMA 위 (RISK_ON): Green - 시장이 건강하고 위험 자산 선호
 * - 100주 SMA 아래 (RISK_OFF): Red - 시장 스트레스, 안전 자산 선호
 *
 * Uses native Kotlin BloodIndicatorClient + BloodIndicatorCalculator (no Python).
 * Requires FRED API key (free from https://fred.stlouisfed.org/docs/api/api_key.html)
 */
@Singleton
class BloodIndicatorRepositoryImpl @Inject constructor(
    private val bloodIndicatorDao: BloodIndicatorDao,
    private val etfDao: EtfDao,
    private val bloodClient: BloodIndicatorClient
) : BloodIndicatorRepository {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorRepoImpl")
        private const val KEY_DIALOG_DISMISSED = "blood_indicator_dialog_dismissed"
        private const val KEY_FRED_API_KEY = "fred_api_key"
        private const val SMA_WARMUP_WEEKS = 110L  // 100-week SMA + 10 extra weeks buffer
    }

    /**
     * Get FRED API key from stored settings.
     */
    private suspend fun getFredApiKey(): String? {
        return etfDao.getSetting(KEY_FRED_API_KEY)
    }

    override fun getAll(): Flow<List<BloodIndicator>> =
        bloodIndicatorDao.getAll()
            .map { it.toBloodDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecent(limit: Int): Flow<List<BloodIndicator>> =
        bloodIndicatorDao.getRecent(limit)
            .map { it.toBloodDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getByDateRange(startDate: String, endDate: String): Flow<List<BloodIndicator>> =
        bloodIndicatorDao.getByDateRange(startDate, endDate)
            .map { it.toBloodDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getByDate(date: String): BloodIndicator? =
        withContext(Dispatchers.IO) {
            bloodIndicatorDao.getByDate(date)?.toDomain()
        }

    override suspend fun getCount(): Int =
        withContext(Dispatchers.IO) {
            bloodIndicatorDao.getCount()
        }

    override suspend fun getLatestDate(): String? =
        withContext(Dispatchers.IO) {
            bloodIndicatorDao.getLatestDate()
        }

    override suspend fun getEarliestDate(): String? =
        withContext(Dispatchers.IO) {
            bloodIndicatorDao.getEarliestDate()
        }

    override suspend fun getLastUpdateTime(): Long? =
        withContext(Dispatchers.IO) {
            bloodIndicatorDao.getLastUpdateTime()
        }

    override suspend fun isDialogDismissed(): Boolean = withContext(Dispatchers.IO) {
        etfDao.getSetting(KEY_DIALOG_DISMISSED) == "true"
    }

    override suspend fun saveDialogDismissed() = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_DIALOG_DISMISSED, "true"))
    }

    override suspend fun initializeBloodIndicator(
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing Blood Indicator: $days days")

            onProgress?.invoke("Blood Indicator 데이터 수집 준비 중...", 0)

            val fredApiKey = getFredApiKey()
            if (fredApiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("FRED API 키가 설정되지 않았습니다"))
            }

            // Calculate date range
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())

            // Extend start date for 100-week SMA warmup
            val extendedStart = startDate.minusWeeks(SMA_WARMUP_WEEKS)

            onProgress?.invoke("US 시장 데이터 수집 중 (Yahoo Finance + FRED)...", 20)

            // Fetch data from APIs
            val data = fetchAndCalculate(fredApiKey, extendedStart, endDate, startDate, endDate)

            if (data.isEmpty()) {
                logger.e("No Blood Indicator data fetched")
                return@withContext Result.failure(Exception("데이터를 가져올 수 없습니다"))
            }

            onProgress?.invoke("데이터베이스 저장 중...", 80)

            // Save to database
            bloodIndicatorDao.deleteAll()
            bloodIndicatorDao.insertAll(data)

            logger.d("Saved ${data.size} Blood Indicator records")
            onProgress?.invoke("완료", 100)

            Result.success(data.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Initialization cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error initializing Blood Indicator", e)
            Result.failure(e)
        }
    }

    override suspend fun updateBloodIndicator(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating Blood Indicator...")

            val fredApiKey = getFredApiKey()
            if (fredApiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("FRED API 키가 설정되지 않았습니다"))
            }

            // Fetch recent 30 days (with SMA warmup extension)
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)
            val extendedStart = startDate.minusWeeks(SMA_WARMUP_WEEKS)

            val data = fetchAndCalculate(fredApiKey, extendedStart, endDate, startDate, endDate)

            if (data.isEmpty()) {
                logger.e("No Blood Indicator data fetched for update")
                return@withContext Result.failure(Exception("업데이트 데이터를 가져올 수 없습니다"))
            }

            // Insert/replace (REPLACE strategy)
            bloodIndicatorDao.insertAll(data)

            logger.d("Updated ${data.size} Blood Indicator records")
            Result.success(data.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Update cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error updating Blood Indicator", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch data from Yahoo Finance + FRED, then calculate Blood Indicator.
     *
     * @param fredApiKey FRED API key
     * @param fetchStart Extended start date (includes SMA warmup period)
     * @param fetchEnd End date for API fetch
     * @param requestedStart User's requested start date (for filtering results)
     * @param requestedEnd User's requested end date (for filtering results)
     * @return List of BloodIndicator entities ready for Room storage
     */
    private suspend fun fetchAndCalculate(
        fredApiKey: String,
        fetchStart: LocalDate,
        fetchEnd: LocalDate,
        requestedStart: LocalDate,
        requestedEnd: LocalDate
    ): List<BloodIndicatorEntity> {
        // Fetch IRX (3M T-Bill) from Yahoo Finance
        val irxResult = bloodClient.fetchIrxData(fetchStart, fetchEnd)
        delay(300) // Rate limiting

        // Fetch High Yield Spread from FRED
        val spreadResult = bloodClient.fetchHighYieldSpread(fredApiKey, fetchStart, fetchEnd)
        delay(300) // Rate limiting

        // Fetch SPY (reference) — optional, don't fail if unavailable
        val spyResult = bloodClient.fetchSpyData(fetchStart, fetchEnd)

        val irxData = irxResult.getOrElse {
            logger.e("Failed to fetch IRX data: ${it.message}")
            return emptyList()
        }

        val spreadData = spreadResult.getOrElse {
            logger.e("Failed to fetch High Yield Spread data: ${it.message}")
            return emptyList()
        }

        val spyData = spyResult.getOrNull()

        // Calculate Blood Indicator
        val weeklyData = BloodIndicatorCalculator.calculate(
            irxDaily = irxData,
            spreadDaily = spreadData,
            spyDaily = spyData,
            requestedStart = requestedStart,
            requestedEnd = requestedEnd
        )

        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        // Convert to Room entities
        return weeklyData.map { weekly ->
            val dateStr = weekly.date.format(formatter)
            BloodIndicatorEntity(
                id = "BLOOD-$dateStr",
                date = dateStr,
                bloodValue = weekly.bloodValue,
                bloodSma = weekly.bloodSma,
                us03my = weekly.us03my,
                highYieldSpread = weekly.highYieldSpread,
                spyClose = weekly.spyClose,
                signalType = weekly.signalType,
                signalColor = weekly.signalColor,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
