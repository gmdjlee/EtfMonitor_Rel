package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toTimeSeriesDomain
import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.repository.StockTrendRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Trend Repository Implementation
 *
 * ETF 내 종목의 시계열 추이를 조회합니다.
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
@Singleton
class StockTrendRepositoryImpl @Inject constructor(
    private val localDataSource: StockStatisticsLocalDataSource
) : StockTrendRepository {

    override suspend fun getStockTrend(etfTicker: String, stockTicker: String): StockTrend? = withContext(Dispatchers.IO) {
        val timeSeries = localDataSource.getHoldingTimeSeries(etfTicker, stockTicker)

        if (timeSeries.isEmpty()) return@withContext null

        val firstDate = timeSeries.first().date
        val stockName = localDataSource.getHoldings(etfTicker, firstDate)
            .find { it.stockTicker == stockTicker }
            ?.stockName ?: stockTicker

        StockTrend(
            etfTicker = etfTicker,
            stockTicker = stockTicker,
            stockName = stockName,
            timeSeries = timeSeries.toTimeSeriesDomain()
        )
    }
}
