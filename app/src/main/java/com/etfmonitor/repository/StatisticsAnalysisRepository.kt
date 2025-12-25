package com.etfmonitor.repository

import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.MarketIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 통계 분석 Repository
 * ETF 통계 계산, 상관관계 분석, 매수/매도 신호 생성
 */
@Singleton
class StatisticsAnalysisRepository @Inject constructor(
    private val etfDao: EtfDao,
    private val marketIndexDao: MarketIndexDao,
    private val dailyEtfStatisticsDao: DailyEtfStatisticsDao
) {
    companion object {
        private val logger = AppLogger.getLogger("StatisticsAnalysis")
    }

    /**
     * 일별 ETF 통계 계산 및 저장
     * 전체 ETF의 신규/제외/증가/감소 종목 통계를 계산합니다.
     */
    suspend fun calculateAndStoreDailyStatistics(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d( "Calculating daily ETF statistics...")

            // 최근 2개 날짜 가져오기
            val dates = etfDao.getLatestTwoDates()
            if (dates.size < 2) {
                logger.w( "Not enough data to calculate statistics")
                return@withContext Result.failure(Exception("최소 2일의 데이터가 필요합니다"))
            }

            val currentDate = dates[0]
            val previousDate = dates[1]

            logger.d( "Calculating statistics for $currentDate (vs $previousDate)")

            // 신규 편입 통계
            val newStocks = etfDao.getAllNewStocks(currentDate, previousDate)
            val newStockCount = newStocks.size
            val newStockAmount = newStocks.sumOf { it.currentAmount.toLong() as Long }

            // 제외 종목 통계
            val removedStocks = etfDao.getAllRemovedStocks(currentDate, previousDate)
            val removedStockCount = removedStocks.size
            val removedStockAmount = removedStocks.sumOf { (it.previousWeight * 1000000).toLong() as Long }

            // 비중 증가 통계
            val increasedStocks = etfDao.getAllIncreasedStocks(currentDate, previousDate)
            val increasedStockCount = increasedStocks.size
            val increasedStockAmount = increasedStocks.sumOf { it.currentAmount.toLong() as Long }

            // 비중 감소 통계
            val decreasedStocks = etfDao.getAllDecreasedStocks(currentDate, previousDate)
            val decreasedStockCount = decreasedStocks.size
            val decreasedStockAmount = decreasedStocks.sumOf { it.currentAmount.toLong() as Long }

            // 원화예금 통계
            val cashTrend = etfDao.getCashDepositTrend()
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
            val currentHoldings = etfDao.getStockAmountRanking(currentDate, previousDate)
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

            logger.d( "Daily statistics saved: new=$newStockCount, removed=$removedStockCount, " +
                    "increased=$increasedStockCount, decreased=$decreasedStockCount")

            Result.success(1)
        } catch (e: Exception) {
            logger.e( "Error calculating daily statistics", e)
            Result.failure(e)
        }
    }

    /**
     * 특정 기간의 ETF 통계와 시장 지수 상관관계 계산
     * Pearson 상관계수를 사용합니다.
     */
    suspend fun calculateCorrelation(
        market: String,
        startDate: String,
        endDate: String
    ): CorrelationResult = withContext(Dispatchers.IO) {
        try {
            logger.d( "Calculating correlation for $market from $startDate to $endDate")

            // 데이터 가져오기
            val statistics = dailyEtfStatisticsDao.getByDateRangeSuspend(startDate, endDate)
            val indices = marketIndexDao.getByMarketAndDateRangeSuspend(market, startDate, endDate)

            if (statistics.isEmpty() || indices.isEmpty()) {
                logger.w( "Not enough data for correlation: stats=${statistics.size}, indices=${indices.size}")
                return@withContext CorrelationResult.empty()
            }

            // 날짜별로 매핑
            val indexMap = indices.associateBy { it.date }
            val pairs = statistics.mapNotNull { stat ->
                indexMap[stat.date]?.let { index ->
                    stat to index
                }
            }

            if (pairs.size < 10) {
                logger.w( "Not enough paired data for correlation: ${pairs.size}")
                return@withContext CorrelationResult.empty()
            }

            logger.d( "Paired data count: ${pairs.size}")

            // 지수 변화율 계산
            val indexChanges = mutableListOf<Double>()
            for (i in 1 until indices.size) {
                val prev = indices[i - 1].closePrice
                val curr = indices[i].closePrice
                if (prev > 0) {
                    indexChanges.add((curr - prev) / prev * 100)
                }
            }

            // 상관관계 계산
            val newStockCorr = calculatePearsonCorrelation(
                pairs.map { it.first.newStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            val removedStockCorr = calculatePearsonCorrelation(
                pairs.map { it.first.removedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            val increasedStockCorr = calculatePearsonCorrelation(
                pairs.map { it.first.increasedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            val decreasedStockCorr = calculatePearsonCorrelation(
                pairs.map { it.first.decreasedStockCount.toDouble() },
                pairs.map { it.second.changeRate }
            )

            val cashDepositCorr = calculatePearsonCorrelation(
                pairs.map { it.first.cashDepositChangeRate },
                pairs.map { it.second.changeRate }
            )

            logger.d( "Correlations calculated: new=$newStockCorr, removed=$removedStockCorr, " +
                    "increased=$increasedStockCorr, decreased=$decreasedStockCorr, cash=$cashDepositCorr")

            CorrelationResult(
                market = market,
                period = "$startDate ~ $endDate",
                dataPointCount = pairs.size,
                newStockCorrelation = newStockCorr,
                removedStockCorrelation = removedStockCorr,
                increasedStockCorrelation = increasedStockCorr,
                decreasedStockCorrelation = decreasedStockCorr,
                cashDepositCorrelation = cashDepositCorr,
                averageIndexChange = indexChanges.average()
            )
        } catch (e: Exception) {
            logger.e( "Error calculating correlation", e)
            CorrelationResult.empty()
        }
    }

    /**
     * Pearson 상관계수 계산
     */
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

    /**
     * 모든 일별 통계 조회
     */
    fun getAllStatistics(): Flow<List<DailyEtfStatistics>> =
        dailyEtfStatisticsDao.getAll().flowOn(Dispatchers.IO)

    /**
     * 최근 N일의 통계 조회
     */
    fun getRecentStatistics(limit: Int): Flow<List<DailyEtfStatistics>> =
        dailyEtfStatisticsDao.getRecent(limit).flowOn(Dispatchers.IO)

    /**
     * 기간별 통계 조회
     */
    fun getStatisticsByDateRange(startDate: String, endDate: String): Flow<List<DailyEtfStatistics>> =
        dailyEtfStatisticsDao.getByDateRange(startDate, endDate).flowOn(Dispatchers.IO)
}

/**
 * 상관관계 분석 결과
 */
data class CorrelationResult(
    val market: String,
    val period: String,
    val dataPointCount: Int,
    val newStockCorrelation: Double,
    val removedStockCorrelation: Double,
    val increasedStockCorrelation: Double,
    val decreasedStockCorrelation: Double,
    val cashDepositCorrelation: Double,
    val averageIndexChange: Double
) {
    companion object {
        fun empty() = CorrelationResult(
            market = "",
            period = "",
            dataPointCount = 0,
            newStockCorrelation = 0.0,
            removedStockCorrelation = 0.0,
            increasedStockCorrelation = 0.0,
            decreasedStockCorrelation = 0.0,
            cashDepositCorrelation = 0.0,
            averageIndexChange = 0.0
        )
    }

    /**
     * 상관관계 강도 해석
     */
    fun getCorrelationStrength(correlation: Double): String {
        return when {
            correlation >= 0.7 -> "강한 양의 상관관계"
            correlation >= 0.4 -> "중간 양의 상관관계"
            correlation >= 0.1 -> "약한 양의 상관관계"
            correlation > -0.1 -> "상관관계 없음"
            correlation > -0.4 -> "약한 음의 상관관계"
            correlation > -0.7 -> "중간 음의 상관관계"
            else -> "강한 음의 상관관계"
        }
    }
}
