package com.etfmonitor.feature.market.data.datasource

import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.entities.MarketIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 지수 로컬 데이터 소스
 * Room DAO를 래핑하여 데이터 접근 제공
 */
@Singleton
class MarketIndexLocalDataSource @Inject constructor(
    private val dao: MarketIndexDao
) {
    fun getAllByMarket(market: String): Flow<List<MarketIndex>> =
        dao.getAllByMarket(market).flowOn(Dispatchers.IO)

    fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>> =
        dao.getRecentByMarket(market, limit).flowOn(Dispatchers.IO)

    fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        dao.getByMarketAndDateRange(market, startDate, endDate).flowOn(Dispatchers.IO)

    suspend fun getByMarketAndDate(market: String, date: String): MarketIndex? =
        withContext(Dispatchers.IO) {
            dao.getByMarketAndDate(market, date)
        }

    suspend fun getByMarketAndDateRangeSuspend(
        market: String,
        startDate: String,
        endDate: String
    ): List<MarketIndex> =
        withContext(Dispatchers.IO) {
            dao.getByMarketAndDateRangeSuspend(market, startDate, endDate)
        }

    suspend fun getByDate(date: String): List<MarketIndex> =
        withContext(Dispatchers.IO) {
            dao.getByDate(date)
        }

    suspend fun insertAll(indices: List<MarketIndex>) =
        withContext(Dispatchers.IO) {
            dao.insertAll(indices)
        }

    suspend fun insert(index: MarketIndex) =
        withContext(Dispatchers.IO) {
            dao.insert(index)
        }

    suspend fun deleteByMarket(market: String) =
        withContext(Dispatchers.IO) {
            dao.deleteByMarket(market)
        }

    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }

    suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) {
            dao.getCountByMarket(market)
        }

    suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) {
            dao.getLatestDate(market)
        }

    suspend fun getLastUpdateTime(market: String): Long? =
        withContext(Dispatchers.IO) {
            dao.getLastUpdateTime(market)
        }

    suspend fun hasDataSince(market: String, startDate: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.hasDataSince(market, startDate) > 0
        }

    suspend fun getAllDates(): List<String> =
        withContext(Dispatchers.IO) {
            dao.getAllDates()
        }
}
