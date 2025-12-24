package com.etfmonitor.feature.etf.data.datasource

import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.Holding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ETF Local Data Source
 *
 * Room 데이터베이스에 대한 로컬 데이터 접근을 담당합니다.
 * Entity 객체를 직접 반환하며, Domain 변환은 Repository에서 수행합니다.
 */
@Singleton
class EtfLocalDataSource @Inject constructor(
    private val etfDao: EtfDao
) {

    // ========== ETF List ==========

    /**
     * 모든 ETF 목록 조회 (Flow)
     */
    fun getAllEtfs(): Flow<List<Etf>> = etfDao.getAllEtfs()

    /**
     * ETF 검색 (Flow)
     */
    fun searchEtfs(query: String): Flow<List<Etf>> = etfDao.searchEtfs(query)

    // ========== Data Status ==========

    /**
     * ETF 개수 조회
     */
    suspend fun getEtfCount(): Int = withContext(Dispatchers.IO) {
        etfDao.getEtfCount()
    }

    /**
     * 최신 데이터 날짜 조회
     */
    suspend fun getLatestDate(): String? = withContext(Dispatchers.IO) {
        etfDao.getLatestDate()
    }

    // ========== ETF Detail ==========

    /**
     * ETF 정보 조회
     */
    suspend fun getEtf(ticker: String): Etf? = withContext(Dispatchers.IO) {
        etfDao.getEtf(ticker)
    }

    /**
     * ETF의 데이터 날짜 목록 조회 (내림차순)
     */
    suspend fun getDates(etfTicker: String): List<String> = withContext(Dispatchers.IO) {
        etfDao.getDates(etfTicker)
    }

    /**
     * 특정 날짜의 Holdings 조회
     */
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding> = withContext(Dispatchers.IO) {
        etfDao.getHoldings(etfTicker, date)
    }
}
