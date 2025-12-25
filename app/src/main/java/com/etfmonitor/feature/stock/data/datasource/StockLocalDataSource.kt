package com.etfmonitor.feature.stock.data.datasource

import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.entities.Stock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Local Data Source
 *
 * 종목 마스터 데이터에 대한 로컬 데이터 접근을 담당합니다.
 * StockDao를 래핑하여 데이터 레이어에서 사용합니다.
 *
 * @property stockDao Stock DAO
 */
@Singleton
class StockLocalDataSource @Inject constructor(
    private val stockDao: StockDao
) {
    // ========== 조회 ==========

    fun getAllStocks(): Flow<List<Stock>> = stockDao.getAllStocks()

    fun searchStocks(query: String): Flow<List<Stock>> = stockDao.searchStocks(query)

    fun getEtfHoldingStocks(): Flow<List<Stock>> = stockDao.getEtfHoldingStocks()

    fun getStocksByMarket(market: String): Flow<List<Stock>> = stockDao.getStocksByMarket(market)

    suspend fun getStock(ticker: String): Stock? = stockDao.getStock(ticker)

    suspend fun getStockName(ticker: String): String? = stockDao.getStockName(ticker)

    suspend fun getCount(): Int = stockDao.getCount()

    suspend fun getEtfHoldingCount(): Int = stockDao.getEtfHoldingCount()

    suspend fun getLastUpdateTime(): Long? = stockDao.getLastUpdateTime()

    // ========== 동기화 ==========

    suspend fun upsertFromHolding(ticker: String, name: String, market: String, lastUpdated: Long) {
        stockDao.upsertFromHolding(ticker, name, market, lastUpdated)
    }

    suspend fun syncFromHoldings(holdings: List<Pair<String, String>>) {
        stockDao.syncFromHoldings(holdings)
    }

    // ========== 삽입/삭제 ==========

    suspend fun insertAll(stocks: List<Stock>) {
        stockDao.insertAll(stocks)
    }

    suspend fun insert(stock: Stock) {
        stockDao.insert(stock)
    }

    suspend fun deleteAll() {
        stockDao.deleteAll()
    }
}
