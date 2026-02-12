package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.BloodIndicatorDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.krx.BloodIndicatorClient
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toBloodDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.repository.BloodIndicatorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blood Indicator Repository Implementation (v2.0 - FRED API)
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread from FRED)
 * - 100주 SMA 위 (RISK_ON): Green - 시장이 건강하고 위험 자산 선호
 * - 100주 SMA 아래 (RISK_OFF): Red - 시장 스트레스, 안전 자산 선호
 *
 * Requires FRED API key (free from https://fred.stlouisfed.org/docs/api/api_key.html)
 */
@Singleton
class BloodIndicatorRepositoryImpl @Inject constructor(
    private val bloodIndicatorDao: BloodIndicatorDao,
    private val etfDao: EtfDao,
    private val bloodIndicatorClient: BloodIndicatorClient
) : BloodIndicatorRepository {

    companion object {
        private val logger = AppLogger.getLogger("BloodIndicatorRepoImpl")
        private const val KEY_DIALOG_DISMISSED = "blood_indicator_dialog_dismissed"
        private const val KEY_FRED_API_KEY = "fred_api_key"
    }

    /**
     * Set FRED API key in client from stored settings.
     * Should be called before fetching Blood Indicator data.
     */
    private suspend fun setFredApiKeyFromSettings() {
        val fredApiKey = etfDao.getSetting(KEY_FRED_API_KEY)
        if (!fredApiKey.isNullOrBlank()) {
            bloodIndicatorClient.setFredApiKey(fredApiKey)
            logger.d("FRED API key set from settings")
        } else {
            logger.w("FRED API key not configured. Blood Indicator data fetch may fail.")
        }
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

            // Set FRED API key before fetching
            setFredApiKeyFromSettings()

            // Calculate date range
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            onProgress?.invoke("US 시장 데이터 수집 중 (Yahoo Finance + FRED)...", 20)

            // Fetch from Python
            val data = bloodIndicatorClient.fetchBloodIndicator(startStr, endStr)

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

            // Set FRED API key before fetching
            setFredApiKeyFromSettings()

            // Fetch recent 30 days
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            val data = bloodIndicatorClient.fetchBloodIndicator(startStr, endStr)

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
}
