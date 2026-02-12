package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.entities.MarketIndex
import com.krxkt.KrxIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Kotlin 시장 지수 데이터 클라이언트
 *
 * MarketIndexPyClient (Python/Chaquopy)를 대체하는 네이티브 구현
 * kotlin_krx 라이브러리를 사용하여 KOSPI/KOSDAQ 지수 데이터를 직접 조회
 *
 * @property krxIndex kotlin_krx Index API
 */
@Singleton
class MarketIndexClient @Inject constructor(
    private val krxIndex: KrxIndex
) {
    companion object {
        private val logger = AppLogger.getLogger("MarketIndexClient")
        private const val TIMEOUT_MS = 30_000L
    }

    /**
     * 지정된 기간 동안의 시장 지수 데이터 수집
     *
     * @param startDate 시작 날짜 (yyyyMMdd 형식)
     * @param endDate 종료 날짜 (yyyyMMdd 형식)
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchMarketIndices(
        startDate: String,
        endDate: String,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching market indices: markets=$markets, from=$startDate to=$endDate")

            val allIndices = mutableListOf<MarketIndex>()

            withTimeout(TIMEOUT_MS) {
                for (market in markets) {
                    val indexData = when (market.uppercase()) {
                        "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                        "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                        else -> {
                            logger.w("Unknown market: $market, skipping")
                            continue
                        }
                    }

                    val marketIndices = indexData.map { ohlcv ->
                        val dateStr = DateFormatter.formatFromYYYYMMDD(ohlcv.date)
                        MarketIndex(
                            id = "$market-$dateStr",
                            date = dateStr,
                            market = market,
                            closePrice = ohlcv.close,
                            openPrice = ohlcv.open,
                            highPrice = ohlcv.high,
                            lowPrice = ohlcv.low,
                            volume = ohlcv.volume,
                            changeRate = ohlcv.change ?: 0.0,
                            lastUpdated = System.currentTimeMillis()
                        )
                    }

                    allIndices.addAll(marketIndices)
                }
            }

            logger.d("Successfully fetched ${allIndices.size} market index records")
            allIndices
        } catch (e: Exception) {
            logger.e("Error fetching market indices", e)
            emptyList()
        }
    }

    /**
     * 최근 N일의 시장 지수 데이터 수집
     *
     * @param days 수집할 일수 (기본 30일)
     * @param markets 수집할 시장 목록
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchRecentDays(
        days: Int = 30,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        try {
            logger.d("Fetching recent $days days for markets: $markets")

            val endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            // Request more days to account for weekends/holidays
            val startDate = LocalDate.now()
                .minusDays((days * 1.5).toLong())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            fetchMarketIndices(startDate, endDate, markets)
        } catch (e: Exception) {
            logger.e("Error fetching recent market indices", e)
            emptyList()
        }
    }

    /**
     * 특정 시장의 최신 지수 데이터 조회
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @return 최신 MarketIndex 또는 null
     */
    suspend fun getLatestIndex(market: String): MarketIndex? = withContext(Dispatchers.IO) {
        try {
            logger.d("Getting latest index for market: $market")

            val endDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val startDate = LocalDate.now()
                .minusDays(10)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            val indices = withTimeout(TIMEOUT_MS) {
                when (market.uppercase()) {
                    "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                    "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                    else -> {
                        logger.w("Unknown market: $market")
                        return@withTimeout null
                    }
                }
            }

            if (indices.isNullOrEmpty()) {
                logger.w("No latest index data found for $market")
                return@withContext null
            }

            val latest = indices.last()
            val dateStr = DateFormatter.formatFromYYYYMMDD(latest.date)

            MarketIndex(
                id = "$market-$dateStr",
                date = dateStr,
                market = market,
                closePrice = latest.close,
                openPrice = latest.open,
                highPrice = latest.high,
                lowPrice = latest.low,
                volume = latest.volume,
                changeRate = latest.change ?: 0.0,
                lastUpdated = System.currentTimeMillis()
            ).also {
                logger.d("Successfully fetched latest index for $market: date=${it.date}, close=${it.closePrice}")
            }
        } catch (e: Exception) {
            logger.e("Error getting latest index for $market", e)
            null
        }
    }
}
