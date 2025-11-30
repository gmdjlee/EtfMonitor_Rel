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
 * 주식 종목 Repository
 *
 * Production 최적화:
 * - @Singleton: Hilt가 단일 인스턴스 관리
 * - @Inject: 생성자 주입으로 의존성 명확화
 * - flowOn(Dispatchers.IO): Flow 메서드에 명시적 디스패처 지정
 */
@Singleton
class StockRepository @Inject constructor(
    private val stockDao: StockDao,
    private val pyClient: OscillatorPyClient
) {
    companion object {
        private const val TAG = "StockRepository"
    }

    fun getAllStocks(): Flow<List<Stock>> = stockDao.getAllStocks().flowOn(Dispatchers.IO)

    fun searchStocks(query: String): Flow<List<Stock>> = stockDao.searchStocks(query).flowOn(Dispatchers.IO)

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
            val stockList = pyClient.getAllStocksList()

            if (stockList.isEmpty()) {
                Log.e(TAG, "Failed to get stocks list from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패"))
            }

            // List<Pair<String, String>>를 Stock 엔티티로 변환
            val stocks = stockList.map { (ticker, name) ->
                // market 정보는 ticker 번호로 추정
                // KOSPI: 6자리 숫자가 대부분 0으로 시작
                // KOSDAQ: 대부분 A로 시작하거나 다른 패턴
                val market = when {
                    ticker.startsWith("0") || ticker.startsWith("1") || ticker.startsWith("2") -> "KOSPI"
                    else -> "KOSDAQ"
                }

                Stock(
                    ticker = ticker,
                    name = name,
                    market = market,
                    lastUpdated = System.currentTimeMillis()
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
