package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.analysis.model.StockData
import com.etfmonitor.core.analysis.model.StockOhlcvData
import com.krxkt.KrxStock
import com.krxkt.model.AskBidType
import com.krxkt.model.TradingValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Kotlin 종목 데이터 클라이언트
 *
 * OscillatorPyClient의 종목 분석/OHLCV/리스트 기능을 대체하는 네이티브 구현
 * kotlin_krx 라이브러리를 사용하여 KRX API에 직접 접근
 *
 * ## 주요 기능
 * - 종목 검색: [searchStock]
 * - 종목 분석 데이터 (시총, 외국인/기관 수급): [getStockAnalysis]
 * - 종목 OHLCV 데이터: [getStockOhlcv]
 * - 전체 종목 리스트: [getAllStocksList]
 *
 * @property krxStock kotlin_krx Stock API
 */
@Singleton
class StockDataClient @Inject constructor(
    private val krxStock: KrxStock
) {

    companion object {
        private val logger = AppLogger.getLogger("StockDataClient")
        private const val TIMEOUT_MS = 30_000L
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 종목 검색
     *
     * @param query 종목코드 또는 종목명
     * @return (ticker, name) 쌍 또는 null
     */
    suspend fun searchStock(query: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("searchStock: $query")
                val today = LocalDate.now().format(dateFormat)

                val tickerList = krxStock.getTickerList(today)

                // First try exact ticker match
                val exactMatch = tickerList.find { it.ticker == query }
                if (exactMatch != null) {
                    logger.d("Found stock (exact): ${exactMatch.ticker} - ${exactMatch.name}")
                    return@withTimeout Pair(exactMatch.ticker, exactMatch.name)
                }

                // Then try name search
                val nameMatch = tickerList.find { it.name.contains(query, ignoreCase = true) }
                if (nameMatch != null) {
                    logger.d("Found stock (name): ${nameMatch.ticker} - ${nameMatch.name}")
                    return@withTimeout Pair(nameMatch.ticker, nameMatch.name)
                }

                logger.d("Stock not found: $query")
                null
            }
        } catch (e: Exception) {
            logger.e("searchStock error", e)
            null
        }
    }

    /**
     * 종목 분석 데이터 수집
     *
     * 시가총액, 외국인/기관 5일 누적 수급 데이터를 수집합니다.
     *
     * @param ticker 종목코드
     * @param days 분석 기간 (일)
     * @return StockData 또는 null
     */
    suspend fun getStockAnalysis(ticker: String, days: Int = 180): StockData? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(TIMEOUT_MS) {
                    logger.d("getStockAnalysis: $ticker, $days days")

                    val endDate = LocalDate.now().format(dateFormat)
                    val startDate = LocalDate.now()
                        .minusDays((days * 1.5).toLong())
                        .format(dateFormat)

                    // OHLCV 데이터 조회 (필수 - 실패 시 null 반환)
                    val ohlcvList = krxStock.getOhlcvByTicker(startDate, endDate, ticker)

                    if (ohlcvList.isEmpty()) {
                        logger.e("No OHLCV data for $ticker")
                        return@withTimeout null
                    }

                    // 투자자별 거래실적 (보조 - 실패 시 빈 리스트로 대체)
                    val foreignTrading = try {
                        krxStock.getTradingByInvestor(
                            startDate, endDate, ticker,
                            valueType = TradingValueType.VALUE,
                            askBidType = AskBidType.NET_BUY
                        )
                    } catch (e: Exception) {
                        logger.w("Failed to get trading data for $ticker: ${e.message}")
                        emptyList()
                    }

                    // 종목명 조회 (보조 - 실패 시 ticker로 대체)
                    val stockName = try {
                        val tickerList = krxStock.getTickerList(endDate)
                        tickerList.find { it.ticker == ticker }?.name ?: ticker
                    } catch (e: Exception) {
                        logger.w("Failed to get stock name for $ticker: ${e.message}")
                        ticker
                    }

                    // 날짜별 데이터 매핑
                    val dates = ohlcvList.map { DateFormatter.formatFromYYYYMMDD(it.date) }
                    val closePrices = ohlcvList.map { it.close * 1L }

                    // 외국인/기관 5일 누적 계산
                    val tradingDates = foreignTrading.map { DateFormatter.formatFromYYYYMMDD(it.date) }
                    val foreignDaily = foreignTrading.map { it.foreigner }
                    val institutionDaily = foreignTrading.map { it.institutionalTotal }

                    // 5일 누적 rolling sum
                    val foreign5d = rollingSum(foreignDaily, 5)
                    val institution5d = rollingSum(institutionDaily, 5)

                    // 날짜 기준으로 데이터 정렬 및 매핑
                    // OHLCV 데이터와 투자자 데이터의 날짜가 다를 수 있으므로 공통 날짜만 사용
                    val commonDates = mutableListOf<String>()
                    val finalMarketCaps = mutableListOf<Long>()
                    val finalForeign5d = mutableListOf<Long>()
                    val finalInstitution5d = mutableListOf<Long>()

                    val tradingMap = tradingDates.zip(foreign5d.zip(institution5d)).toMap()

                    for (i in dates.indices) {
                        val date = dates[i]
                        val tradingData = tradingMap[date]
                        commonDates.add(date)
                        finalMarketCaps.add(closePrices[i])
                        finalForeign5d.add(tradingData?.first ?: 0L)
                        finalInstitution5d.add(tradingData?.second ?: 0L)
                    }

                    // Take last `days` entries
                    val take = minOf(days, commonDates.size)
                    val result = StockData(
                        ticker = ticker,
                        name = stockName,
                        dates = commonDates.takeLast(take),
                        marketCap = finalMarketCaps.takeLast(take),
                        foreign5d = finalForeign5d.takeLast(take),
                        institution5d = finalInstitution5d.takeLast(take)
                    )

                    logger.d("Stock analysis complete: ${result.name}, ${result.dates.size} data points")
                    result
                }
            } catch (e: Exception) {
                logger.e("getStockAnalysis error for $ticker", e)
                null
            }
        }

    /**
     * 종목 OHLCV 데이터 조회
     *
     * @param ticker 종목 코드
     * @param days 분석 기간 (일)
     * @param interval 주기 ("d"=일별, "w"=주별)
     * @return StockOhlcvData 또는 null
     */
    suspend fun getStockOhlcv(
        ticker: String,
        days: Int = 180,
        interval: String = "d"
    ): StockOhlcvData? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("getStockOhlcv: $ticker, $days days, interval: $interval")

                val endDate = LocalDate.now().format(dateFormat)
                val startDate = LocalDate.now()
                    .minusDays((days * 1.5).toLong())
                    .format(dateFormat)

                val ohlcvList = krxStock.getOhlcvByTicker(startDate, endDate, ticker)

                if (ohlcvList.isEmpty()) {
                    logger.e("No OHLCV data for $ticker")
                    return@withTimeout null
                }

                // 종목명 조회 (보조 - 실패 시 ticker로 대체)
                val stockName = try {
                    val tickerList = krxStock.getTickerList(endDate)
                    tickerList.find { it.ticker == ticker }?.name ?: ticker
                } catch (e: Exception) {
                    logger.w("Failed to get stock name for $ticker: ${e.message}")
                    ticker
                }

                // 주별 리샘플링 (interval == "w")
                val processedData = if (interval == "w") {
                    resampleWeekly(ohlcvList)
                } else {
                    ohlcvList.map { ohlcv ->
                        OhlcvRecord(
                            date = DateFormatter.formatFromYYYYMMDD(ohlcv.date),
                            open = ohlcv.open.toDouble(),
                            high = ohlcv.high.toDouble(),
                            low = ohlcv.low.toDouble(),
                            close = ohlcv.close.toDouble(),
                            volume = ohlcv.volume
                        )
                    }
                }

                val take = minOf(days, processedData.size)
                val finalData = processedData.takeLast(take)

                StockOhlcvData(
                    ticker = ticker,
                    name = stockName,
                    dates = finalData.map { it.date },
                    open = finalData.map { it.open },
                    high = finalData.map { it.high },
                    low = finalData.map { it.low },
                    close = finalData.map { it.close },
                    volume = finalData.map { it.volume }
                ).also {
                    logger.d("OHLCV data complete: ${it.name}, ${it.dates.size} data points")
                }
            }
        } catch (e: Exception) {
            logger.e("getStockOhlcv error for $ticker", e)
            null
        }
    }

    /**
     * 전체 종목 리스트 가져오기 (자동완성용)
     *
     * @return (ticker, name) 쌍의 리스트
     */
    suspend fun getAllStocksList(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                logger.d("getAllStocksList")
                val today = LocalDate.now().format(dateFormat)
                val tickerList = krxStock.getTickerList(today)
                tickerList.map { Pair(it.ticker, it.name) }
            }
        } catch (e: Exception) {
            logger.e("getAllStocksList error", e)
            emptyList()
        }
    }

    /**
     * 5일 rolling sum 계산
     */
    private fun rollingSum(data: List<Long>, window: Int): List<Long> {
        if (data.isEmpty()) return emptyList()
        return data.mapIndexed { index, _ ->
            val start = maxOf(0, index - window + 1)
            data.subList(start, index + 1).sum()
        }
    }

    /**
     * 일별 데이터를 주별로 리샘플링
     */
    private fun resampleWeekly(
        ohlcvList: List<com.krxkt.model.StockOhlcvHistory>
    ): List<OhlcvRecord> {
        if (ohlcvList.isEmpty()) return emptyList()

        val weeklyRecords = mutableListOf<OhlcvRecord>()
        var weekOpen: Double? = null
        var weekHigh = Double.MIN_VALUE
        var weekLow = Double.MAX_VALUE
        var weekClose = 0.0
        var weekVolume = 0L
        var weekDate = ""

        for (ohlcv in ohlcvList) {
            val date = LocalDate.parse(ohlcv.date, dateFormat)
            val dayOfWeek = date.dayOfWeek

            if (weekOpen == null) {
                weekOpen = ohlcv.open.toDouble()
            }
            weekHigh = maxOf(weekHigh, ohlcv.high.toDouble())
            weekLow = minOf(weekLow, ohlcv.low.toDouble())
            weekClose = ohlcv.close.toDouble()
            weekVolume += ohlcv.volume
            weekDate = DateFormatter.formatFromYYYYMMDD(ohlcv.date)

            // End of week (Friday) or last item
            if (dayOfWeek == DayOfWeek.FRIDAY || ohlcv === ohlcvList.last()) {
                weeklyRecords.add(
                    OhlcvRecord(
                        date = weekDate,
                        open = weekOpen,
                        high = weekHigh,
                        low = weekLow,
                        close = weekClose,
                        volume = weekVolume
                    )
                )
                weekOpen = null
                weekHigh = Double.MIN_VALUE
                weekLow = Double.MAX_VALUE
                weekClose = 0.0
                weekVolume = 0L
            }
        }

        return weeklyRecords
    }

    private data class OhlcvRecord(
        val date: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Long
    )
}
