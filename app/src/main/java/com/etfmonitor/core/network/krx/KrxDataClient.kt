package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.SnapshotType
import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Kotlin KRX ETF/Stock 데이터 클라이언트
 *
 * PyKrxClient (Python/Chaquopy)를 대체하는 네이티브 구현
 * kotlin_krx 라이브러리를 사용하여 KRX API에 직접 접근
 *
 * ## 주요 기능
 * - ETF 목록 조회 (필터링 지원): [getFilteredEtfList], [getEtfList]
 * - ETF 보유 종목 조회: [getHoldings]
 * - 영업일 목록 조회: [getBusinessDays]
 * - 종목명 조회: [getStockName]
 *
 * ## 타임아웃 및 재시도
 * - 기본 타임아웃: 30초
 * - 보유 종목 조회 시 최대 2회 재시도
 *
 * @property krxEtf kotlin_krx ETF API
 * @property krxStock kotlin_krx Stock API
 * @property krxIndex kotlin_krx Index API
 */
@Singleton
class KrxDataClient @Inject constructor(
    private val krxEtf: KrxEtf,
    private val krxStock: KrxStock,
    private val krxIndex: KrxIndex
) {

    companion object {
        private val logger = AppLogger.getLogger("KrxDataClient")
        private const val TIMEOUT_MS = 30_000L
        private const val MAX_RETRIES = 2
    }

    /**
     * ETF 목록을 필터링과 함께 조회
     *
     * @param date 조회 날짜 (yyyyMMdd)
     * @param includeKeywords 포함할 키워드 목록
     * @param excludeKeywords 제외할 키워드 목록
     * @return 필터링된 ETF 리스트
     */
    suspend fun getFilteredEtfList(
        date: String,
        includeKeywords: List<String>,
        excludeKeywords: List<String>
    ): List<Etf> = withContext(Dispatchers.IO) {
        try {
            if (includeKeywords.isEmpty()) {
                logger.e("ERROR: includeKeywords is empty in KrxDataClient")
                return@withContext emptyList()
            }

            logger.d("getFilteredEtfList: date=$date, include=$includeKeywords, exclude=$excludeKeywords")

            val etfInfoList = withTimeout(TIMEOUT_MS) {
                krxEtf.getEtfTickerList(date)
            }

            logger.d("KRX returned ${etfInfoList.size} total ETFs")

            val filtered = etfInfoList.filter { info ->
                val name = info.name
                val matchesInclude = includeKeywords.any { keyword ->
                    name.contains(keyword, ignoreCase = true)
                }
                val matchesExclude = excludeKeywords.any { keyword ->
                    name.contains(keyword, ignoreCase = true)
                }
                matchesInclude && !matchesExclude
            }

            val etfs = filtered.map { info ->
                Etf(info.ticker, info.name)
            }

            logger.d("Filtered to ${etfs.size} ETFs")
            if (etfs.isNotEmpty()) {
                logger.d("Sample: ${etfs.take(3).joinToString { "${it.ticker}: ${it.name}" }}")
            }

            etfs
        } catch (e: Exception) {
            logger.e("getFilteredEtfList failed", e)
            emptyList()
        }
    }

    /**
     * ETF 목록 조회 (필터링 없음)
     *
     * @param date 조회 날짜 (yyyyMMdd)
     * @return 전체 ETF 리스트
     */
    suspend fun getEtfList(date: String): List<Etf> = withContext(Dispatchers.IO) {
        try {
            logger.d("getEtfList: $date")

            val etfInfoList = withTimeout(TIMEOUT_MS) {
                krxEtf.getEtfTickerList(date)
            }

            val etfs = etfInfoList.map { info ->
                Etf(info.ticker, info.name)
            }

            logger.d("getEtfList result: ${etfs.size} ETFs")
            etfs
        } catch (e: Exception) {
            logger.e("getEtfList error", e)
            emptyList()
        }
    }

    /**
     * ETF 보유 종목 조회
     *
     * @param etfTicker ETF 종목코드
     * @param date 조회 날짜 (yyyyMMdd)
     * @return 보유 종목 리스트
     */
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding> =
        withContext(Dispatchers.IO) {
            retryWithTimeout(maxRetries = MAX_RETRIES) {
                try {
                    val portfolioList = krxEtf.getPortfolio(date, etfTicker)

                    if (portfolioList.isEmpty()) {
                        return@retryWithTimeout emptyList()
                    }

                    val holdings = portfolioList.mapNotNull { portfolio ->
                        val stockTicker = portfolio.ticker
                        if (stockTicker.isBlank() || stockTicker.length != 6) {
                            return@mapNotNull null
                        }

                        val stockName = portfolio.name.ifEmpty {
                            getStockName(stockTicker)
                        }
                        val weight = portfolio.weight?.toFloat() ?: 0f
                        val amount = portfolio.valuationAmount.toFloat()

                        Holding.create(
                            etfTicker = etfTicker,
                            stockTicker = stockTicker,
                            stockName = stockName,
                            date = DateFormatter.formatFromYYYYMMDD(date),
                            weight = weight / 100f, // kotlin_krx returns percentage, convert to ratio
                            amount = amount,
                            snapshotType = SnapshotType.DAILY
                        )
                    }

                    holdings
                } catch (e: Exception) {
                    logger.e("getHoldings error for $etfTicker", e)
                    throw e
                }
            } ?: emptyList()
        }

    /**
     * 영업일 조회
     *
     * KRX에서 데이터가 있는 날짜를 기반으로 영업일을 추출
     *
     * @param days 필요한 영업일 수
     * @return 영업일 리스트 (yyyy-MM-dd 형식)
     */
    suspend fun getBusinessDays(days: Int): List<String> = withContext(Dispatchers.IO) {
        try {
            logger.d("getBusinessDays: $days days")
            val end = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val start = LocalDate.now()
                .minusDays((days * 2).toLong())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            logger.d("Date range: $start to $end")

            // Use market OHLCV data to detect business days
            val ohlcvList = withTimeout(TIMEOUT_MS) {
                krxStock.getMarketOhlcv(end)
            }

            // If today has no data, try going back up to 7 days
            if (ohlcvList.isEmpty()) {
                // Fallback: use index data to find latest business day
                val indexData = withTimeout(TIMEOUT_MS) {
                    krxIndex.getKospi(start, end)
                }
                val businessDays = indexData
                    .map { DateFormatter.formatFromYYYYMMDD(it.date) }
                    .takeLast(days)
                logger.d("getBusinessDays result (via index): ${businessDays.size} days")
                return@withContext businessDays
            }

            // Get ticker list for multiple dates to identify business days
            val tickerList = withTimeout(TIMEOUT_MS) {
                krxStock.getTickerList(end)
            }

            // Use index data for a reliable date range
            val indexData = withTimeout(TIMEOUT_MS) {
                krxIndex.getKospi(start, end)
            }

            val businessDays = indexData
                .map { DateFormatter.formatFromYYYYMMDD(it.date) }
                .takeLast(days)

            logger.d("getBusinessDays result: ${businessDays.size} days")
            businessDays
        } catch (e: Exception) {
            logger.e("getBusinessDays error", e)
            emptyList()
        }
    }

    /**
     * 종목명 조회
     *
     * @param ticker 종목코드
     * @return 종목명 (조회 실패 시 ticker 반환)
     */
    suspend fun getStockName(ticker: String): String = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val tickerList = withTimeout(TIMEOUT_MS) {
                krxStock.getTickerList(today)
            }
            val tickerInfo = tickerList.find { it.ticker == ticker }
            tickerInfo?.name ?: ticker
        } catch (e: Exception) {
            logger.e("Error getting stock name for $ticker: ${e.message}")
            ticker
        }
    }

    /**
     * KIS API 관련 메서드 (하위 호환)
     * kotlin_krx는 API 키가 불필요하므로 항상 true/no-op
     */
    suspend fun initializeKisClient(appKey: String, appSecret: String): Boolean = true
    suspend fun isKisClientInitialized(): Boolean = true
    suspend fun testKisApiConnection(): Boolean = true
    suspend fun resetKisClient() {}

    private suspend fun <T> retryWithTimeout(
        maxRetries: Int = MAX_RETRIES,
        timeoutMs: Long = TIMEOUT_MS,
        block: suspend () -> T
    ): T? {
        repeat(maxRetries) { attempt ->
            try {
                return withTimeout(timeoutMs) {
                    block()
                }
            } catch (e: Exception) {
                logger.w("Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt == maxRetries - 1) {
                    logger.e("All retry attempts failed")
                    return null
                }
            }
        }
        return null
    }
}
