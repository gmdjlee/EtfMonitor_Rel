package com.etfmonitor.feature.stock.data.datasource

import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.HoldingTimeSeries
import com.etfmonitor.core.database.entities.StockAmountRanking
import com.etfmonitor.core.database.entities.StockChangeInfo
import com.etfmonitor.core.database.entities.CashDepositTrend
import com.etfmonitor.core.database.entities.StockAggregatedTimePoint
import com.etfmonitor.core.database.StockSearchResult
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

    suspend fun getStockAmountRanking(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockAmountRanking> {
        return etfDao.getStockAmountRanking(currentDate, previousDate, visibleEtfTickers)
    }

    // ========== 종목 변화 ==========

    suspend fun getAllNewStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo> {
        return etfDao.getAllNewStocks(currentDate, previousDate, visibleEtfTickers)
    }

    suspend fun getAllRemovedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo> {
        return etfDao.getAllRemovedStocks(currentDate, previousDate, visibleEtfTickers)
    }

    suspend fun getAllIncreasedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo> {
        return etfDao.getAllIncreasedStocks(currentDate, previousDate, visibleEtfTickers)
    }

    suspend fun getAllDecreasedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo> {
        return etfDao.getAllDecreasedStocks(currentDate, previousDate, visibleEtfTickers)
    }

    // ========== 종목 검색 ==========

    suspend fun searchStocks(query: String, visibleEtfTickers: List<String>): List<StockSearchResult> {
        return etfDao.searchStocks(query, visibleEtfTickers)
    }

    // ========== 종목 분석용 데이터 ==========

    suspend fun getLatestTwoDates(): List<String> = etfDao.getLatestTwoDates()

    suspend fun getStockHoldingsByDate(stockTicker: String, date: String, visibleEtfTickers: List<String>) =
        etfDao.getStockHoldingsByDate(stockTicker, date, visibleEtfTickers)

    suspend fun getStockName(stockTicker: String): String? = etfDao.getStockName(stockTicker)

    // ========== 원화예금 추이 ==========

    suspend fun getCashDepositTrend(visibleEtfTickers: List<String>): List<CashDepositTrend> {
        return etfDao.getCashDepositTrend(visibleEtfTickers)
    }

    // ========== 종목 통합 추이 ==========

    suspend fun getStockAggregatedTrend(stockTicker: String, visibleEtfTickers: List<String>): List<StockAggregatedTimePoint> {
        return etfDao.getStockAggregatedTrend(stockTicker, visibleEtfTickers)
    }

    // ========== ETF 정보 ==========

    suspend fun getEtf(ticker: String) = etfDao.getEtf(ticker)

    // ========== 날짜 목록 ==========

    suspend fun getAllDistinctDates(limit: Int = 100): List<String> = etfDao.getAllDistinctDates(limit)
}
