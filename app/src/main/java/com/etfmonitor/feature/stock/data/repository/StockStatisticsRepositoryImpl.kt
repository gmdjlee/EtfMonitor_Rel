package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toRankingDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toChangeInfoDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toCashDepositDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toSearchResultDomain
import com.etfmonitor.core.database.entities.HoldingStatus
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTrend
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTimePoint
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.model.StockEtfDetail
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Statistics Repository Implementation
 *
 * 종목 통계 데이터를 관리합니다.
 *
 * ## 주요 기능
 * - 종목 금액순위 조회
 * - 신규/제외/비중변화 종목 조회
 * - 종목 분석 (ETF별 보유 분석)
 * - 원화예금 추이 조회
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
@Singleton
class StockStatisticsRepositoryImpl @Inject constructor(
    private val localDataSource: StockStatisticsLocalDataSource,
    private val etfDao: com.etfmonitor.core.database.EtfDao
) : StockStatisticsRepository {

    companion object {
        private val logger = AppLogger.getLogger("StockStatisticsRepoImpl")
    }

    // One-shot flag: normalize yyyyMMdd → yyyy-MM-dd in holdings table (Critical Rule #10)
    @Volatile
    private var dateFormatNormalized = false

    private suspend fun ensureDateFormatNormalized() {
        if (!dateFormatNormalized) {
            etfDao.normalizeDateFormat()
            dateFormatNormalized = true
        }
    }

    private suspend fun getVisibleEtfTickers(etfNameFilter: String? = null): List<String> {
        val themesStr = etfDao.getSetting("include_themes") ?: ""
        val exclusionsStr = etfDao.getSetting("exclude_keywords") ?: ""
        val themes = if (themesStr.isBlank()) emptyList() else themesStr.split(",").map { it.trim() }
        val exclusions = if (exclusionsStr.isBlank()) emptyList() else exclusionsStr.split(",").map { it.trim() }
        val includeKeywords = themes + listOf("액티브")

        val allEtfs = etfDao.getAllEtfsSuspend()
        return allEtfs.filter { etf ->
            val includeMatch = includeKeywords.any { kw ->
                etf.name.contains(kw, ignoreCase = true)
            }
            val excludeMatch = exclusions.any { kw ->
                etf.name.contains(kw, ignoreCase = true)
            }
            val categoryMatch = etfNameFilter?.let {
                etf.name.contains(it, ignoreCase = true)
            } ?: true
            includeMatch && !excludeMatch && categoryMatch
        }.map { it.ticker }
    }

    // ========== 통계 날짜 ==========

    override suspend fun getStatisticsDates(): Pair<String, String>? = withContext(Dispatchers.IO) {
        ensureDateFormatNormalized()
        // holdings 테이블에서 직접 최근 2개 날짜 가져오기
        val dates = localDataSource.getLatestTwoDates()
        if (dates.size < 2) {
            logger.d("getStatisticsDates: Not enough dates (${dates.size})")
            return@withContext null
        }

        logger.d("getStatisticsDates: currentDate=${dates[0]}, previousDate=${dates[1]}")
        Pair(dates[0], dates[1])
    }

    override suspend fun getAvailableDates(limit: Int): List<String> = withContext(Dispatchers.IO) {
        localDataSource.getAllDistinctDates(limit)
    }

    override suspend fun getStatisticsDatesInRange(
        startDate: String,
        endDate: String
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        ensureDateFormatNormalized()
        val allDates = localDataSource.getAllDistinctDates(500)
        val datesInRange = allDates.filter { it in startDate..endDate }

        if (datesInRange.size < 2) {
            logger.d("getStatisticsDatesInRange: Not enough dates in range (${datesInRange.size})")
            // 범위 내 날짜가 1개만 있으면 그 날짜를 양쪽에 사용
            if (datesInRange.size == 1) {
                return@withContext Pair(datesInRange[0], datesInRange[0])
            }
            return@withContext null
        }

        // 내림차순 정렬되어 있으므로 first가 최신, last가 가장 오래된 날짜
        logger.d("getStatisticsDatesInRange: currentDate=${datesInRange.first()}, previousDate=${datesInRange.last()}")
        Pair(datesInRange.first(), datesInRange.last())
    }

    // ========== 금액순위 ==========

    override suspend fun getStockAmountRanking(): List<StockAmountRanking> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.getStockAmountRanking(dates.first, dates.second, visibleTickers).toRankingDomain()
    }

    override suspend fun getStockAmountRankingInRange(
        currentDate: String,
        previousDate: String,
        etfNameFilter: String?
    ): List<StockAmountRanking> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getStockAmountRanking(currentDate, previousDate, visibleTickers).toRankingDomain()
    }

    // ========== 종목 변화 ==========

    override suspend fun getAllNewStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.getAllNewStocks(dates.first, dates.second, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllNewStocksInRange(
        currentDate: String,
        previousDate: String,
        etfNameFilter: String?
    ): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getAllNewStocks(currentDate, previousDate, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllRemovedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.getAllRemovedStocks(dates.first, dates.second, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllRemovedStocksInRange(
        currentDate: String,
        previousDate: String,
        etfNameFilter: String?
    ): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getAllRemovedStocks(currentDate, previousDate, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllIncreasedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.getAllIncreasedStocks(dates.first, dates.second, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllIncreasedStocksInRange(
        currentDate: String,
        previousDate: String,
        etfNameFilter: String?
    ): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getAllIncreasedStocks(currentDate, previousDate, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllDecreasedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.getAllDecreasedStocks(dates.first, dates.second, visibleTickers).toChangeInfoDomain()
    }

    override suspend fun getAllDecreasedStocksInRange(
        currentDate: String,
        previousDate: String,
        etfNameFilter: String?
    ): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getAllDecreasedStocks(currentDate, previousDate, visibleTickers).toChangeInfoDomain()
    }

    // ========== 종목 분석 ==========

    override suspend fun searchStocks(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers()
        localDataSource.searchStocks(query, visibleTickers).toSearchResultDomain()
    }

    override suspend fun analyzeStock(stockTicker: String): StockAnalysisResult? = withContext(Dispatchers.IO) {
        val dates = localDataSource.getLatestTwoDates()
        if (dates.isEmpty()) return@withContext null

        val currentDate = dates[0]
        val previousDate = dates.getOrNull(1)
        val visibleTickers = getVisibleEtfTickers()

        // 현재 보유 현황
        val currentHoldings = localDataSource.getStockHoldingsByDate(stockTicker, currentDate, visibleTickers)
        if (currentHoldings.isEmpty()) return@withContext null

        // 이전 보유 현황 (있는 경우)
        val previousHoldings = previousDate?.let {
            localDataSource.getStockHoldingsByDate(stockTicker, it, visibleTickers)
        } ?: emptyList()

        val previousHoldingsMap = previousHoldings.associateBy { it.etfTicker }

        val stockName = localDataSource.getStockName(stockTicker) ?: stockTicker

        // 카운터
        var newIncludedCount = 0
        var increasedCount = 0
        var decreasedCount = 0

        // ETF별 상세 정보 생성 (with status)
        val etfDetails = currentHoldings.map { holding ->
            val previous = previousHoldingsMap[holding.etfTicker]
            val previousWeight = previous?.weight ?: 0f
            val currentWeight = holding.weight
            val change = currentWeight - previousWeight

            val status = when {
                previous == null -> {
                    newIncludedCount++
                    HoldingStatus.NEW
                }
                change > 0.01f -> {
                    increasedCount++
                    HoldingStatus.INCREASE
                }
                change < -0.01f -> {
                    decreasedCount++
                    HoldingStatus.DECREASE
                }
                else -> HoldingStatus.MAINTAIN
            }

            StockEtfDetail(
                etfTicker = holding.etfTicker,
                etfName = holding.etfName,
                previousWeight = previousWeight,
                currentWeight = currentWeight,
                change = change,
                amount = holding.amount,
                status = status
            )
        }.sortedByDescending { it.amount }

        // 제외된 ETF 카운트 (이전에 있었지만 현재에 없는)
        val currentEtfTickers = currentHoldings.map { it.etfTicker }.toSet()
        val removedCount = previousHoldings.count { it.etfTicker !in currentEtfTickers }

        // 통계 계산
        val totalAmount = currentHoldings.sumOf { it.amount.toDouble() }.toFloat()
        val weights = currentHoldings.map { it.weight }
        val avgWeight = if (weights.isNotEmpty()) weights.average().toFloat() else 0f
        val maxWeight = weights.maxOrNull() ?: 0f

        StockAnalysisResult(
            stockTicker = stockTicker,
            stockName = stockName,
            etfDetails = etfDetails,
            totalAmount = totalAmount,
            currentEtfCount = currentHoldings.size,
            previousEtfCount = previousHoldings.size,
            increasedCount = increasedCount,
            decreasedCount = decreasedCount,
            newIncludedCount = newIncludedCount,
            removedCount = removedCount,
            avgWeight = avgWeight,
            maxWeight = maxWeight
        )
    }

    // ========== 원화예금 추이 ==========

    override suspend fun getCashDepositTrend(etfNameFilter: String?): List<CashDepositTrend> = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers(etfNameFilter)
        localDataSource.getCashDepositTrend(visibleTickers).toCashDepositDomain()
    }

    // ========== 종목 통합 추이 ==========

    override suspend fun getStockAggregatedTrend(stockTicker: String): StockAggregatedTrend? = withContext(Dispatchers.IO) {
        val visibleTickers = getVisibleEtfTickers()
        val timeSeries = localDataSource.getStockAggregatedTrend(stockTicker, visibleTickers)
        if (timeSeries.isEmpty()) return@withContext null

        val stockName = localDataSource.getStockName(stockTicker) ?: stockTicker

        StockAggregatedTrend(
            stockTicker = stockTicker,
            stockName = stockName,
            timeSeries = timeSeries.map { entity ->
                StockAggregatedTimePoint(
                    date = entity.date,
                    totalAmount = entity.totalAmount,
                    etfCount = entity.etfCount,
                    maxWeight = entity.maxWeight,
                    avgWeight = entity.avgWeight
                )
            }
        )
    }
}
