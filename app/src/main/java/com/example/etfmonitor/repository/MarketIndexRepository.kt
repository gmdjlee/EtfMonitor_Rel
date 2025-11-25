package com.etfmonitor.repository

import com.etfmonitor.database.MarketIndexDao
import com.etfmonitor.database.entities.MarketIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MarketIndex 데이터 접근 Repository
 * KOSPI/KOSDAQ 지수 데이터 관리
 */
@Singleton
class MarketIndexRepository @Inject constructor(
    private val dao: MarketIndexDao
) {
    /**
     * 특정 시장의 모든 데이터 조회
     */
    fun getAllByMarket(market: String): Flow<List<MarketIndex>> =
        dao.getAllByMarket(market).flowOn(Dispatchers.IO)

    /**
     * 특정 시장의 특정 날짜 데이터 조회
     */
    suspend fun getByMarketAndDate(market: String, date: String): MarketIndex? =
        withContext(Dispatchers.IO) {
            dao.getByMarketAndDate(market, date)
        }

    /**
     * 특정 시장의 최근 N개 데이터 조회
     */
    fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>> =
        dao.getRecentByMarket(market, limit).flowOn(Dispatchers.IO)

    /**
     * 특정 시장의 기간별 데이터 조회
     */
    fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        dao.getByMarketAndDateRange(market, startDate, endDate).flowOn(Dispatchers.IO)

    /**
     * 특정 시장의 기간별 데이터 조회 (suspend)
     */
    suspend fun getByMarketAndDateRangeSuspend(
        market: String,
        startDate: String,
        endDate: String
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        dao.getByMarketAndDateRangeSuspend(market, startDate, endDate)
    }

    /**
     * 모든 시장의 특정 날짜 데이터 조회
     */
    suspend fun getByDate(date: String): List<MarketIndex> =
        withContext(Dispatchers.IO) {
            dao.getByDate(date)
        }

    /**
     * 데이터 삽입/업데이트
     */
    suspend fun insertAll(indices: List<MarketIndex>) =
        withContext(Dispatchers.IO) {
            dao.insertAll(indices)
        }

    suspend fun insert(index: MarketIndex) =
        withContext(Dispatchers.IO) {
            dao.insert(index)
        }

    /**
     * 특정 시장 데이터 삭제
     */
    suspend fun deleteByMarket(market: String) =
        withContext(Dispatchers.IO) {
            dao.deleteByMarket(market)
        }

    /**
     * 모든 데이터 삭제
     */
    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }

    /**
     * 특정 시장의 데이터 개수
     */
    suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) {
            dao.getCountByMarket(market)
        }

    /**
     * 특정 시장의 최신 날짜
     */
    suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) {
            dao.getLatestDate(market)
        }

    /**
     * 특정 시장의 최종 업데이트 시간
     */
    suspend fun getLastUpdateTime(market: String): Long? =
        withContext(Dispatchers.IO) {
            dao.getLastUpdateTime(market)
        }

    /**
     * 데이터 존재 여부 확인
     */
    suspend fun hasData(market: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getCountByMarket(market) > 0
        }

    /**
     * 최근 N일의 데이터 존재 여부 확인
     */
    suspend fun hasDataSince(market: String, startDate: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.hasDataSince(market, startDate) > 0
        }

    /**
     * 모든 날짜 목록 조회
     */
    suspend fun getAllDates(): List<String> =
        withContext(Dispatchers.IO) {
            dao.getAllDates()
        }
}
