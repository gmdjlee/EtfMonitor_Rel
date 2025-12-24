package com.etfmonitor.feature.market.data.datasource

import com.etfmonitor.database.FearGreedDao
import com.etfmonitor.database.entities.FearGreedIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fear & Greed 로컬 데이터 소스
 * Room DAO를 래핑하여 데이터 접근 제공
 */
@Singleton
class FearGreedLocalDataSource @Inject constructor(
    private val dao: FearGreedDao
) {
    fun getAllByMarket(market: String): Flow<List<FearGreedIndex>> =
        dao.getAllByMarket(market).flowOn(Dispatchers.IO)

    fun getRecentByMarket(market: String, limit: Int): Flow<List<FearGreedIndex>> =
        dao.getRecentByMarket(market, limit).flowOn(Dispatchers.IO)

    fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>> =
        dao.getByMarketAndDateRange(market, startDate, endDate).flowOn(Dispatchers.IO)

    suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex? =
        withContext(Dispatchers.IO) {
            dao.getByMarketAndDate(market, date)
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

    suspend fun insertAll(indices: List<FearGreedIndex>) =
        withContext(Dispatchers.IO) {
            dao.insertAll(indices)
        }

    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
}
