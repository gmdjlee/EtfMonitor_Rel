package com.etfmonitor.feature.analysis.data.repository

import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.feature.analysis.domain.repository.CorrelationData
import com.etfmonitor.feature.analysis.domain.repository.StatisticsAnalysisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 통계 분석 Repository 구현체
 * ETF 통계 계산, 상관관계 분석
 */
@Singleton
class StatisticsAnalysisRepositoryImpl @Inject constructor(
    private val etfDao: EtfDao,
    private val marketIndexDao: MarketIndexDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao
) : StatisticsAnalysisRepository {

    companion object {
        private val logger = AppLogger.getLogger("StatisticsAnalysisImpl")
    }

    override suspend fun calculateAndStoreDailyStatistics(date: String): DailyEtfStatistics? =
        withContext(Dispatchers.IO) {
            try {
                logger.d("Calculating daily ETF statistics for $date...")

                // 최근 2개 날짜 가져오기
                val dates = etfDao.getLatestTwoDates()
                if (dates.size < 2) {
                    logger.w("Not enough data to calculate statistics")
                    return@withContext null
                }

                val currentDate = dates[0]
                val previousDate = dates[1]

                logger.d("Calculating statistics for $currentDate (vs $previousDate)")

                val allEtfTickers = etfDao.getAllEtfsSuspend().map { it.ticker }

                // 신규 편입 통계
                val newStocks = etfDao.getAllNewStocks(currentDate, previousDate, allEtfTickers)
                val newStockCount = newStocks.size
                val newStockAmount = newStocks.sumOf { it.currentAmount.toLong() }

                // 제외 종목 통계
                val removedStocks = etfDao.getAllRemovedStocks(currentDate, previousDate, allEtfTickers)
                val removedStockCount = removedStocks.size
                val removedStockAmount = removedStocks.sumOf { (it.previousWeight * 1000000).toLong() }

                // 비중 증가 통계
                val increasedStocks = etfDao.getAllIncreasedStocks(currentDate, previousDate, allEtfTickers)
                val increasedStockCount = increasedStocks.size
                val increasedStockAmount = increasedStocks.sumOf { it.currentAmount.toLong() }

                // 비중 감소 통계
                val decreasedStocks = etfDao.getAllDecreasedStocks(currentDate, previousDate, allEtfTickers)
                val decreasedStockCount = decreasedStocks.size
                val decreasedStockAmount = decreasedStocks.sumOf { it.currentAmount.toLong() }

                // 원화예금 통계
                val cashTrend = etfDao.getCashDepositTrend(allEtfTickers)
                val currentCash = cashTrend.findLast { it.date == currentDate }
                val previousCash = cashTrend.findLast { it.date == previousDate }

                val cashDepositAmount = currentCash?.totalAmount?.toLong() ?: 0L
                val previousCashAmount = previousCash?.totalAmount?.toLong() ?: 0L
                val cashDepositChange = cashDepositAmount - previousCashAmount
                val cashDepositChangeRate = if (previousCashAmount > 0) {
                    (cashDepositChange.toDouble() / previousCashAmount) * 100
                } else {
                    0.0
                }

                // ETF 수 및 총 보유 금액
                val etfCount = etfDao.getEtfCount()
                val currentHoldings = etfDao.getStockAmountRanking(currentDate, previousDate, allEtfTickers)
                val totalHoldingAmount = currentHoldings.sumOf { it.totalAmount.toLong() }

                // 통계 객체 생성
                val statistics = DailyEtfStatistics(
                    date = currentDate,
                    newStockCount = newStockCount,
                    newStockAmount = newStockAmount,
                    removedStockCount = removedStockCount,
                    removedStockAmount = removedStockAmount,
                    increasedStockCount = increasedStockCount,
                    increasedStockAmount = increasedStockAmount,
                    decreasedStockCount = decreasedStockCount,
                    decreasedStockAmount = decreasedStockAmount,
                    cashDepositAmount = cashDepositAmount,
                    cashDepositChange = cashDepositChange,
                    cashDepositChangeRate = cashDepositChangeRate,
                    totalEtfCount = etfCount,
                    totalHoldingAmount = totalHoldingAmount
                )

                // 저장
                dailyEtfStatisticsDao.insert(statistics)

                logger.d("Daily statistics saved: new=$newStockCount, removed=$removedStockCount, " +
                        "increased=$increasedStockCount, decreased=$decreasedStockCount")

                statistics
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Error calculating daily statistics", e)
                null
            }
        }

    override suspend fun getStatisticsByDate(date: String): DailyEtfStatistics? =
        withContext(Dispatchers.IO) {
            dailyEtfStatisticsDao.getByDate(date)
        }

    override suspend fun getLatestDate(): String? = withContext(Dispatchers.IO) {
        dailyEtfStatisticsDao.getLatestDate()
    }

    override suspend fun getAllDates(): List<String> = withContext(Dispatchers.IO) {
        dailyEtfStatisticsDao.getAllDates()
    }

    override suspend fun calculateCorrelation(
        market: String,
        startDate: String,
        endDate: String
    ): CorrelationData? = withContext(Dispatchers.IO) {
        try {
            logger.d("Calculating correlation for $market from $startDate to $endDate")

            // 데이터 가져오기
            val statistics = dailyEtfStatisticsDao.getByDateRangeSuspend(startDate, endDate)
            val indices = marketIndexDao.getByMarketAndDateRangeSuspend(market, startDate, endDate)

            if (statistics.isEmpty() || indices.isEmpty()) {
                logger.w("Not enough data for correlation: stats=${statistics.size}, indices=${indices.size}")
                return@withContext null
            }

            // 날짜별로 매핑
            val indexMap = indices.associateBy { it.date }
            val pairs = statistics.mapNotNull { stat ->
                indexMap[stat.date]?.let { index ->
                    stat to index
                }
            }

            if (pairs.size < 10) {
                logger.w("Not enough paired data for correlation: ${pairs.size}")
                return@withContext null
            }

            logger.d("Paired data count: ${pairs.size}")

            // 상관관계 계산
            val correlations = mutableMapOf<String, Double>()

            correlations["newStock"] = calculatePearsonCorrelation(
                pairs.map { it.first.newStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            correlations["removedStock"] = calculatePearsonCorrelation(
                pairs.map { it.first.removedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            correlations["increasedStock"] = calculatePearsonCorrelation(
                pairs.map { it.first.increasedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            correlations["decreasedStock"] = calculatePearsonCorrelation(
                pairs.map { it.first.decreasedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            correlations["cashDeposit"] = calculatePearsonCorrelation(
                pairs.map { it.first.cashDepositChangeRate },
                pairs.map { it.second.changeRate }
            )

            logger.d("Correlations calculated: $correlations")

            CorrelationData(
                market = market,
                period = "$startDate ~ $endDate",
                dataPoints = pairs.size,
                correlations = correlations
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Error calculating correlation", e)
            null
        }
    }

    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) return 0.0

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var sumSqX = 0.0
        var sumSqY = 0.0

        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            sumSqX += dx.pow(2)
            sumSqY += dy.pow(2)
        }

        val denominator = sqrt(sumSqX * sumSqY)
        return if (denominator > 0) numerator / denominator else 0.0
    }
}
