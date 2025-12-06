package com.etfmonitor.repository

import android.util.Log
import com.etfmonitor.database.StockAnalysisDao
import com.etfmonitor.database.StockDao
import com.etfmonitor.database.entities.StockAnalysisData
import com.etfmonitor.database.entities.StockAnalysisWithName
import com.etfmonitor.oscillator.model.StockData
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.utils.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종목 수급 분석 Repository
 *
 * stocks 테이블과 JOIN하여 종목명 조회
 */
@Singleton
class StockAnalysisRepository @Inject constructor(
    private val stockAnalysisDao: StockAnalysisDao,
    private val stockDao: StockDao,
    private val pyClient: OscillatorPyClient
) {
    companion object {
        private const val TAG = "StockAnalysisRepository"
        private const val DATA_EXPIRY_HOURS = 24
    }

    /**
     * 종목 분석 데이터 가져오기 (DB 캐시 활용)
     * stocks JOIN으로 종목명 조회
     */
    suspend fun getStockAnalysis(ticker: String, days: Int = 180): StockData? = withContext(Dispatchers.IO) {
        try {
            // 1. DB에서 기존 데이터 확인 (JOIN으로 name 포함)
            val cachedData = stockAnalysisDao.getAnalysisDataWithName(ticker)

            val today = DateFormatter.formatToday()
            val shouldUpdate = shouldUpdateData(cachedData, today, days)

            if (!shouldUpdate && cachedData != null) {
                Log.d(TAG, "Using cached data for $ticker")
                return@withContext convertToStockData(cachedData)
            }

            // 2. Python에서 새 데이터 가져오기
            Log.d(TAG, "Fetching new data for $ticker (days: $days)")
            val stockData = pyClient.getStockAnalysis(ticker, days)

            if (stockData == null) {
                Log.e(TAG, "Failed to fetch data from Python for $ticker")
                return@withContext cachedData?.let { convertToStockData(it) }
            }

            // 3. DB에 새 데이터 저장 (name 제외)
            val analysisData = StockAnalysisData(
                ticker = ticker,
                dates = stockData.dates,
                marketCap = stockData.marketCap,
                foreign5d = stockData.foreign5d,
                institution5d = stockData.institution5d,
                lastUpdated = System.currentTimeMillis(),
                dataStartDate = stockData.dates.firstOrNull() ?: "",
                dataEndDate = stockData.dates.lastOrNull() ?: ""
            )

            stockAnalysisDao.insertAnalysisData(analysisData)

            // 4. stocks 마스터에도 동기화
            stockDao.upsertFromHolding(
                ticker = ticker,
                name = stockData.name,
                market = com.etfmonitor.database.entities.Stock.inferMarket(ticker),
                lastUpdated = System.currentTimeMillis()
            )

            Log.d(TAG, "Saved analysis data for $ticker")
            stockData
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stock analysis for $ticker", e)
            null
        }
    }

    private fun shouldUpdateData(cachedData: StockAnalysisWithName?, today: String, requestedDays: Int): Boolean {
        if (cachedData == null) return true

        val hoursSinceUpdate = (System.currentTimeMillis() - cachedData.lastUpdated) / (1000 * 60 * 60)
        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) return true
        if (cachedData.dataEndDate != today) return true
        if (cachedData.dates.size < requestedDays * 0.8) return true

        return false
    }

    private fun convertToStockData(data: StockAnalysisWithName): StockData {
        return StockData(
            ticker = data.ticker,
            name = data.name,
            dates = data.dates,
            marketCap = data.marketCap,
            foreign5d = data.foreign5d,
            institution5d = data.institution5d
        )
    }

    suspend fun clearCache(ticker: String) {
        stockAnalysisDao.deleteAnalysisData(ticker)
        Log.d(TAG, "Cleared cache for $ticker")
    }

    suspend fun clearAllCache() {
        stockAnalysisDao.deleteAll()
        Log.d(TAG, "Cleared all cache")
    }
}
