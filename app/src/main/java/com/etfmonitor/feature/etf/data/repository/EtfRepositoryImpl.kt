package com.etfmonitor.feature.etf.data.repository

import com.etfmonitor.feature.etf.data.datasource.EtfLocalDataSource
import com.etfmonitor.feature.etf.data.mapper.EtfMapper.toDomain
import com.etfmonitor.feature.etf.domain.model.ComparisonResult
import com.etfmonitor.feature.etf.domain.model.DataStatus
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ETF Repository Implementation
 *
 * Domain 레이어의 EtfRepository 인터페이스를 구현합니다.
 * EtfLocalDataSource로부터 Entity를 받아 Domain Model로 변환합니다.
 *
 * ## 성능 최적화
 * - 모든 Flow는 flowOn(Dispatchers.IO)로 실행
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 실행
 *
 * ## 비교 분석 로직
 * - 최근 2일간의 보유 종목을 비교하여 상태(NEW/INCREASE/DECREASE/MAINTAIN/REMOVED) 결정
 * - 변화량 임계값: 0.01% (WEIGHT_CHANGE_THRESHOLD)
 */
@Singleton
class EtfRepositoryImpl @Inject constructor(
    private val localDataSource: EtfLocalDataSource
) : EtfRepository {

    companion object {
        private val logger = AppLogger.getLogger("EtfRepositoryImpl")
        // Holding weight change threshold for status determination (in percentage points)
        private const val WEIGHT_CHANGE_THRESHOLD = 0.01f
    }

    // ========== ETF List ==========

    override fun getAllEtfs(): Flow<List<Etf>> =
        localDataSource.getAllEtfs()
            .map { entities -> entities.toDomain() }
            .flowOn(Dispatchers.IO)

    override fun searchEtfs(query: String): Flow<List<Etf>> =
        localDataSource.searchEtfs(query)
            .map { entities -> entities.toDomain() }
            .flowOn(Dispatchers.IO)

    // ========== Data Status ==========

    override suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        val count = localDataSource.getEtfCount()
        logger.d("hasData: count = $count")
        count > 0
    }

    override suspend fun getDataStatus(): DataStatus = withContext(Dispatchers.IO) {
        val count = localDataSource.getEtfCount()
        val latestDate = localDataSource.getLatestDate()
        DataStatus(
            hasData = count > 0,
            latestDate = latestDate
        )
    }

    override suspend fun getLatestDate(): String? = withContext(Dispatchers.IO) {
        val date = localDataSource.getLatestDate()
        logger.d("getLatestDate: $date")
        date
    }

    // ========== ETF Detail ==========

    override suspend fun getEtf(ticker: String): Etf? = withContext(Dispatchers.IO) {
        localDataSource.getEtf(ticker)?.toDomain()
    }

    override suspend fun getComparison(etfTicker: String): ComparisonResult? = withContext(Dispatchers.IO) {
        val dates = localDataSource.getDates(etfTicker)

        logger.d("getComparison for $etfTicker: ${dates.size} dates available")

        if (dates.isEmpty()) {
            logger.d("No dates found for $etfTicker")
            return@withContext null
        }

        if (dates.size == 1) {
            logger.d("Only one date available: ${dates[0]}")
            val current = localDataSource.getHoldings(etfTicker, dates[0])
            return@withContext ComparisonResult(
                etfTicker = etfTicker,
                currentDate = dates[0],
                previousDate = "N/A",
                items = current.map { holding ->
                    HoldingWithComparison(
                        stockTicker = holding.stockTicker,
                        stockName = holding.stockName,
                        previousWeight = 0f,
                        currentWeight = holding.weight,
                        change = holding.weight,
                        currentAmount = holding.amount,
                        status = HoldingStatus.NEW
                    )
                },
                collectionStartDate = dates[0],
                collectionEndDate = dates[0]
            )
        }

        val currentDate = dates[0]
        val previousDate = dates[1]

        logger.d("Comparing: $previousDate vs $currentDate")

        val current = localDataSource.getHoldings(etfTicker, currentDate)
        val previous = localDataSource.getHoldings(etfTicker, previousDate)

        logger.d("Current holdings: ${current.size}, Previous holdings: ${previous.size}")

        val currentMap = current.associateBy { it.stockTicker }
        val previousMap = previous.associateBy { it.stockTicker }

        val allTickers = (currentMap.keys + previousMap.keys).toSet()

        val items = allTickers.map { ticker ->
            val curr = currentMap[ticker]
            val prev = previousMap[ticker]

            when {
                curr != null && prev == null -> {
                    logger.d("NEW: ${curr.stockName}")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = 0f,
                        currentWeight = curr.weight,
                        change = curr.weight,
                        currentAmount = curr.amount,
                        status = HoldingStatus.NEW
                    )
                }
                curr == null && prev != null -> {
                    logger.d("REMOVED: ${prev.stockName}")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = prev.stockName,
                        previousWeight = prev.weight,
                        currentWeight = 0f,
                        change = -prev.weight,
                        currentAmount = 0f,
                        status = HoldingStatus.REMOVED
                    )
                }
                curr != null && prev != null -> {
                    val prevWeight = prev.weight
                    val currWeight = curr.weight
                    val change = currWeight - prevWeight

                    val status = when {
                        change > WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.INCREASE
                        change < -WEIGHT_CHANGE_THRESHOLD -> HoldingStatus.DECREASE
                        else -> HoldingStatus.MAINTAIN
                    }

                    if (status != HoldingStatus.MAINTAIN) {
                        logger.d("${status.name}: ${curr.stockName} ($prevWeight% → $currWeight%)")
                    }

                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr.stockName,
                        previousWeight = prevWeight,
                        currentWeight = currWeight,
                        change = change,
                        currentAmount = curr.amount,
                        status = status
                    )
                }
                else -> {
                    logger.e("Unexpected case for ticker: $ticker")
                    HoldingWithComparison(
                        stockTicker = ticker,
                        stockName = curr?.stockName ?: prev?.stockName ?: ticker,
                        previousWeight = 0f,
                        currentWeight = 0f,
                        change = 0f,
                        currentAmount = 0f,
                        status = HoldingStatus.MAINTAIN
                    )
                }
            }
        }
            .sortedWith(
                compareByDescending<HoldingWithComparison> { it.status == HoldingStatus.NEW }
                    .thenByDescending { it.status == HoldingStatus.REMOVED }
                    .thenByDescending { it.currentWeight }
            )

        logger.d("Comparison result: ${items.size} items")
        val statusCount = items.groupBy { it.status }.mapValues { it.value.size }
        logger.d("Status counts: $statusCount")

        ComparisonResult(
            etfTicker = etfTicker,
            currentDate = currentDate,
            previousDate = previousDate,
            items = items,
            collectionStartDate = dates.last(),  // 가장 오래된 날짜
            collectionEndDate = dates.first()    // 가장 최신 날짜
        )
    }
}
