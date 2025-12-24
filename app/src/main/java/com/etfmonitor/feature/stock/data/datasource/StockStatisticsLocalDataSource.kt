package com.etfmonitor.feature.stock.data.datasource

import com.etfmonitor.database.EtfDao
import com.etfmonitor.database.entities.HoldingTimeSeries
import com.etfmonitor.database.entities.StockAmountRanking
import com.etfmonitor.database.entities.StockChangeInfo
import com.etfmonitor.database.entities.CashDepositTrend
import com.etfmonitor.database.StockSearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Statistics Local Data Source
 *
 * 종목 통계 데이터에 대한 로컬 데이터 접근을 담당합니다.
 * EtfDao를 사용하여 Holding 기반 통계를 조회합니다.
 *
 * @property etfDao ETF DAO
 */
@Singleton
class StockStatisticsLocalDataSource @Inject constructor(
    private val etfDao: EtfDao
) {
    // ========== 날짜 조회 ==========

    suspend fun getLatestDate(): String? = etfDao.getLatestDate()

    suspend fun getDates(etfTicker: String): List<String> = etfDao.getDates(etfTicker)

    // ========== 시계열 ==========

    suspend fun getHoldingTimeSeries(etfTicker: String, stockTicker: String): List<HoldingTimeSeries> {
        return etfDao.getHoldingTimeSeries(etfTicker, stockTicker)
    }

    suspend fun getHoldings(etfTicker: String, date: String) = etfDao.getHoldings(etfTicker, date)

    // ========== 금액순위 ==========

    suspend fun getStockAmountRanking(currentDate: String, previousDate: String): List<StockAmountRanking> {
        return etfDao.getStockAmountRanking(currentDate, previousDate)
    }

    // ========== 종목 변화 ==========

    suspend fun getAllNewStocks(currentDate: String, previousDate: String): List<StockChangeInfo> {
        return etfDao.getAllNewStocks(currentDate, previousDate)
    }

    suspend fun getAllRemovedStocks(currentDate: String, previousDate: String): List<StockChangeInfo> {
        return etfDao.getAllRemovedStocks(currentDate, previousDate)
    }

    suspend fun getAllIncreasedStocks(currentDate: String, previousDate: String): List<StockChangeInfo> {
        return etfDao.getAllIncreasedStocks(currentDate, previousDate)
    }

    suspend fun getAllDecreasedStocks(currentDate: String, previousDate: String): List<StockChangeInfo> {
        return etfDao.getAllDecreasedStocks(currentDate, previousDate)
    }

    // ========== 종목 검색/분석 ==========

    suspend fun searchStocks(query: String): List<StockSearchResult> {
        return etfDao.searchStocks(query)
    }

    suspend fun analyzeStock(stockTicker: String): com.etfmonitor.database.entities.StockAnalysisResult? {
        return etfDao.analyzeStock(stockTicker)
    }

    // ========== 원화예금 추이 ==========

    suspend fun getCashDepositTrend(): List<CashDepositTrend> {
        return etfDao.getCashDepositTrend()
    }

    // ========== ETF 정보 ==========

    suspend fun getEtf(ticker: String) = etfDao.getEtf(ticker)
}
