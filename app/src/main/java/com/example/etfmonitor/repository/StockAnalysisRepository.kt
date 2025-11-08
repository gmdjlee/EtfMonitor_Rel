package com.etfmonitor.repository

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.StockAnalysisDao
import com.etfmonitor.database.entities.StockAnalysisData
import com.etfmonitor.oscillator.model.StockData
import com.etfmonitor.oscillator.python.OscillatorPyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StockAnalysisRepository(
    private val stockAnalysisDao: StockAnalysisDao,
    private val python: Python
) {
    companion object {
        private const val TAG = "StockAnalysisRepository"
        private const val DATA_EXPIRY_HOURS = 24 // 24시간 후 데이터 만료
    }

    private val pyClient by lazy {
        OscillatorPyClient(python)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 종목 분석 데이터 가져오기 (DB 캐시 활용)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 Python에서 가져옴
     */
    suspend fun getStockAnalysis(ticker: String, days: Int = 180): StockData? = withContext(Dispatchers.IO) {
        try {
            // 1. DB에서 기존 데이터 확인
            val cachedData = stockAnalysisDao.getAnalysisData(ticker)

            val today = dateFormat.format(Date())
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
                // Python 실패 시 캐시된 데이터라도 반환
                return@withContext if (cachedData != null) {
                    Log.d(TAG, "Returning stale cached data for $ticker")
                    convertToStockData(cachedData)
                } else {
                    null
                }
            }

            // 3. DB에 새 데이터 저장
            val analysisData = StockAnalysisData(
                ticker = ticker,
                name = stockData.name,
                dates = stockData.dates,
                marketCap = stockData.marketCap,
                foreign5d = stockData.foreign5d,
                institution5d = stockData.institution5d,
                lastUpdated = System.currentTimeMillis(),
                dataStartDate = stockData.dates.firstOrNull() ?: "",
                dataEndDate = stockData.dates.lastOrNull() ?: ""
            )

            stockAnalysisDao.insertAnalysisData(analysisData)
            Log.d(TAG, "Saved analysis data for $ticker to DB")

            stockData
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stock analysis for $ticker", e)
            null
        }
    }

    /**
     * 데이터 업데이트가 필요한지 확인
     */
    private fun shouldUpdateData(cachedData: StockAnalysisData?, today: String, requestedDays: Int): Boolean {
        if (cachedData == null) {
            return true // 데이터가 없으면 업데이트 필요
        }

        // 1. 데이터 만료 시간 확인 (24시간)
        val hoursSinceUpdate = (System.currentTimeMillis() - cachedData.lastUpdated) / (1000 * 60 * 60)
        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) {
            Log.d(TAG, "Data expired (${hoursSinceUpdate}h old)")
            return true
        }

        // 2. 데이터 종료일이 오늘이 아니면 업데이트 필요
        if (cachedData.dataEndDate != today) {
            Log.d(TAG, "Data end date (${cachedData.dataEndDate}) != today ($today)")
            return true
        }

        // 3. 요청된 기간보다 데이터가 부족하면 업데이트 필요
        if (cachedData.dates.size < requestedDays * 0.8) { // 80% 이상이면 OK
            Log.d(TAG, "Insufficient data points: ${cachedData.dates.size} < $requestedDays")
            return true
        }

        return false
    }

    /**
     * StockAnalysisData를 StockData로 변환
     */
    private fun convertToStockData(data: StockAnalysisData): StockData {
        return StockData(
            ticker = data.ticker,
            name = data.name,
            dates = data.dates,
            marketCap = data.marketCap,
            foreign5d = data.foreign5d,
            institution5d = data.institution5d
        )
    }

    /**
     * 특정 종목의 캐시된 데이터 삭제
     */
    suspend fun clearCache(ticker: String) {
        stockAnalysisDao.deleteAnalysisData(ticker)
        Log.d(TAG, "Cleared cache for $ticker")
    }

    /**
     * 모든 캐시 삭제
     */
    suspend fun clearAllCache() {
        stockAnalysisDao.deleteAll()
        Log.d(TAG, "Cleared all cache")
    }
}
