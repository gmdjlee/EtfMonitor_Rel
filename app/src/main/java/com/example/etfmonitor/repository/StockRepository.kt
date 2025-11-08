package com.etfmonitor.repository

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.StockDao
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.oscillator.python.OscillatorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class StockRepository(
    private val stockDao: StockDao,
    private val python: Python
) {
    companion object {
        private const val TAG = "StockRepository"
    }

    private val pyClient by lazy {
        OscillatorPyClient(python)
    }

    fun getAllStocks(): Flow<List<Stock>> = stockDao.getAllStocks()

    fun searchStocks(query: String): Flow<List<Stock>> = stockDao.searchStocks(query)

    suspend fun getStock(ticker: String): Stock? = stockDao.getStock(ticker)

    suspend fun getStockCount(): Int = stockDao.getCount()

    suspend fun getLastUpdateTime(): Long? = stockDao.getLastUpdateTime()

    /**
     * 종목 데이터 초기화 (Python에서 가져와서 DB에 저장)
     */
    suspend fun initializeStocks(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing stock data from Python...")

            // Python에서 전체 종목 리스트 가져오기
            val result = pyClient.getAllStocksList()

            if (result == null) {
                Log.e(TAG, "Failed to get stocks list from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패"))
            }

            // JSON 파싱
            val jsonArray = JSONArray(result)
            val stocks = mutableListOf<Stock>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val ticker = obj.getString("ticker")
                val name = obj.getString("name")

                // market 정보는 ticker 번호로 추정
                // KOSPI: 6자리 숫자가 대부분 0으로 시작
                // KOSDAQ: 대부분 A로 시작하거나 다른 패턴
                val market = when {
                    ticker.startsWith("0") || ticker.startsWith("1") || ticker.startsWith("2") -> "KOSPI"
                    else -> "KOSDAQ"
                }

                stocks.add(
                    Stock(
                        ticker = ticker,
                        name = name,
                        market = market,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }

            // DB에 일괄 저장
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
    suspend fun updateStocks(): Result<Int> {
        return initializeStocks() // 전체 갱신
    }
}
