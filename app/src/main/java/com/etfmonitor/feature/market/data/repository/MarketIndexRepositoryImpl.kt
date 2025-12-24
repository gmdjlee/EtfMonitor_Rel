package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.feature.market.data.datasource.MarketIndexLocalDataSource
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomainIndex
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toEntity
import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import com.etfmonitor.core.network.python.MarketIndexPyClient
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 지수 Repository 구현
 */
@Singleton
class MarketIndexRepositoryImpl @Inject constructor(
    private val localDataSource: MarketIndexLocalDataSource,
    private val pyClient: MarketIndexPyClient
) : MarketIndexRepository {

    companion object {
        private val logger = AppLogger.getLogger("MarketIndexRepoImpl")
    }

    override fun getAllByMarket(market: String): Flow<List<MarketIndex>> =
        localDataSource.getAllByMarket(market).map { it.toDomainIndex() }

    override suspend fun getByMarketAndDate(market: String, date: String): MarketIndex? =
        localDataSource.getByMarketAndDate(market, date)?.toDomain()

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>> =
        localDataSource.getRecentByMarket(market, limit).map { it.toDomainIndex() }

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        localDataSource.getByMarketAndDateRange(market, startDate, endDate).map { it.toDomainIndex() }

    override suspend fun getByMarketAndDateRangeSuspend(
        market: String,
        startDate: String,
        endDate: String
    ): List<MarketIndex> =
        localDataSource.getByMarketAndDateRangeSuspend(market, startDate, endDate).toDomainIndex()

    override suspend fun getByDate(date: String): List<MarketIndex> =
        localDataSource.getByDate(date).toDomainIndex()

    override suspend fun insertAll(indices: List<MarketIndex>) =
        localDataSource.insertAll(indices.map { it.toEntity() })

    override suspend fun insert(index: MarketIndex) =
        localDataSource.insert(index.toEntity())

    override suspend fun deleteByMarket(market: String) =
        localDataSource.deleteByMarket(market)

    override suspend fun deleteAll() =
        localDataSource.deleteAll()

    override suspend fun getCountByMarket(market: String): Int =
        localDataSource.getCountByMarket(market)

    override suspend fun getLatestDate(market: String): String? =
        localDataSource.getLatestDate(market)

    override suspend fun getLastUpdateTime(market: String): Long? =
        localDataSource.getLastUpdateTime(market)

    override suspend fun hasData(market: String): Boolean =
        localDataSource.getCountByMarket(market) > 0

    override suspend fun hasDataSince(market: String, startDate: String): Boolean =
        localDataSource.hasDataSince(market, startDate)

    override suspend fun getAllDates(): List<String> =
        localDataSource.getAllDates()

    override suspend fun initializeMarketIndex(days: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing market index data for $days days")

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            val indices = pyClient.fetchMarketIndices(startStr, endStr)

            if (indices.isEmpty()) {
                logger.e("No market index data fetched")
                return@withContext Result.failure(Exception("시장 지수 데이터를 가져올 수 없습니다"))
            }

            localDataSource.deleteAll()
            localDataSource.insertAll(indices)

            logger.d("Successfully initialized ${indices.size} market index records")
            Result.success(indices.size)
        } catch (e: Exception) {
            logger.e("Error initializing market index data", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMarketIndex(days: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating market index data for recent $days days")

            val indices = pyClient.fetchRecentDays(days)

            if (indices.isEmpty()) {
                logger.e("No market index data fetched for update")
                return@withContext Result.failure(Exception("시장 지수 데이터를 가져올 수 없습니다"))
            }

            localDataSource.insertAll(indices)

            logger.d("Successfully updated ${indices.size} market index records")
            Result.success(indices.size)
        } catch (e: Exception) {
            logger.e("Error updating market index data", e)
            Result.failure(e)
        }
    }
}
