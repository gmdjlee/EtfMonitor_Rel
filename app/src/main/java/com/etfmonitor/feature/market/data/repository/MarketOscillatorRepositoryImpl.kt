package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.database.entities.MarketOscillatorData as MarketOscillatorEntity
import com.etfmonitor.feature.market.data.datasource.MarketOscillatorLocalDataSource
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomainOscillator
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 과매수/과매도 Repository 구현
 */
@Singleton
class MarketOscillatorRepositoryImpl @Inject constructor(
    private val localDataSource: MarketOscillatorLocalDataSource,
    private val pyClient: OscillatorPyClient
) : MarketOscillatorRepository {

    companion object {
        private val logger = AppLogger.getLogger("MarketOscRepoImpl")
        private const val DEFAULT_KEEP_DAYS = 365
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
        localDataSource.getMarketData(market).map { it.toDomainOscillator() }

    override fun getRecentData(market: String, limit: Int): Flow<List<MarketOscillator>> =
        localDataSource.getRecentData(market, limit).map { it.toDomainOscillator() }

    override fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillator>> =
        localDataSource.getDataByDateRange(market, startDate, endDate).map { it.toDomainOscillator() }

    override suspend fun getLatestData(market: String): MarketOscillator? =
        localDataSource.getLatestData(market)?.toDomain()

    override suspend fun getDataCount(market: String): Int =
        localDataSource.getDataCount(market)

    override suspend fun initializeMarketData(
        market: String,
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing $market data for $days days")
            onProgress?.invoke("$market 데이터 수집 준비 중...", 0)

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            onProgress?.invoke("$market 시장 지수 데이터 수집 중...", 30)
            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val response = json.decodeFromString<MarketOscillatorResponse>(jsonStr)

            if (response.error != null) {
                logger.e("Error fetching market data: ${response.error}")
                return@withContext Result.failure(Exception(response.error))
            }

            onProgress?.invoke("$market 데이터 처리 중...", 70)

            if (response.dates.isEmpty() || response.index.isEmpty() || response.oscillator.isEmpty()) {
                logger.e("Empty data received from Python")
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dates = response.dates
            val indexValues = response.index
            val oscillators = response.oscillator

            val dataList = dates.indices.map { i ->
                MarketOscillatorEntity(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            onProgress?.invoke("$market 데이터베이스 저장 중...", 90)
            localDataSource.insertAll(dataList)

            logger.d("Initialized $market with ${dataList.size} data points")
            onProgress?.invoke("$market 완료", 100)
            Result.success(dataList.size)

        } catch (e: Exception) {
            logger.e("Error initializing market data", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMarketData(market: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating $market data")

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startDateStr = startDate.format(formatter)
            val endDateStr = endDate.format(formatter)

            val jsonStr = pyClient.getMarketOscillator(market, startDateStr, endDateStr)
            val response = json.decodeFromString<MarketOscillatorResponse>(jsonStr)

            if (response.error != null) {
                logger.e("Error updating market data: ${response.error}")
                return@withContext Result.failure(Exception(response.error))
            }

            if (response.dates.isEmpty() || response.index.isEmpty() || response.oscillator.isEmpty()) {
                logger.e("Empty data received from Python")
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dates = response.dates
            val indexValues = response.index
            val oscillators = response.oscillator

            val dataList = dates.indices.map { i ->
                MarketOscillatorEntity(
                    id = "${market}-${dates[i]}",
                    market = market,
                    date = dates[i],
                    indexValue = indexValues[i],
                    oscillator = oscillators[i]
                )
            }

            localDataSource.insertAll(dataList)
            localDataSource.deleteOldData(market, DEFAULT_KEEP_DAYS)

            logger.d("Updated $market with ${dataList.size} data points")
            Result.success(dataList.size)

        } catch (e: Exception) {
            logger.e("Error updating market data", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteMarketData(market: String) =
        localDataSource.deleteMarketData(market)

    override suspend fun deleteAll() =
        localDataSource.deleteAll()
}
