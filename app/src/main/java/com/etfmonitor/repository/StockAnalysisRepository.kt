package com.etfmonitor.repository

import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.entities.StockAnalysisData
import com.etfmonitor.core.database.entities.StockAnalysisWithName
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.core.common.util.DateFormatter
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
        private val logger = AppLogger.getLogger("StockAnalysisRepo")
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
                logger.d( "Using cached data for $ticker")
                return@withContext convertToStockData(cachedData)
            }

            // 2. Python에서 새 데이터 가져오기
            logger.d( "Fetching new data for $ticker (days: $days)")
            val stockData = pyClient.getStockAnalysis(ticker, days)

            if (stockData == null) {
                logger.e( "Failed to fetch data from Python for $ticker")
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
                market = com.etfmonitor.core.database.entities.Stock.inferMarket(ticker),
                lastUpdated = System.currentTimeMillis()
            )

            logger.d( "Saved analysis data for $ticker")
            stockData
        } catch (e: Exception) {
            logger.e( "Error getting stock analysis for $ticker", e)
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
        logger.d( "Cleared cache for $ticker")
    }

    suspend fun clearAllCache() {
        stockAnalysisDao.deleteAll()
        logger.d( "Cleared all cache")
    }
}
