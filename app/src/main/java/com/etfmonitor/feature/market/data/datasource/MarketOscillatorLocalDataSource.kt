package com.etfmonitor.feature.market.data.datasource

import com.etfmonitor.database.MarketOscillatorDao
import com.etfmonitor.database.entities.MarketOscillatorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시장 과매수/과매도 로컬 데이터 소스
 * Room DAO를 래핑하여 데이터 접근 제공
 */
@Singleton
class MarketOscillatorLocalDataSource @Inject constructor(
    private val dao: MarketOscillatorDao
) {
    fun getMarketData(market: String): Flow<List<MarketOscillatorData>> =
        dao.getMarketData(market).flowOn(Dispatchers.IO)

    fun getRecentData(market: String, limit: Int): Flow<List<MarketOscillatorData>> =
        dao.getRecentData(market, limit).flowOn(Dispatchers.IO)

    fun getDataByDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketOscillatorData>> =
        dao.getDataByDateRange(market, startDate, endDate).flowOn(Dispatchers.IO)

    suspend fun getLatestData(market: String): MarketOscillatorData? =
        withContext(Dispatchers.IO) {
            dao.getLatestData(market)
        }

    suspend fun getDataCount(market: String): Int =
        withContext(Dispatchers.IO) {
            dao.getDataCount(market)
        }

    suspend fun insertAll(data: List<MarketOscillatorData>) =
        withContext(Dispatchers.IO) {
            dao.insertAll(data)
        }

    suspend fun deleteMarketData(market: String) =
        withContext(Dispatchers.IO) {
            dao.deleteMarketData(market)
        }

    suspend fun deleteOldData(market: String, keepDays: Int) =
        withContext(Dispatchers.IO) {
            dao.deleteOldData(market, keepDays)
        }

    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
}
