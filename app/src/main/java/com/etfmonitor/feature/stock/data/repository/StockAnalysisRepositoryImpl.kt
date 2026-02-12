package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.feature.stock.data.datasource.StockAnalysisLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.network.krx.StockDataClient
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.entities.StockAnalysisData
import com.etfmonitor.core.database.entities.StockAnalysisWithName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Analysis Repository Implementation
 *
 * 종목 수급 분석 데이터를 관리합니다.
 *
 * ## 주요 기능
 * - 종목 분석 데이터 조회 (24시간 캐싱)
 * - KRX에서 새 데이터 수집
 * - stocks 테이블과 JOIN하여 종목명 조회
 *
 * ## 캐싱 정책
 * - 데이터 만료 시간: 24시간
 * - 최신 날짜가 오늘이 아니면 업데이트
 * - 데이터가 요청 일수의 80% 미만이면 업데이트
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
@Singleton
class StockAnalysisRepositoryImpl @Inject constructor(
    private val analysisLocalDataSource: StockAnalysisLocalDataSource,
    private val stockLocalDataSource: StockLocalDataSource,
    private val stockDataClient: StockDataClient
) : StockAnalysisRepository {

    companion object {
        private val logger = AppLogger.getLogger("StockAnalysisRepoImpl")
        private const val DATA_EXPIRY_HOURS = 24
    }

    override suspend fun getStockAnalysis(ticker: String, days: Int): StockData? = withContext(Dispatchers.IO) {
        try {
            // 1. DB에서 기존 데이터 확인 (JOIN으로 name 포함)
            val cachedData = analysisLocalDataSource.getAnalysisDataWithName(ticker)

            val today = DateFormatter.formatToday()
            val shouldUpdate = shouldUpdateData(cachedData, today, days)

            if (!shouldUpdate && cachedData != null) {
                logger.d("Using cached data for $ticker")
                return@withContext cachedData.toStockData()
            }

            // 2. KRX에서 새 데이터 가져오기
            logger.d("Fetching new data for $ticker (days: $days)")
            val stockData = stockDataClient.getStockAnalysis(ticker, days)

            if (stockData == null) {
                logger.e("Failed to fetch data from KRX for $ticker")
                return@withContext cachedData?.toStockData()
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

            analysisLocalDataSource.insertAnalysisData(analysisData)

            // 4. stocks 마스터에도 동기화
            stockLocalDataSource.upsertFromHolding(
                ticker = ticker,
                name = stockData.name,
                market = Stock.inferMarket(ticker),
                lastUpdated = System.currentTimeMillis()
            )

            logger.d("Saved analysis data for $ticker")
            stockData
        } catch (e: Exception) {
            logger.e("Error getting stock analysis for $ticker", e)
            null
        }
    }

    /**
     * StockAnalysisWithName을 StockData로 변환
     */
    private fun StockAnalysisWithName.toStockData(): StockData = StockData(
        ticker = ticker,
        name = name,
        dates = dates,
        marketCap = marketCap,
        foreign5d = foreign5d,
        institution5d = institution5d
    )

    private fun shouldUpdateData(
        cachedData: com.etfmonitor.core.database.entities.StockAnalysisWithName?,
        today: String,
        requestedDays: Int
    ): Boolean {
        if (cachedData == null) return true

        val hoursSinceUpdate = (System.currentTimeMillis() - cachedData.lastUpdated) / (1000 * 60 * 60)
        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) return true
        if (cachedData.dataEndDate != today) return true
        if (cachedData.dates.size < requestedDays * 0.8) return true

        return false
    }

    override suspend fun clearCache(ticker: String) = withContext(Dispatchers.IO) {
        analysisLocalDataSource.deleteAnalysisData(ticker)
        logger.d("Cleared cache for $ticker")
    }

    override suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        analysisLocalDataSource.deleteAll()
        logger.d("Cleared all cache")
    }
}
