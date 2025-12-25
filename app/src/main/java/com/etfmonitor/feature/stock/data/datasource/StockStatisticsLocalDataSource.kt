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

    // ========== 종목 검색 ==========

    suspend fun searchStocks(query: String): List<StockSearchResult> {
        return etfDao.searchStocks(query)
    }

    // ========== 종목 분석용 데이터 ==========

    suspend fun getLatestTwoDates(): List<String> = etfDao.getLatestTwoDates()

    suspend fun getStockHoldingsByDate(stockTicker: String, date: String) =
        etfDao.getStockHoldingsByDate(stockTicker, date)

    suspend fun getStockName(stockTicker: String): String? = etfDao.getStockName(stockTicker)

    // ========== 원화예금 추이 ==========

    suspend fun getCashDepositTrend(): List<CashDepositTrend> {
        return etfDao.getCashDepositTrend()
    }

    // ========== 종목 통합 추이 ==========

    suspend fun getStockAggregatedTrend(stockTicker: String): List<StockAggregatedTimePoint> {
        return etfDao.getStockAggregatedTrend(stockTicker)
    }

    // ========== ETF 정보 ==========

    suspend fun getEtf(ticker: String) = etfDao.getEtf(ticker)
}
