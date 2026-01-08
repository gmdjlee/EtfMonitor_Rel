package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.entities.MarketOscillatorData as MarketOscillatorEntity
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toOscillatorDomainList
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Oscillator Repository Implementation
 *
 * Clean Architecture 패턴을 따르는 직접 구현:
 * - 180초 timeout으로 200+ 주식 데이터 수집
 * - KOSPI/KOSDAQ 과매수/과매도 지표 관리
 */
@Singleton
class MarketOscillatorRepositoryImpl @Inject constructor(
    private val dao: MarketOscillatorDao,
    private val etfDao: EtfDao,
    private val pyClient: OscillatorPyClient
) : MarketOscillatorRepository {

    companion object {
        private val logger = AppLogger.getLogger("MktOscRepoImpl")
        private const val DEFAULT_KEEP_DAYS = 365
        private const val KEY_DIALOG_DISMISSED = "market_oscillator_dialog_dismissed"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Serializable
    private data class MarketOscillatorResponse(
        val dates: List<String> = emptyList(),
        val index: List<Double> = emptyList(),
        val oscillator: List<Double> = emptyList(),
        val error: String? = null
    )

    override fun getMarketData(market: String): Flow<List<MarketOscillator>> =
        dao.getMarketData(market)
            .map { it.toOscillatorDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecentData(market: String, limit: Int): Flow<List<MarketOscillator>> =
        dao.getRecentData(market, limit)
            .map { it.toOscillatorDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillator>> =
        dao.getDataByDateRange(market, startDate, endDate)
            .map { it.toOscillatorDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getLatestData(market: String): MarketOscillator? =
        withContext(Dispatchers.IO) {
            dao.getLatestData(market)?.toDomain()
        }

    override suspend fun getDataCount(market: String): Int =
        withContext(Dispatchers.IO) {
            dao.getDataCount(market)
        }

    override suspend fun isDialogDismissed(): Boolean = withContext(Dispatchers.IO) {
        etfDao.getSetting(KEY_DIALOG_DISMISSED) == "true"
    }

    override suspend fun saveDialogDismissed() = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_DIALOG_DISMISSED, "true"))
    }

    /**
     * 초기 데이터 수집 (12개월)
     */
    override suspend fun initializeMarketData(
        market: String,
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing $market data for $days days")
            onProgress?.invoke("$market 데이터 수집 준비 중...", 0)

            // 종료일: 오늘
            val endDate = LocalDate.now()
            // 시작일: days일 전
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            // Python에서 데이터 수집
            onProgress?.invoke("$market 시장 지수 데이터 수집 중...", 30)
            coroutineContext.ensureActive()  // 취소 상태 확인
            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val response = json.decodeFromString<MarketOscillatorResponse>(jsonStr)

            if (response.error != null) {
                logger.e("Error fetching market data: ${response.error}")
                return@withContext Result.failure(Exception(response.error))
            }

            onProgress?.invoke("$market 데이터 처리 중...", 70)

            // 데이터 검증
            if (response.dates.isEmpty() || response.index.isEmpty() || response.oscillator.isEmpty()) {
                logger.e("Empty data received from Python")
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dates = response.dates
            val indexValues = response.index
            val oscillators = response.oscillator

            // Entity 리스트 생성
            val dataList = dates.indices.map { i ->
                MarketOscillatorEntity(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            // DB에 저장
            onProgress?.invoke("$market 데이터베이스 저장 중...", 90)
            dao.insertAll(dataList)

            logger.d("Initialized $market with ${dataList.size} data points")
            onProgress?.invoke("$market 완료", 100)
            Result.success(dataList.size)

        } catch (e: Exception) {
            logger.e("Error initializing market data", e)
            Result.failure(e)
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    override suspend fun updateMarketData(market: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating $market data")

            // 최근 30일 데이터 수집
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            // Python에서 데이터 수집
            coroutineContext.ensureActive()  // 취소 상태 확인
            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val response = json.decodeFromString<MarketOscillatorResponse>(jsonStr)

            if (response.error != null) {
                logger.e("Error updating market data: ${response.error}")
                return@withContext Result.failure(Exception(response.error))
            }

            // 데이터 검증
            if (response.dates.isEmpty() || response.index.isEmpty() || response.oscillator.isEmpty()) {
                logger.e("Empty data received from Python")
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dates = response.dates
            val indexValues = response.index
            val oscillators = response.oscillator

            // Entity 리스트 생성
            val dataList = dates.indices.map { i ->
                MarketOscillatorEntity(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            // DB에 저장 (REPLACE 전략으로 업데이트)
            dao.insertAll(dataList)

            // 오래된 데이터 삭제 (1년치만 유지)
            dao.deleteOldData(market, DEFAULT_KEEP_DAYS)

            logger.d("Updated $market with ${dataList.size} data points")
            Result.success(dataList.size)

        } catch (e: Exception) {
            logger.e("Error updating market data", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMarketData(market: String) = withContext(Dispatchers.IO) {
        dao.deleteMarketData(market)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
