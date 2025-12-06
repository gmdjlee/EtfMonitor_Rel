package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.database.StockDao
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.oscillator.python.OscillatorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종목 마스터 Repository
 *
 * 역할:
 * - 전체 종목 관리 (stocks 테이블)
 * - ETF 보유 종목 자동 동기화
 * - 종목명 조회 (JOIN 대체용)
 */
@Singleton
class StockRepository @Inject constructor(
    private val stockDao: StockDao,
    private val pyClient: OscillatorPyClient
) {
    companion object {
        private const val TAG = "StockRepository"
    }

    // ========== 조회 ==========

    fun getAllStocks(): Flow<List<Stock>> = stockDao.getAllStocks().flowOn(Dispatchers.IO)

    fun searchStocks(query: String): Flow<List<Stock>> = stockDao.searchStocks(query).flowOn(Dispatchers.IO)

    fun getEtfHoldingStocks(): Flow<List<Stock>> = stockDao.getEtfHoldingStocks().flowOn(Dispatchers.IO)

    fun getStocksByMarket(market: String): Flow<List<Stock>> = stockDao.getStocksByMarket(market).flowOn(Dispatchers.IO)

    suspend fun getStock(ticker: String): Stock? = withContext(Dispatchers.IO) {
        stockDao.getStock(ticker)
    }

    suspend fun getStockName(ticker: String): String = withContext(Dispatchers.IO) {
        stockDao.getStockName(ticker) ?: ticker
    }

    suspend fun getStockCount(): Int = stockDao.getCount()

    suspend fun getEtfHoldingCount(): Int = stockDao.getEtfHoldingCount()

    suspend fun getLastUpdateTime(): Long? = stockDao.getLastUpdateTime()

    // ========== ETF 보유 종목 동기화 ==========

    /**
     * 단일 종목 동기화 (ETF 보유 종목에서 호출)
     */
    suspend fun syncFromHolding(ticker: String, name: String) = withContext(Dispatchers.IO) {
        try {
            val market = Stock.inferMarket(ticker)
            stockDao.upsertFromHolding(ticker, name, market, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync stock: $ticker", e)
        }
    }

    /**
     * 일괄 종목 동기화 (ETF 데이터 수집 후 호출)
     */
    suspend fun syncFromHoldings(holdings: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        try {
            if (holdings.isEmpty()) return@withContext
            stockDao.syncFromHoldings(holdings)
            Log.d(TAG, "Synced ${holdings.size} stocks from holdings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync stocks from holdings", e)
        }
    }

    // ========== 전체 종목 초기화 ==========

    /**
     * 네트워크 오류를 나타내는 예외
     */
    class NetworkException(message: String) : Exception(message)

    /**
     * 종목 데이터 초기화 (Python에서 가져와서 DB에 저장)
     * @return Result.success(종목 수) 또는 Result.failure(NetworkException 또는 Exception)
     */
    suspend fun initializeStocks(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing stock data from Python...")

            val stockList = pyClient.getAllStocksList()

            if (stockList.isEmpty()) {
                Log.e(TAG, "Failed to get stocks list from Python (empty result - possible network issue)")
                return@withContext Result.failure(
                    NetworkException("종목 데이터를 가져올 수 없습니다. 네트워크 연결을 확인해 주세요.")
                )
            }

            val stocks = stockList.map { (ticker, name) ->
                Stock(
                    ticker = ticker,
                    name = name,
                    market = Stock.inferMarket(ticker),
                    lastUpdated = System.currentTimeMillis()
                )
            }

            stockDao.deleteAll()
            stockDao.insertAll(stocks)

            Log.d(TAG, "Successfully initialized ${stocks.size} stocks")
            Result.success(stocks.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing stocks", e)
            Result.failure(e)
        }
    }

    /**
     * 종목 데이터 업데이트
     */
    suspend fun updateStocks(): Result<Int> = initializeStocks()
}
